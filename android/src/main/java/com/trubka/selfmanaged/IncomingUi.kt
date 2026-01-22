package com.trubka.selfmanaged

import android.app.*
import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import java.net.HttpURLConnection
import java.net.URL

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

  internal fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= 26) {
      val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val existing = nm.getNotificationChannel(CHANNEL_ID)
      var needCreate = true
      if (existing != null) {
        if (existing.importance < NotificationManager.IMPORTANCE_HIGH) {
          nm.deleteNotificationChannel(CHANNEL_ID)
        } else {
          needCreate = false
        }
      }
      if (needCreate) {
        val attrs = AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .setLegacyStreamType(AudioManager.STREAM_RING)
          .build()
        val ch = NotificationChannel(
          CHANNEL_ID, "Incoming Calls", NotificationManager.IMPORTANCE_HIGH
        ).apply {
          description = "Incoming call notifications"
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
      .setContentTitle(if (video) "Входящий видеозвонок" else "Входящий звонок")
      .setContentText(name ?: number)
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

  internal fun loadAvatarBitmap(avatarUri: String?): Bitmap? {
    if (avatarUri.isNullOrBlank()) return null
    return try {
      val url = URL(avatarUri)
      val conn = url.openConnection() as HttpURLConnection
      conn.connectTimeout = 2000
      conn.readTimeout = 2000
      conn.doInput = true
      conn.instanceFollowRedirects = true
      conn.connect()
      conn.inputStream.use { stream ->
        BitmapFactory.decodeStream(stream)
      }
    } catch (_: Exception) {
      null
    }
  }
}
