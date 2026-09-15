package com.robinying.paddlevision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OcrLanguageTest {
    @Test
    fun onlyLanguagesWithAPackagedModelAreOffered() {
        assertEquals(listOf(OcrLanguage.CHINESE), OcrLanguage.packaged)
        assertTrue(OcrLanguage.packaged.all(OcrLanguage::isPackaged))
        assertTrue("The English OCR model is not bundled yet", !OcrLanguage.ENGLISH.isPackaged)
    }

    @Test
    fun everyPackagedLanguagePassesTheEngineGuard() {
        OcrLanguage.packaged.forEach(::requireSupportedOcrLanguage)
    }

    @Test
    fun unpackagedLanguagesAreRejectedWithAnActionableMessage() {
        try {
            requireSupportedOcrLanguage(OcrLanguage.ENGLISH)
            fail("Expected an unpackaged OCR language to be rejected")
        } catch (exception: VisionInferenceException) {
            assertEquals(VisionErrorCode.UNSUPPORTED_TASK, exception.code)
            assertEquals(UiText(R.string.error_ocr_language_unsupported), exception.text)
        }
    }
}

class ObjectCategoryLabelTest {
    private val labels = listOf("Background", "Dog", "Person")

    @Test
    fun knownCategoryIdsMapToTheirLabel() {
        assertEquals("Background", objectCategoryLabel(0, labels) { "unknown($it)" })
        assertEquals("Person", objectCategoryLabel(2, labels) { "unknown($it)" })
    }

    @Test
    fun outOfRangeCategoryIdsFallBackInsteadOfLeakingAnIndex() {
        assertEquals("unknown(7)", objectCategoryLabel(7, labels) { "unknown($it)" })
        assertEquals("unknown(-1)", objectCategoryLabel(-1, labels) { "unknown($it)" })
    }

    @Test
    fun anEmptyLabelListAlwaysFallsBack() {
        assertEquals("unknown(12)", objectCategoryLabel(12, emptyList()) { "unknown($it)" })
    }
}
