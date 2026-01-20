package com.trubka.selfmanaged

import android.app.*
import android.content.*
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.Person

object IncomingUi {
  internal const val CHANNEL_ID = "trubka.incoming.v3"  // новый id!
  internal const val NOTIF_ID = 4455
  internal const val ACTION_ANSWER_CALL = "com.trubka.ACTION_ANSWER_CALL"
  internal const val ACTION_END_CALL = "com.trubka.ACTION_END_CALL"

  fun show(context: Context, uuid: String, number: String, name: String?, avatarUrl: String?, extraData: Bundle?) {
    // Запускаем fg-service: это резко повышает шанс фуллскрина на локскрине
    IncomingCallService.start(context, uuid, number, name, avatarUrl, extraData)
  }

  fun dismiss(context: Context) {
    (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
      .cancel(NOTIF_ID)
    IncomingCallService.stop(context)
  }

  internal fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= 26) {
      val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val ch = NotificationChannel(
        CHANNEL_ID, "Incoming Calls", NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = "Incoming call notifications"
        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        enableVibration(true)
        setShowBadge(false)

        val uri = android.media.RingtoneManager.getDefaultUri(
          android.media.RingtoneManager.TYPE_RINGTONE
        )
        val attrs = android.media.AudioAttributes.Builder()
          .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
          .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .build()
        setSound(uri, attrs)   // ← РЕШАЮЩЕЕ
      }
      nm.createNotificationChannel(ch)
    }
  }

  internal fun buildNotification(
    ctx: Context,
    uuid: String,
    number: String,
    name: String?,
    avatarUrl: String?,
    extraData: Bundle?
  ): Notification {
    val base = Bundle().apply {
      putString("uuid", uuid)
      putString("number", number)
      putString("displayName", name ?: number)
      putString("avatarUrl", avatarUrl)
      putBundle("extraData", extraData)
      putBoolean("incoming_call", true)
    }

    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
      (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)

    // Full-screen Activity
    val fsIntent = Intent(ctx, IncomingCallActivity::class.java).apply {
      action = "com.trubka.ACTION_INCOMING_CALL"
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
      putExtras(base)
    }
    val fsPi = PendingIntent.getActivity(
      ctx, 100, fsIntent, flags
    )

    // Actions
    val ansPi = PendingIntent.getService(
      ctx,
      101,
      Intent(ctx, IncomingCallService::class.java)
        .setAction(ACTION_ANSWER_CALL)
        .putExtras(base),
      flags
    )

    val decPi = PendingIntent.getService(
      ctx,
      102,
      Intent(ctx, IncomingCallService::class.java)
        .setAction(ACTION_END_CALL)
        .putExtras(base),
      flags
    )

    val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.sym_call_incoming)
      .setOngoing(true)
      .setCategory(Notification.CATEGORY_CALL)
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setContentTitle(name ?: number)
      .setContentText("Входящий вызов")
      .setDefaults(NotificationCompat.DEFAULT_ALL)
      .setFullScreenIntent(fsPi, true)  // ключ для локскрина
      .setContentIntent(fsPi)           // по тапу — те же extras

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val caller = Person.Builder()
        .setName(name ?: number)
        .setImportant(true)
        .build()
      builder.setStyle(NotificationCompat.CallStyle.forIncomingCall(caller, decPi, ansPi))
    } else {
      builder
        .addAction(0, "Отклонить", decPi)
        .addAction(0, "Ответить", ansPi)
    }

    return builder.build()
  }
}
