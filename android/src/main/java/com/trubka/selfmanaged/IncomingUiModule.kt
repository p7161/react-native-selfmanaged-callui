package com.trubka.selfmanaged

import android.app.NotificationManager
import android.app.NotificationChannel
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.facebook.react.bridge.*
import android.content.*
import android.net.Uri
import com.trubka.selfmanaged.IncomingUi.CHANNEL_ID

class IncomingUiModule(private val rc: ReactApplicationContext) : ReactContextBaseJavaModule(rc) {
  override fun getName() = "IncomingUi"

  // avatarUrl и extraData теперь опциональны
  @ReactMethod
  fun show(uuid: String, number: String, name: String, avatarUrl: String?, extraData: ReadableMap?) {
    val bundle = if (extraData != null) Arguments.toBundle(extraData) else null
    IncomingUi.show(rc, uuid, number, name, avatarUrl, bundle)
  }

  @ReactMethod fun dismiss() { IncomingUi.dismiss(rc) }

  @ReactMethod fun finishActivity() { currentActivity?.finish() }

  private val prefs get() = rc.getSharedPreferences("rn-selfmanaged-callui", Context.MODE_PRIVATE)

  @ReactMethod
  fun ensureIncomingChannel() {
    IncomingUi.ensureChannel(reactApplicationContext)
  }

  @ReactMethod
  fun getNotificationDiagnostics(promise: Promise) {
    try {
      val nm = rc.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val res = Arguments.createMap()
      val notifEnabled = androidx.core.app.NotificationManagerCompat.from(rc).areNotificationsEnabled()
      res.putBoolean("notificationsEnabled", notifEnabled)

      if (android.os.Build.VERSION.SDK_INT >= 26) {
        val ch = nm.getNotificationChannel(CHANNEL_ID)
        res.putBoolean("channelExists", ch != null)
        if (ch != null) {
          res.putInt("importance", ch.importance)        // HIGH = 4
          res.putInt("lockscreenVisibility", ch.lockscreenVisibility) // PUBLIC = 1
          res.putBoolean("soundEnabled", ch.sound != null)
          val usageOk = ch.audioAttributes?.usage == android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE
          res.putBoolean("ringtoneUsage", usageOk)
        }
      } else {
        // до O каналов нет — считаем ок
        res.putBoolean("channelExists", true)
        res.putInt("importance", 4)
        res.putInt("lockscreenVisibility", 1)
        res.putBoolean("soundEnabled", true)
        res.putBoolean("ringtoneUsage", true)
      }
      promise.resolve(res)
    } catch (e: Exception) {
      promise.reject("diag_error", e)
    }
  }

  @ReactMethod
  fun openChannelSettings() {
    if (android.os.Build.VERSION.SDK_INT >= 26) {
      val i = android.content.Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
        putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, rc.packageName)
        putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      rc.startActivity(i)
    } else {
      val i = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, rc.packageName)
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      rc.startActivity(i)
    }
  }

  @ReactMethod
  fun isMiui(promise: Promise) {
    try {
      val b = Build.MANUFACTURER.equals("Xiaomi", true) ||
              Build.BRAND.equals("xiaomi", true) ||
              Build.BRAND.equals("redmi", true) ||
              Build.BRAND.equals("poco", true)
      promise.resolve(b)
    } catch (e: Exception) {
      promise.reject("miui_error", e)
    }
  }

  @ReactMethod
  fun wasLockscreenShown(promise: Promise) {
    try {
      val v = rc.getSharedPreferences("rn-selfmanaged-callui", Context.MODE_PRIVATE)
        .getBoolean("lockscreen_shown_ok", false)
      promise.resolve(v)
    } catch (e: Exception) {
      promise.reject("state_error", e)
    }
  }

  @ReactMethod
  fun setLockscreenShown(value: Boolean) {
    prefs.edit().putBoolean("lockscreen_shown_ok", value).apply()
  }

  // Уже были ранее (из твоих методов) — оставь, если нужно:
  @ReactMethod fun openAppNotificationSettings() {
    val i = android.content.Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
      putExtra(Settings.EXTRA_APP_PACKAGE, rc.packageName)
      addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    rc.startActivity(i)
  }


  // MIUI: общий экран “Разрешения”
  @ReactMethod fun openMiuiPermissionsScreen() {
    val ctx = rc
    val tried = arrayOf(
      Intent("miui.intent.action.APP_PERM_EDITOR").apply {
        setClassName("com.miui.securitycenter",
          "com.miui.permcenter.permissions.PermissionsEditorActivity")
        putExtra("extra_pkgname", ctx.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      },
      Intent("miui.intent.action.APP_PERM_EDITOR").apply {
        setClassName("com.miui.securitycenter",
          "com.miui.permcenter.permissions.AppPermissionsEditorActivity")
        putExtra("extra_pkgname", ctx.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    )
    for (it in tried) try { ctx.startActivity(it); return } catch (_: Exception) {}
    // Fallback — карточка приложения
    val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
      data = Uri.fromParts("package", ctx.packageName, null)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    ctx.startActivity(fallback)
  }

  // MIUI: Автозапуск (рекомендуется включить)
  @ReactMethod fun openMiuiAutostart() {
    val ctx = rc
    try {
      val i = Intent().apply {
        component = ComponentName(
          "com.miui.securitycenter",
          "com.miui.permcenter.autostart.AutoStartManagementActivity"
        )
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      ctx.startActivity(i)
    } catch (_: Exception) {
      openAppNotificationSettings()
    }
  }
}
