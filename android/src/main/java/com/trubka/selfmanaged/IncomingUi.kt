package com.trubka.selfmanaged

import android.app.*
import android.content.*
import android.os.Build
import android.os.Bundle
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.Target
import java.util.concurrent.Executors

object IncomingUi {
  private const val NOTIF_ID = 4455

  fun show(context: Context, uuid: String, number: String, name: String?, avatarUrl: String?, extra: Bundle?) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channelId = context.packageName + ".incoming.calls"

    if (Build.VERSION.SDK_INT >= 26) {
      val ch = NotificationChannel(channelId, "Incoming Calls", NotificationManager.IMPORTANCE_HIGH)
      ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
      nm.createNotificationChannel(ch)
    }

    val intent = Intent(context, IncomingCallActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
      putExtra("uuid", uuid)
      putExtra("number", number)
      putExtra("displayName", name ?: number)
      putExtra("avatarUrl", avatarUrl)
      putExtra("incoming_call", true)
      if (extra != null) putExtra("extraData", extra)
    }
    val pi = PendingIntent.getActivity(
      context, 0, intent,
      PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
    )

    // Базовое уведомление (без аватара)
    val base = NotificationCompat.Builder(context, channelId)
      .setSmallIcon(android.R.drawable.sym_call_incoming)
      .setOngoing(true)
      .setCategory(Notification.CATEGORY_CALL)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setContentTitle(name ?: number)
      .setContentText("Входящий вызов")
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setFullScreenIntent(pi, true)
      .build()

    nm.notify(NOTIF_ID, base)

    // Подгрузим аватар асинхронно и обновим уведомление
    if (!avatarUrl.isNullOrBlank()) {
      Executors.newSingleThreadExecutor().execute {
        try {
          val bmp: Bitmap = Glide.with(context)
            .asBitmap()
            .load(avatarUrl)
            .circleCrop()                   // красиво кругом
            .submit(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
            .get()

          val withIcon = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentTitle(name ?: number)
            .setContentText("Входящий вызов")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(pi, true)
            .setLargeIcon(bmp)              // <-- аватар
            .build()

          nm.notify(NOTIF_ID, withIcon)     // обновили тем же ID
        } catch (_: Exception) { /* игнор, оставим без аватара */ }
      }
    }
  }

  fun dismiss(context: Context) {
    (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIF_ID)
  }
}
