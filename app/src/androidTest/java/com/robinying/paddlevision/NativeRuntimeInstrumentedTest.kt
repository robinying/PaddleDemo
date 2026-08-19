package com.robinying.paddlevision

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeRuntimeInstrumentedTest {
    @Test
    fun nativeBridgeLoadsOnArm64Device() {
        assertTrue(NativeBridge.isRuntimeAvailable())
        assertTrue(NativeBridge.runtimeInfo().contains("v2.10-rc"))
    }
}
