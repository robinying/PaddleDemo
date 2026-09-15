package com.robinying.paddlevision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder as AndroidImageDecoder
import android.net.Uri
import java.io.FileNotFoundException
import kotlin.math.max

/**
 * Decodes Photo Picker content URIs once with bounded software-backed output.
 *
 * EXIF orientation is not handled here explicitly: the platform [AndroidImageDecoder]
 * applies the orientation tag while decoding, so the returned bitmap is already upright.
 * `ImageDecoderInstrumentedTest` pins that behaviour down with an `Orientation=6` sample.
 */
class ImageDecoder(
    private val context: Context,
    private val maxDimension: Int = MAX_DIMENSION,
    private val maxPixels: Int = MAX_PIXELS,
) {
    fun decode(uri: Uri): DecodedImage {
        return try {
            val source = AndroidImageDecoder.createSource(context.contentResolver, uri)
            val bitmap = AndroidImageDecoder.decodeBitmap(source) { decoder, imageInfo, _ ->
                val targetSize = calculateTargetSize(
                    width = imageInfo.size.width,
                    height = imageInfo.size.height,
                    maxDimension = maxDimension,
                    maxPixels = maxPixels,
                )
                decoder.allocator = AndroidImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSize(targetSize.width, targetSize.height)
            }.toArgb8888()
            DecodedImage(bitmap = bitmap, sourceSize = ImageSize(bitmap.width, bitmap.height))
        } catch (exception: VisionInferenceException) {
            throw exception
        } catch (exception: FileNotFoundException) {
            throw VisionInferenceException(
                VisionErrorCode.IMAGE_DECODE_FAILED,
                UiText(R.string.error_image_unavailable),
                exception,
            )
        } catch (exception: Exception) {
            throw VisionInferenceException(
                VisionErrorCode.IMAGE_DECODE_FAILED,
                UiText(R.string.error_image_undecodable),
                exception,
            )
        }
    }

    private fun Bitmap.toArgb8888(): Bitmap {
        return if (config == Bitmap.Config.ARGB_8888) {
            this
        } else {
            copy(Bitmap.Config.ARGB_8888, false)
                ?: throw VisionInferenceException(
                    VisionErrorCode.IMAGE_DECODE_FAILED,
                    UiText(R.string.error_image_convert_failed),
                )
        }.also { converted -> if (converted !== this) recycle() }
    }

    companion object {
        const val MAX_DIMENSION = 2048
        const val MAX_PIXELS = 4_000_000

        /** Longest side of the workspace preview bitmap. Smaller than [MAX_DIMENSION] on purpose. */
        const val PREVIEW_MAX_DIMENSION = 1200

        /**
         * Decodes a bounded bitmap for the on-screen preview through the same size policy and
         * error classification as the inference path, so the workspace and the engine can never
         * disagree about the same source image. The caller owns the returned bitmap and must
         * recycle it.
         */
        fun decodePreview(context: Context, uri: Uri): Bitmap =
            ImageDecoder(context, maxDimension = PREVIEW_MAX_DIMENSION).decode(uri).bitmap
    }
}

/**
 * Bounds a decoded image by its longest side and by total pixel count, preserving aspect ratio.
 * Shared by the preview and inference paths.
 */
internal fun calculateTargetSize(width: Int, height: Int, maxDimension: Int, maxPixels: Int): ImageSize {
    if (width <= 0 || height <= 0) {
        throw VisionInferenceException(
            VisionErrorCode.IMAGE_DECODE_FAILED,
            UiText(R.string.error_image_invalid),
        )
    }
    val dimensionScale = minOf(1f, maxDimension.toFloat() / max(width, height))
    val pixelScale = minOf(1f, kotlin.math.sqrt(maxPixels.toDouble() / (width.toLong() * height)).toFloat())
    val scale = minOf(dimensionScale, pixelScale)
    return ImageSize(
        width = max(1, (width * scale).toInt()),
        height = max(1, (height * scale).toInt()),
    )
}

data class DecodedImage(
    val bitmap: Bitmap,
    val sourceSize: ImageSize,
) {
    val imageSize: ImageSize get() = ImageSize(bitmap.width, bitmap.height)
}
