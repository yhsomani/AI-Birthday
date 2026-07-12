package com.yashsomani.birthdayautopilot.bridge

import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider
import com.yashsomani.birthdayautopilot.bridge.codegen.NativeBirthdaySpec

class BirthdayNativePackage : BaseReactPackage() {
  override fun getModule(
    name: String,
    reactContext: ReactApplicationContext,
  ): NativeModule? = when (name) {
    NativeBirthdaySpec.NAME -> BirthdayNativeModule(reactContext)
    else -> null
  }

  override fun getReactModuleInfoProvider(): ReactModuleInfoProvider = ReactModuleInfoProvider {
    mapOf(
      NativeBirthdaySpec.NAME to ReactModuleInfo(
        NativeBirthdaySpec.NAME,
        BirthdayNativeModule::class.java.name,
        false,
        false,
        false,
        true,
      ),
    )
  }
}
