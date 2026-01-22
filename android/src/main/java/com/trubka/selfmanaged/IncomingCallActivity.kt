package com.trubka.selfmanaged

import android.os.Bundle
import android.content.Intent
import android.os.Build
import android.util.Log
import java.lang.ref.WeakReference
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.ReactInstanceEventListener
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactContext
import com.facebook.react.modules.core.DeviceEventManagerModule

class IncomingCallActivity : ReactActivity() {

  companion object {
    @Volatile
    private var currentRef: WeakReference<IncomingCallActivity>? = null

    private fun setCurrent(activity: IncomingCallActivity) {
      currentRef = WeakReference(activity)
    }

    private fun clearCurrent(activity: IncomingCallActivity) {
      if (currentRef?.get() === activity) {
        currentRef = null
      }
    }

    fun finishAndRemoveIfRunning() {
      val activity = currentRef?.get() ?: return
      activity.runOnUiThread {
        Log.d("CallUI", "call finish: taskId=${activity.taskId}, isTaskRoot=${activity.isTaskRoot}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          activity.finishAndRemoveTask()
        } else {
          activity.finish()
        }
      }
    }
  }

  override fun getMainComponentName() = "IncomingRoot"

  // КЛЮЧ: прокинуть extras как initialProps в RN
  override fun createReactActivityDelegate(): ReactActivityDelegate {
    return object : ReactActivityDelegate(this, mainComponentName) {
      override fun getLaunchOptions(): Bundle? = intent?.extras
    }
  }


  override fun onCreate(savedInstanceState: Bundle?) {
    if (Build.VERSION.SDK_INT >= 27) {
      setShowWhenLocked(true)
      setTurnScreenOn(true)
    }
    window.addFlags(
      android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
              android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
              android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
    )
    super.onCreate(savedInstanceState)
    setCurrent(this)
    emitIncomingIntentExtras(intent)
  }

  override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    setIntent(intent)
    emitIncomingIntentExtras(intent)
  }

  override fun onDestroy() {
    clearCurrent(this)
    super.onDestroy()
  }

  private fun emitIncomingIntentExtras(intent: Intent?) {
    val rim = reactNativeHost.reactInstanceManager
    val extras = intent?.extras

    fun send(ctx: ReactContext, b: Bundle?) {
      val map = Arguments.createMap().apply {
        putString("uuid", b?.getString("uuid"))
        putString("number", b?.getString("number"))
        putString("displayName", b?.getString("displayName"))
        putString("avatarUri", b?.getString("avatarUri"))
        putString("notif_action", b?.getString("notif_action"))
        val extra: Bundle? = b?.getBundle("extraData")
        if (extra != null) putMap("extraData", Arguments.fromBundle(extra))
        putBoolean("incoming_call", b?.getBoolean("incoming_call", true) ?: true)
      }

      ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
        .emit("IncomingIntent", map)
    }

    val ctx = rim.currentReactContext
    if (ctx != null) {
      send(ctx, extras)
      return
    }

    val listener = object : ReactInstanceEventListener {
      override fun onReactContextInitialized(context: ReactContext) {
        rim.removeReactInstanceEventListener(this)
        send(context, extras)
      }
    }
    rim.addReactInstanceEventListener(listener)
  }
}
