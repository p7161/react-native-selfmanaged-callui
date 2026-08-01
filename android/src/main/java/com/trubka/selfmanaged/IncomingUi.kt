package com.trubka.selfmanaged

import android.app.*
import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import java.io.File

object IncomingUi {
  internal const val CHANNEL_ID = "trubka.incoming.v3"  // новый id!
  internal const val QUIET_CHANNEL_ID = "trubka.callui.quiet"
  internal const val NOTIF_ID = 4455
  internal const val ACTION_ANSWER_CALL = "com.trubka.ACTION_ANSWER_CALL"
  internal const val ACTION_END_CALL = "com.trubka.ACTION_END_CALL"

  // Показ входящего идёт через несколько async-шагов на стороне JS (резолв аватара,
  // канал), поэтому отмена звонка вполне может прилететь раньше, чем сам показ.
  // Убирать UI командой в этом случае некого — команда просто теряется, а UI потом
  // всплывает и висит. Поэтому отмена не команда, а состояние: uuid помечается
  // терминальным, и любой поздний показ этого же звонка отклоняется.
  private const val PHASE_TTL_MS = 60_000L
  private val terminatedCalls = HashMap<String, Long>()

  // UUID приходит из разных источников (JS, extras нотификации, Telecom), а те
  // исторически меняют регистр. Ключ нормализуем, иначе промах по регистру снял бы
  // ровно ту защиту, ради которой всё это и делается.
  private fun key(uuid: String) = uuid.lowercase()

  @Synchronized
  fun markTerminated(uuid: String) {
    purgeExpired()
    terminatedCalls[key(uuid)] = android.os.SystemClock.elapsedRealtime()
    endRinging(uuid)
  }

  @Synchronized
  fun isTerminated(uuid: String?): Boolean {
    if (uuid == null) return false
    purgeExpired()
    return terminatedCalls.containsKey(key(uuid))
  }

  // Помнить дольше нечего: окно между отменой и опоздавшим показом — сотни мс.
  private fun purgeExpired() {
    val now = android.os.SystemClock.elapsedRealtime()
    terminatedCalls.entries.removeAll { now - it.value > PHASE_TTL_MS }
  }

  // Какой звонок сейчас должен звонить. Звонок перестаёт звонить не только когда
  // завершён, но и когда принят, — а принятый терминальным помечать нельзя, он
  // продолжается. Поэтому причина одна и та же, а состояние отдельное: сервис,
  // поднявшийся позже, сверяется именно с ним и покрывает сразу все случаи —
  // завершён, принят, устарел.
  private var ringingUuid: String? = null

  @Synchronized
  fun endRinging(uuid: String?) {
    if (uuid == null || ringingUuid.equals(uuid, ignoreCase = true)) {
      ringingUuid = null
    }
  }

  @Synchronized
  internal fun isRinging(uuid: String?): Boolean =
    uuid != null && ringingUuid.equals(uuid, ignoreCase = true)

  // startForegroundService() поднимает сервис отдельным сообщением системы, и до
  // onStartCommand останавливать его нельзя: остановка сервиса, который ещё не
  // опубликовал foreground-нотификацию, считается ошибкой и роняет процесс.
  // Поэтому пока старт в полёте, отмена только снимает звонок со звонка, а
  // свернётся сервис сам — см. IncomingCallService.onStartCommand.
  private const val PENDING_START_TTL_MS = 15_000L
  private var pendingStartAt = 0L

  @Synchronized
  internal fun clearStartPending() {
    pendingStartAt = 0L
  }

  // TTL — страховка на случай, когда onStartCommand не выполнится вовсе (процесс
  // убит, старт отброшен системой): иначе сервис навсегда стал бы неостановимым.
  @Synchronized
  internal fun isStartPending(): Boolean {
    if (pendingStartAt == 0L) return false
    if (android.os.SystemClock.elapsedRealtime() - pendingStartAt > PENDING_START_TTL_MS) {
      pendingStartAt = 0L
      return false
    }
    return true
  }

