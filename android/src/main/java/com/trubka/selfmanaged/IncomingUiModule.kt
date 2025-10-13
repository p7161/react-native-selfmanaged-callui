package com.trubka.selfmanaged

import com.facebook.react.bridge.*

class IncomingUiModule(private val rc: ReactApplicationContext)
  : ReactContextBaseJavaModule(rc) {
  override fun getName() = "IncomingUi"

  // avatarUrl и extraData теперь опциональны
  @ReactMethod
  fun show(uuid: String, number: String, name: String, avatarUrl: String?, extraData: ReadableMap?) {
    val bundle = if (extraData != null) Arguments.toBundle(extraData) else null
    IncomingUi.show(rc, uuid, number, name, avatarUrl, bundle)
  }

  @ReactMethod fun dismiss() { IncomingUi.dismiss(rc) }

  @ReactMethod fun finishActivity() { currentActivity?.finish() }
}
