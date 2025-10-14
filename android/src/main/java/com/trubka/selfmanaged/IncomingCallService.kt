package com.trubka.selfmanaged

import android.app.*
import android.content.*
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat

class IncomingCallService : Service() {
    override fun onCreate() {
        super.onCreate()
        IncomingUi.ensureChannel(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val uuid   = intent?.getStringExtra("uuid") ?: ""
        val number = intent?.getStringExtra("number") ?: ""
        val name   = intent?.getStringExtra("displayName")

        // тот же билдер, что и был в IncomingUi.show, но через startForeground
        val notif = IncomingUi.buildNotification(this, uuid, number, name)
        startForeground(IncomingUi.NOTIF_ID, notif)

        // Важное: сразу пинганём full-screen интент (часть NotificationCompat)
        // Уже сделано в билдере через setFullScreenIntent(...)
        return START_NOT_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        fun start(ctx: Context, uuid: String, number: String, name: String?) {
            val i = Intent(ctx, IncomingCallService::class.java)
                .putExtra("uuid", uuid)
                .putExtra("number", number)
                .putExtra("displayName", name)
            if (Build.VERSION.SDK_INT >= 26) {
                ctx.startForegroundService(i)
            } else {
                ctx.startService(i)
            }
        }
        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, IncomingCallService::class.java))
        }
    }
}