  // Проверка tombstone и переход в «звонит» — под одним монитором. Раздельно их
  // делать нельзя: отмена, пришедшая между проверкой и записью, оказалась бы
  // затёрта, и сервис увидел бы isRinging=true для уже завершённого звонка.
  // Именно эта атомарность и позволяет сервису обходиться одной проверкой.
  @Synchronized
  private fun tryBeginRinging(uuid: String): Boolean {
    purgeExpired()
    if (terminatedCalls.containsKey(key(uuid))) return false
    ringingUuid = uuid
    pendingStartAt = android.os.SystemClock.elapsedRealtime()
    return true
  }

  fun show(context: Context, uuid: String, number: String, name: String?, avatarUri: String?, video: Boolean, extraData: Bundle?) {
    if (!tryBeginRinging(uuid)) {
      android.util.Log.w("CallUI", "IncomingUi.show ignored, call already terminated, uuid=$uuid")
      return
    }
    // Запускаем fg-service: это резко повышает шанс фуллскрина на локскрине
    IncomingCallService.start(context, uuid, number, name, avatarUri, video, extraData)
  }

  // Снять incoming-UI с экрана. Звонок при этом мог как закончиться, так и быть
  // принят — состояние звонка снимает вызывающий (markTerminated / endRinging),
  // здесь только поверхность.
  fun dismiss(context: Context) {
    (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
      .cancel(NOTIF_ID)
    if (isStartPending()) {
      android.util.Log.d("CallUI", "dismiss: FGS start in flight, onStartCommand will stop it")
      return
    }
    IncomingCallService.stop(context)
  }

  // Нейтральная нотификация для сервиса, который поднялся ради уже завершённого
  // звонка. Без full-screen intent и на канале минимальной важности, чтобы не
  // мигнуть heads-up; нужна только чтобы честно закрыть контракт
  // startForegroundService → startForeground перед остановкой.
  internal fun buildQuietForegroundNotification(ctx: Context): Notification {
    if (Build.VERSION.SDK_INT >= 26) {
      val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      if (nm.getNotificationChannel(QUIET_CHANNEL_ID) == null) {
        nm.createNotificationChannel(
          NotificationChannel(QUIET_CHANNEL_ID, "Служебные", NotificationManager.IMPORTANCE_MIN).apply {
            lockscreenVisibility = Notification.VISIBILITY_SECRET
            setShowBadge(false)
          }
        )
      }
    }
    return NotificationCompat.Builder(ctx, QUIET_CHANNEL_ID)
      .setSmallIcon(android.R.drawable.sym_call_incoming)
      .setPriority(NotificationCompat.PRIORITY_MIN)
      .setVisibility(NotificationCompat.VISIBILITY_SECRET)
      .setSilent(true)
      .build()
  }

  internal fun ensureChannel(context: Context, title: String?, description: String?) {
    if (Build.VERSION.SDK_INT >= 26) {
      val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val existing = nm.getNotificationChannel(CHANNEL_ID)
      var importance = NotificationManager.IMPORTANCE_HIGH
      if (existing != null) {
        if (existing.importance < NotificationManager.IMPORTANCE_HIGH) {
          nm.deleteNotificationChannel(CHANNEL_ID)
        } else {
          importance = existing.importance
        }
      }
      val channelTitle = title ?: "Incoming Calls"
      val channelDescription = description ?: "Incoming call notifications"
      val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .setLegacyStreamType(AudioManager.STREAM_RING)
        .build()
      val ch = NotificationChannel(CHANNEL_ID, channelTitle, importance).apply {
        this.description = channelDescription
        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        enableVibration(false)
        enableLights(false)
        setShowBadge(false)
        setBypassDnd(true)
        setSound(null, attrs)
      }
      nm.createNotificationChannel(ch)
    }
  }

  internal fun buildNotification(
    ctx: Context,
    uuid: String,
    number: String,
    name: String?,
    avatarUri: String?,
    video: Boolean,
    avatarBitmap: Bitmap?,
    extraData: Bundle?
  ): Notification {
    val base = Bundle().apply {
      putString("uuid", uuid)
      putString("number", number)
      putString("displayName", name ?: number)
      putString("avatarUri", avatarUri)
      putBoolean("video", video)
      putBundle("extraData", extraData)
      putBoolean("incoming_call", true)
    }

    val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or
      (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)

    // Full-screen Activity
    val fsIntent = Intent(ctx, IncomingCallActivity::class.java).apply {
      action = "com.trubka.ACTION_INCOMING_CALL"
      this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
      putExtras(base)
    }
    val fsPi = PendingIntent.getActivity(
      ctx, 100, fsIntent, piFlags
    )

    // Actions
    val ansPi = PendingIntent.getService(
      ctx,
      101,
      Intent(ctx, IncomingCallService::class.java)
        .setAction(ACTION_ANSWER_CALL)
        .putExtras(base),
      piFlags
    )

    val decPi = PendingIntent.getService(
      ctx,
      102,
      Intent(ctx, IncomingCallService::class.java)
        .setAction(ACTION_END_CALL)
        .putExtras(base),
      piFlags
    )

    val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
      .setSmallIcon(if (video) android.R.drawable.presence_video_online else android.R.drawable.sym_call_incoming)
      .setOngoing(true)
      .setCategory(Notification.CATEGORY_CALL)
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setContentTitle(name ?: number)
      .setContentText(if (video) "Входящий видеозвонок" else "Входящий звонок")
      .setDefaults(0)
      .setSound(null)
      .setVibrate(longArrayOf(0))
      .setFullScreenIntent(fsPi, true)  // ключ для локскрина
      .setContentIntent(fsPi)           // по тапу — те же extras
      .setGroup("trubka.incoming.call.notif")
      .setSortKey("0")
      .setColor(0xff2ca5e0.toInt())

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val caller = Person.Builder()
        .setName(name ?: number)
        .setImportant(true)
        .apply {
          if (avatarBitmap != null) {
            setIcon(IconCompat.createWithAdaptiveBitmap(avatarBitmap))
          }
        }
        .build()
      builder.setStyle(NotificationCompat.CallStyle.forIncomingCall(caller, decPi, ansPi))
    } else {
      builder
        .addAction(0, "Отклонить", decPi)
        .addAction(0, "Ответить", ansPi)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      builder.setShowWhen(false)
    }

    if (avatarBitmap != null) {
      builder.setLargeIcon(avatarBitmap)
    }

    return builder.build()
  }

