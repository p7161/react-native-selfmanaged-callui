package com.trubka.selfmanaged

import android.app.*
import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import kotlin.math.abs

object IncomingUi {
  internal const val CHANNEL_ID = "trubka.incoming.v3"  // новый id!
  internal const val NOTIF_ID = 4455
  internal const val ACTION_ANSWER_CALL = "com.trubka.ACTION_ANSWER_CALL"
  internal const val ACTION_END_CALL = "com.trubka.ACTION_END_CALL"

  fun show(context: Context, uuid: String, number: String, name: String?, avatarUri: String?, video: Boolean, extraData: Bundle?) {
    // Запускаем fg-service: это резко повышает шанс фуллскрина на локскрине
    IncomingCallService.start(context, uuid, number, name, avatarUri, video, extraData)
  }

  fun dismiss(context: Context) {
    (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
      .cancel(NOTIF_ID)
    IncomingCallService.stop(context)
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

  // Telegram-style fallback: a flat coloured circle with the caller's initials,
  // drawn synchronously. Used when no cached avatar is available so the
  // notification always has a person image without ever touching the network.
  internal fun buildInitialsAvatar(displayName: String?): Bitmap {
    val size = 256
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    val name = displayName?.trim().orEmpty()
    val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = colorForName(name)
      style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, circlePaint)

    val initials = initialsOf(name)
    if (initials.isNotEmpty()) {
      val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = size * 0.4f
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

  // Stable colour per caller (same name → same colour), from a small palette.
  private val AVATAR_COLORS = intArrayOf(
    0xFF2CA5E0.toInt(), 0xFF7E57C2.toInt(), 0xFFEF6C00.toInt(),
    0xFF26A69A.toInt(), 0xFFEC407A.toInt(), 0xFF5C6BC0.toInt(),
  )

  private fun colorForName(name: String): Int {
    if (name.isEmpty()) return AVATAR_COLORS[0]
    return AVATAR_COLORS[abs(name.hashCode()) % AVATAR_COLORS.size]
  }
}
