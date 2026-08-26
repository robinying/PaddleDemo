package com.robinying.paddlevision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder as AndroidImageDecoder
import android.net.Uri
import java.io.FileNotFoundException
import kotlin.math.max

/** Decodes Photo Picker content URIs once with bounded software-backed output. */
class ImageDecoder(
    private val context: Context,
    private val maxDimension: Int = MAX_DIMENSION,
    private val maxPixels: Int = MAX_PIXELS,
) {
    fun decode(uri: Uri): DecodedImage {
        return try {
            val source = AndroidImageDecoder.createSource(context.contentResolver, uri)
            val bitmap = AndroidImageDecoder.decodeBitmap(source) { decoder, imageInfo, _ ->
                val targetSize = calculateTargetSize(imageInfo.size.width, imageInfo.size.height)
                decoder.allocator = AndroidImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSize(targetSize.width, targetSize.height)
            }.toArgb8888()
            DecodedImage(bitmap = bitmap, sourceSize = ImageSize(bitmap.width, bitmap.height))
        } catch (exception: VisionInferenceException) {
            throw exception
        } catch (exception: FileNotFoundException) {
            throw VisionInferenceException(
                VisionErrorCode.IMAGE_DECODE_FAILED,
                "所选图片已不可访问，请重新选择图片",
            )
        } catch (exception: Exception) {
            throw VisionInferenceException(
                VisionErrorCode.IMAGE_DECODE_FAILED,
                "无法解码所选图片，请选择常见图片格式后重试",
            )
        }
    }

    private fun calculateTargetSize(width: Int, height: Int): ImageSize {
        if (width <= 0 || height <= 0) {
            throw VisionInferenceException(VisionErrorCode.IMAGE_DECODE_FAILED, "所选文件不是有效图片")
        }
        val dimensionScale = minOf(1f, maxDimension.toFloat() / max(width, height))
        val pixelScale = minOf(1f, kotlin.math.sqrt(maxPixels.toDouble() / (width.toLong() * height)).toFloat())
        val scale = minOf(dimensionScale, pixelScale)
        return ImageSize(
            width = max(1, (width * scale).toInt()),
            height = max(1, (height * scale).toInt()),
        )
    }

    private fun Bitmap.toArgb8888(): Bitmap {
        return if (config == Bitmap.Config.ARGB_8888) {
            this
        } else {
            copy(Bitmap.Config.ARGB_8888, false)
                ?: throw VisionInferenceException(VisionErrorCode.IMAGE_DECODE_FAILED, "无法转换所选图片")
        }.also { converted -> if (converted !== this) recycle() }
    }

    private companion object {
        const val MAX_DIMENSION = 2048
        const val MAX_PIXELS = 4_000_000
    }
}

data class DecodedImage(
    val bitmap: Bitmap,
    val sourceSize: ImageSize,
) {
    val imageSize: ImageSize get() = ImageSize(bitmap.width, bitmap.height)
}
