package com.robinying.paddlevision

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import java.io.InputStream

/**
 * Smoke-test fixtures live in the test APK (`src/androidTest/assets`) so they never ship inside
 * the release APK. They are read through the instrumentation context, not the app under test.
 */
internal object TestAssets {
    private val testContext: Context get() = InstrumentationRegistry.getInstrumentation().context

    fun open(fileName: String): InputStream = testContext.assets.open("samples/$fileName")

    fun bytes(fileName: String): ByteArray = open(fileName).use(InputStream::readBytes)
}

/** Pascal VOC category id of `dog`, the class asserted by the bundled object-detection sample. */
internal const val PASCAL_VOC_DOG_CATEGORY_ID = 12

/** Pascal VOC category id of `person`. */
internal const val PASCAL_VOC_PERSON_CATEGORY_ID = 15
