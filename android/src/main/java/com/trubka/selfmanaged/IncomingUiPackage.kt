package com.trubka.selfmanaged

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.*
import com.facebook.react.uimanager.ViewManager

class IncomingUiPackage : ReactPackage {
  override fun createNativeModules(rc: ReactApplicationContext): MutableList<NativeModule> =
    mutableListOf(IncomingUiModule(rc))
  override fun createViewManagers(rc: ReactApplicationContext): MutableList<ViewManager<*, *>> =
    mutableListOf()
}
