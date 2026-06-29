package com.trubka.selfmanaged

import android.app.*
import android.content.*
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import java.util.concurrent.atomic.AtomicBoolean
import androidx.annotation.RequiresApi

class IncomingCallService : Service() {
    private val isRinging = AtomicBoolean(false)
    private var ringtonePlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var hasAudioFocus = false

    override fun onCreate() {
        super.onCreate()
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
        val avatarUri = intent?.getStringExtra("avatarUri")
        val extraData = intent?.getBundleExtra("extraData")
        val video = intent?.getBooleanExtra("video", false) ?: false

        // Build the notification ONCE, synchronously, with the avatar already in
        // it. avatarUri is a local file:// path (resolved from the app's image
        // cache on the JS side) so decoding is a fast disk read — no network in
        // the notification's lifecycle, so there is no async re-notify that could
        // race teardown and strand a zombie notification. If the avatar isn't
        // cached locally we fall back to a synchronously-drawn initials avatar
        // (à la Telegram's AvatarDrawable). Either way: post once, never update.
        val avatarBitmap = IncomingUi.decodeLocalAvatar(avatarUri)
            ?: IncomingUi.buildInitialsAvatar(name ?: number)
        val notif = IncomingUi.buildNotification(this, uuid, number, name, avatarUri, video, avatarBitmap, extraData)
        startForeground(IncomingUi.NOTIF_ID, notif)
        startRingtoneAndVibration()

        // Важное: сразу пинганём full-screen интент (часть NotificationCompat)
        // Уже сделано в билдере через setFullScreenIntent(...)
        return START_NOT_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onDestroy() {
        stopRingtoneAndVibration()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        fun start(ctx: Context, uuid: String, number: String, name: String?, avatarUri: String?, video: Boolean, extraData: Bundle?) {
            val i = Intent(ctx, IncomingCallService::class.java)
                .putExtra("uuid", uuid)
                .putExtra("number", number)
                .putExtra("displayName", name)
                .putExtra("avatarUri", avatarUri)
                .putExtra("video", video)
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

    private fun startRingtoneAndVibration() {
        if (!isRinging.compareAndSet(false, true)) return
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        if (am.ringerMode == AudioManager.RINGER_MODE_SILENT) {
            isRinging.set(false)
            return
        }

        try {
            ringtonePlayer = MediaPlayer().apply {
                setOnPreparedListener { it.start() }
                isLooping = true
                setAudioStreamType(AudioManager.STREAM_RING)
                val defaultUri = Settings.System.DEFAULT_RINGTONE_URI
                val ringtoneUri = defaultUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                setDataSource(this@IncomingCallService, ringtoneUri)
                prepareAsync()
            }
        } catch (_: Exception) {
            ringtonePlayer?.release()
            ringtonePlayer = null
        }

        if (am.ringerMode != AudioManager.RINGER_MODE_SILENT) {
            vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            val pattern = longArrayOf(0, 700, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        }

        hasAudioFocus = am.requestAudioFocus(
            null,
            AudioManager.STREAM_RING,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun stopRingtoneAndVibration() {
        if (!isRinging.compareAndSet(true, false)) return
        try {
            ringtonePlayer?.stop()
        } catch (_: Exception) {
        }
        ringtonePlayer?.release()
        ringtonePlayer = null

        vibrator?.cancel()
        vibrator = null

        if (hasAudioFocus) {
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            am.abandonAudioFocus(null)
            hasAudioFocus = false
        }
    }
}
