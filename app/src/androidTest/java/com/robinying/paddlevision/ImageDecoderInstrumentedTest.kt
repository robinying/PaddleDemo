package com.robinying.paddlevision

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageDecoderInstrumentedTest {
    @Test
    fun decoderBoundsFileUriOutputDimensions() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val imageFile = File(context.cacheDir, "decoder-bound.jpg")
        createJpeg(imageFile, width = 400, height = 200)

        val decoded = ImageDecoder(context, maxDimension = 100, maxPixels = 10_000).decode(Uri.fromFile(imageFile))
        try {
            assertEquals(100, decoded.bitmap.width)
            assertEquals(50, decoded.bitmap.height)
            assertEquals(Bitmap.Config.ARGB_8888, decoded.bitmap.config)
        } finally {
            decoded.bitmap.recycle()
            imageFile.delete()
        }
    }

    @Test
    fun decoderReportsUnreadableUriAsClassifiedError() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val missingFile = File(context.cacheDir, "missing-image.jpg")

        try {
            ImageDecoder(context).decode(Uri.fromFile(missingFile))
            fail("Expected an image decode failure")
        } catch (exception: VisionInferenceException) {
            assertEquals(VisionErrorCode.IMAGE_DECODE_FAILED, exception.code)
        }
    }

    private fun createJpeg(file: File, width: Int, height: Int) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.BLUE)
            file.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
    }
}
