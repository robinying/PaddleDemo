package com.robinying.paddlevision

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageDecoderInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun decoderBoundsFileUriOutputDimensions() {
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
        val missingFile = File(context.cacheDir, "missing-image.jpg")

        try {
            ImageDecoder(context).decode(Uri.fromFile(missingFile))
            fail("Expected an image decode failure")
        } catch (exception: VisionInferenceException) {
            assertEquals(VisionErrorCode.IMAGE_DECODE_FAILED, exception.code)
            assertEquals(UiText(R.string.error_image_unavailable), exception.text)
        }
    }

    @Test
    fun decoderReportsCorruptContentAsAnUndecodableImage() {
        val corruptFile = File(context.cacheDir, "corrupt-image.jpg")
        corruptFile.writeBytes(ByteArray(2048) { index -> (index % 251).toByte() })

        try {
            ImageDecoder(context).decode(Uri.fromFile(corruptFile))
            fail("Expected a corrupt file to be rejected")
        } catch (exception: VisionInferenceException) {
            assertEquals(VisionErrorCode.IMAGE_DECODE_FAILED, exception.code)
            assertEquals(UiText(R.string.error_image_undecodable), exception.text)
        } finally {
            corruptFile.delete()
        }
    }

    @Test
    fun decoderAppliesExifOrientationSoTheBitmapIsUpright() {
        val rotatedFile = File(context.cacheDir, "decoder-orientation-6.jpg")
        createJpegWithExifOrientation(rotatedFile, width = 200, height = 100, orientation = 6)

        val decoded = ImageDecoder(context, maxDimension = 4096, maxPixels = 16_000_000).decode(Uri.fromFile(rotatedFile))
        try {
            assertEquals(
                "EXIF orientation 6 must rotate the decoded bitmap into portrait",
                100,
                decoded.bitmap.width,
            )
            assertEquals(200, decoded.bitmap.height)
        } finally {
            decoded.bitmap.recycle()
            rotatedFile.delete()
        }
    }

    @Test
    fun decoderHonoursThePixelBudgetForOversizedImages() {
        val oversizedFile = File(context.cacheDir, "decoder-oversized.jpg")
        createJpeg(oversizedFile, width = 3000, height = 3000)

        val decoded = ImageDecoder(context).decode(Uri.fromFile(oversizedFile))
        try {
            assertTrue(
                "Decoded pixel count ${decoded.bitmap.width * decoded.bitmap.height} exceeds the budget",
                decoded.bitmap.width.toLong() * decoded.bitmap.height <= ImageDecoder.MAX_PIXELS,
            )
            assertTrue(decoded.bitmap.width <= ImageDecoder.MAX_DIMENSION)
            assertTrue(decoded.bitmap.height <= ImageDecoder.MAX_DIMENSION)
        } finally {
            decoded.bitmap.recycle()
            oversizedFile.delete()
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun decoderReadsPngAndWebpSources() {
        val pngFile = File(context.cacheDir, "decoder-sample.png")
        val webpFile = File(context.cacheDir, "decoder-sample.webp")
        createJpeg(pngFile, width = 120, height = 60, format = Bitmap.CompressFormat.PNG)
        createJpeg(webpFile, width = 120, height = 60, format = Bitmap.CompressFormat.WEBP)

        listOf(pngFile, webpFile).forEach { file ->
            val decoded = ImageDecoder(context).decode(Uri.fromFile(file))
            try {
                assertEquals(120, decoded.bitmap.width)
                assertEquals(60, decoded.bitmap.height)
            } finally {
                decoded.bitmap.recycle()
                file.delete()
            }
        }
    }

    /** The workspace preview must use the same decoding policy, bounded more tightly. */
    @Test
    fun previewDecodingSharesTheInferenceSizeBudget() {
        val imageFile = File(context.cacheDir, "decoder-preview.jpg")
        createJpeg(imageFile, width = 2400, height = 1200)
        val uri = Uri.fromFile(imageFile)

        val preview = ImageDecoder.decodePreview(context, uri)
        val inference = ImageDecoder(context).decode(uri).bitmap
        try {
            assertNotNull(preview)
            assertEquals(ImageDecoder.PREVIEW_MAX_DIMENSION, maxOf(preview.width, preview.height))
            assertTrue(
                "The preview must never be larger than the inference bitmap",
                preview.width <= inference.width && preview.height <= inference.height,
            )
            assertEquals(
                "Both paths must preserve the aspect ratio",
                inference.width.toFloat() / inference.height,
                preview.width.toFloat() / preview.height,
                0.02f,
            )
        } finally {
            preview.recycle()
            inference.recycle()
            imageFile.delete()
        }
    }

    @Test
    fun previewDecodingFailsWithAClassifiedErrorForAnUnreadableUri() {
        try {
            ImageDecoder.decodePreview(context, Uri.fromFile(File(context.cacheDir, "missing-preview.jpg")))
            fail("Expected a preview decode failure")
        } catch (exception: VisionInferenceException) {
            assertEquals(VisionErrorCode.IMAGE_DECODE_FAILED, exception.code)
        }
    }

    private fun createJpeg(
        file: File,
        width: Int,
        height: Int,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
    ) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.BLUE)
            file.outputStream().use { output ->
                check(bitmap.compress(format, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
    }

    /** Writes [file] as a JPEG carrying a single EXIF IFD0 tag: `Orientation = orientation`. */
    private fun createJpegWithExifOrientation(file: File, width: Int, height: Int, orientation: Int) {
        val plain = File(file.parentFile, "${file.name}.plain")
        createJpeg(plain, width, height)
        val jpeg = plain.readBytes()
        check(plain.delete())
        check(jpeg.size > 2 && jpeg[0] == 0xFF.toByte() && jpeg[1] == 0xD8.toByte())

        file.outputStream().use { output ->
            output.write(jpeg, 0, 2)
            output.write(exifApp1Segment(orientation))
            output.write(jpeg, 2, jpeg.size - 2)
        }
    }

    private fun exifApp1Segment(orientation: Int): ByteArray {
        val tiff = ByteArray(TIFF_IFD0_LENGTH)
        // Big-endian TIFF header: "MM", 42, offset of IFD0 = 8.
        tiff[0] = 0x4D
        tiff[1] = 0x4D
        tiff[3] = 0x2A
        tiff[7] = 8
        // One IFD0 entry.
        tiff[9] = 1
        // Tag 0x0112 (Orientation), type 3 (SHORT), count 1, value.
        tiff[10] = 0x01
        tiff[11] = 0x12
        tiff[13] = 0x03
        tiff[17] = 1
        // A SHORT is left-justified inside the 4-byte value field, so the value belongs in bytes
        // 18-19 and bytes 20-21 are padding. Writing it at byte 21 used to make the tag read back
        // as Orientation 0, which is invalid, so the decoder ignored it and the test's expectation
        // of a rotated bitmap could never hold.
        tiff[18] = (orientation shr 8).toByte()
        tiff[19] = orientation.toByte()
        // The "next IFD" offset stays zero.
        val payload = "Exif".toByteArray(Charsets.US_ASCII) + byteArrayOf(0, 0) + tiff
        val segmentLength = payload.size + 2
        return byteArrayOf(
            0xFF.toByte(),
            0xE1.toByte(),
            (segmentLength shr 8).toByte(),
            segmentLength.toByte(),
        ) + payload
    }

    private companion object {
        /** 8-byte TIFF header + 2-byte entry count + one 12-byte entry + 4-byte next-IFD offset. */
        const val TIFF_IFD0_LENGTH = 8 + 2 + 12 + 4
    }
}