  // Decode the caller's avatar from a LOCAL file synchronously. avatarUri is a
  // file:// path (or bare path) resolved on the JS side from the app's image
  // cache — never a network URL. A non-local/missing path returns null so the
  // caller falls back to the initials avatar. No network here by design: the
  // notification is built once, so a slow load can never race teardown.
  internal fun decodeLocalAvatar(avatarUri: String?): Bitmap? {
    if (avatarUri.isNullOrBlank()) return null
    return try {
      val path = if (avatarUri.startsWith("file://")) Uri.parse(avatarUri).path else avatarUri
      if (path.isNullOrBlank()) return null
      val file = File(path)
      if (!file.exists()) return null
      BitmapFactory.decodeFile(path)
    } catch (_: Exception) {
      null
    }
  }

  // Telegram-style fallback: the app's brand avatar background with the caller's
  // initials on top, drawn synchronously. Used when no cached avatar is available
  // so the notification always has a person image without ever touching the
  // network. The background is always the bundled ios_avatar_bg drawable to match
  // the in-app <Avatar> placeholder.
  internal fun buildInitialsAvatar(context: Context, displayName: String?): Bitmap {
    val size = 256
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    val bg = try {
      BitmapFactory.decodeResource(context.resources, R.drawable.incoming_avatar_bg)
    } catch (_: Exception) {
      null
    }
    if (bg != null) {
      canvas.drawBitmap(bg, Rect(0, 0, bg.width, bg.height), Rect(0, 0, size, size),
        Paint(Paint.FILTER_BITMAP_FLAG))
    }

    val initials = initialsOf(displayName?.trim().orEmpty())
    if (initials.isNotEmpty()) {
      val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = size * 0.27f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      }
      val baseline = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
      canvas.drawText(initials, size / 2f, baseline, textPaint)
    }
    return bmp
  }

  private fun initialsOf(name: String): String {
    if (name.isBlank()) return ""
    val parts = name.split(Regex("\\s+")).filter { it.isNotBlank() }
    val letters = parts.take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }
    return letters.joinToString("")
  }
}
