package com.trubka.selfmanaged

import android.app.*
import android.content.*
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.annotation.RequiresApi

class IncomingCallService : Service() {
    override fun onCreate() {
        super.onCreate()
        IncomingUi.ensureChannel(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val extras = intent?.extras
        if (action == IncomingUi.ACTION_ANSWER_CALL) {
            IncomingUiModule.sendEventToJS("answerCall", extras)
            val activityIntent = Intent(this, IncomingCallActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtras(extras ?: Bundle())
            startActivity(activityIntent)
            IncomingUi.dismiss(this)
            stopSelf()
            return START_NOT_STICKY
        }
        if (action == IncomingUi.ACTION_END_CALL) {
            IncomingUiModule.sendEventToJS("endCall", extras)
            IncomingUi.dismiss(this)
            stopSelf()
            return START_NOT_STICKY
        }

        val uuid   = intent?.getStringExtra("uuid") ?: ""
        val number = intent?.getStringExtra("number") ?: ""
        val name   = intent?.getStringExtra("displayName")
        val avatarUrl = intent?.getStringExtra("avatarUrl")
        val extraData = intent?.getBundleExtra("extraData")

        // тот же билдер, что и был в IncomingUi.show, но через startForeground
        val notif = IncomingUi.buildNotification(this, uuid, number, name, avatarUrl, extraData)
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
        fun start(ctx: Context, uuid: String, number: String, name: String?, avatarUrl: String?, extraData: Bundle?) {
            val i = Intent(ctx, IncomingCallService::class.java)
                .putExtra("uuid", uuid)
                .putExtra("number", number)
                .putExtra("displayName", name)
                .putExtra("avatarUrl", avatarUrl)
                .putExtra("extraData", extraData)
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
