package com.trubka.selfmanaged

import android.os.Bundle
import android.view.WindowManager
import com.facebook.react.ReactActivity
import com.facebook.react.bridge.Arguments
import com.facebook.react.modules.core.DeviceEventManagerModule

class IncomingCallActivity : ReactActivity() {
  override fun getMainComponentName() = "IncomingRoot"

  override fun onCreate(savedInstanceState: Bundle?) {
    setShowWhenLocked(true); setTurnScreenOn(true)
    window.addFlags(
      WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
      WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
    )
    super.onCreate(null)
  }

  override fun getLaunchOptions(): Bundle? = intent?.extras // initialProps в RN

    override fun onNewIntent(intent: Intent?) {
      super.onNewIntent(intent)
      setIntent(intent)
      val ctx = reactNativeHost.reactInstanceManager.currentReactContext ?: return
      val map = Arguments.createMap().apply {
        putString("uuid", intent?.getStringExtra("uuid"))
        putString("number", intent?.getStringExtra("number"))
        putString("displayName", intent?.getStringExtra("displayName"))
        putString("avatarUrl", intent?.getStringExtra("avatarUrl"))
        val extra = intent?.getBundleExtra("extraData")
        if (extra != null) putMap("extraData", Arguments.fromBundle(extra))
        putBoolean("incoming_call", true)
      }
      ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
         .emit("IncomingIntent", map)
    }
}
