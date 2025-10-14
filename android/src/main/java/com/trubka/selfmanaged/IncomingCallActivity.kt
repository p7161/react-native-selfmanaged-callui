package com.trubka.selfmanaged

import android.content.Context
import android.os.Bundle
import android.content.Intent
import android.os.Build
import android.view.WindowManager
import androidx.annotation.RequiresApi
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.bridge.Arguments
import com.facebook.react.modules.core.DeviceEventManagerModule

class IncomingCallActivity : ReactActivity() {

  override fun getMainComponentName() = "IncomingRoot"

  // КЛЮЧ: прокинуть extras как initialProps в RN
  override fun createReactActivityDelegate(): ReactActivityDelegate {
    return object : ReactActivityDelegate(this, mainComponentName) {
      override fun getLaunchOptions(): Bundle? = intent?.extras
    }
  }


  override fun onCreate(savedInstanceState: Bundle?) {
    setShowWhenLocked(true)
    setTurnScreenOn(true)
    window.addFlags(
      android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
              android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
              android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
    )
    super.onCreate(savedInstanceState)
    emitIncomingIntentExtras(intent)
  }

  override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    setIntent(intent)
    emitIncomingIntentExtras(intent)
  }

  private fun emitIncomingIntentExtras(intent: Intent?) {
    val ctx = reactNativeHost.reactInstanceManager.currentReactContext ?: return

    val map = Arguments.createMap().apply {
      putString("uuid", intent?.getStringExtra("uuid"))
      putString("number", intent?.getStringExtra("number"))
      putString("displayName", intent?.getStringExtra("displayName"))
      putString("avatarUrl", intent?.getStringExtra("avatarUrl"))
      val extra: Bundle? = intent?.getBundleExtra("extraData")
      if (extra != null) putMap("extraData", Arguments.fromBundle(extra))
      putBoolean("incoming_call", true)
    }

    ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
      .emit("IncomingIntent", map)
  }
}