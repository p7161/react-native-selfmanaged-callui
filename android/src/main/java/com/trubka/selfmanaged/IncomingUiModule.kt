package com.trubka.selfmanaged

import android.app.NotificationManager
import android.app.NotificationChannel
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.facebook.react.bridge.*
import android.content.*
import android.net.Uri
import android.os.Bundle
import com.trubka.selfmanaged.IncomingUi.CHANNEL_ID
import com.facebook.react.modules.core.DeviceEventManagerModule

class IncomingUiModule(private val rc: ReactApplicationContext) : ReactContextBaseJavaModule(rc) {
  override fun getName() = "IncomingUi"

  private var hasListeners = false

  init {
    instance = this
  }

  // avatarUri, video и extraData теперь опциональны
  @ReactMethod
  fun show(uuid: String, number: String, name: String, avatarUri: String?, video: Boolean, extraData: ReadableMap?) {
    val bundle = if (extraData != null) Arguments.toBundle(extraData) else null
    IncomingUi.show(rc, uuid, number, name, avatarUri, video, bundle)
  }

  @ReactMethod
  fun startCallActivity(
    uuid: String,
    number: String?,
    name: String?,
    avatarUri: String?,
    video: Boolean?,
    extraData: ReadableMap?
  ) {
    val bundle = if (extraData != null) Arguments.toBundle(extraData) else null
    val base = Bundle().apply {
      putString("uuid", uuid)
      putString("number", number)
      putString("displayName", name ?: number ?: "")
      putString("avatarUri", avatarUri)
      putBoolean("video", video ?: false)
      putBundle("extraData", bundle)
      putBoolean("incoming_call", true)
    }
    val fsIntent = Intent(rc, IncomingCallActivity::class.java).apply {
      action = "com.trubka.ACTION_INCOMING_CALL"
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
      putExtras(base)
    }
    rc.startActivity(fsIntent)
  }

  @ReactMethod fun dismiss() { IncomingUi.dismiss(rc) }

  @ReactMethod
  fun finishActivity() {
    IncomingCallActivity.finishAndRemoveIfRunning()
  }

  private val prefs get() = rc.getSharedPreferences("rn-selfmanaged-callui", Context.MODE_PRIVATE)

  @ReactMethod
  fun ensureIncomingChannel(title: String?, description: String?, promise: Promise) {
    try {
      IncomingUi.ensureChannel(reactApplicationContext, title, description)
      promise.resolve(null)
    } catch (e: Exception) {
      promise.reject("ensure_channel_error", e)
    }
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

  @ReactMethod
  fun addListener(eventName: String) {
    hasListeners = true
  }

  @ReactMethod
  fun removeListeners(count: Int) {
  }

  override fun invalidate() {
    if (instance === this) {
      instance = null
    }
    super.invalidate()
  }

  @ReactMethod
  fun getInitialEvents(promise: Promise) {
    try {
      promise.resolve(delayedEvents)
    } catch (e: Exception) {
      promise.reject("initial_events_error", e)
    }
  }

  @ReactMethod
  fun clearInitialEvents() {
    delayedEvents = WritableNativeArray()
  }

  private fun emitEvent(eventName: String, params: Any?) {
    rc.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
      .emit(eventName, params)
  }

  private fun _sendEventToJS(eventName: String, extras: Bundle?) {
    Log.d("IncomingUiModule", "sendEventToJS called: $eventName")
    val params: WritableMap? = extras?.let { Arguments.fromBundle(it) }
    if (rc.hasActiveCatalystInstance() && hasListeners) {
      Log.d("IncomingUiModule", "sendEventToJS called hasListeners: $eventName")
      emitEvent(eventName, params)
    } else {
      Log.d("IncomingUiModule", "sendEventToJS called queueEvent: $eventName")
      queueEvent(eventName, params)
    }
  }

  companion object {
    @JvmStatic var instance: IncomingUiModule? = null
    @JvmStatic var delayedEvents: WritableNativeArray = WritableNativeArray()

    @JvmStatic
    fun sendEventToJS(eventName: String, extras: Bundle?) {
      val module = instance
      if (module != null) {
        module._sendEventToJS(eventName, extras)
      } else {
        val params: WritableMap? = extras?.let { Arguments.fromBundle(it) }
        queueEvent(eventName, params)
      }
    }

    private fun queueEvent(eventName: String, params: WritableMap?) {
      val payload = Arguments.createMap()
      payload.putString("name", eventName)
      if (params != null) {
        payload.putMap("data", params)
      } else {
        payload.putNull("data")
      }
      delayedEvents.pushMap(payload)
    }
  }
}
