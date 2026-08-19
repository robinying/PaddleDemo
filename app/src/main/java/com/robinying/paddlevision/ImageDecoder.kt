package com.robinying.paddlevision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.IOException
import kotlin.math.max

/** Decodes Photo Picker content without resolving a URI to a filesystem path. */
class ImageDecoder(
    private val context: Context,
    private val maxDimension: Int = MAX_DIMENSION,
    private val maxPixels: Int = MAX_PIXELS,
) {
    fun decode(uri: Uri): DecodedImage {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: throw VisionInferenceException(
            VisionErrorCode.IMAGE_DECODE_FAILED,
            "无法读取所选图片",
        )
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw VisionInferenceException(VisionErrorCode.IMAGE_DECODE_FAILED, "所选文件不是有效图片")
        }

        val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(
                it,
                null,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            )
        }?.copy(Bitmap.Config.ARGB_8888, false) ?: throw VisionInferenceException(
            VisionErrorCode.IMAGE_DECODE_FAILED,
            "无法解码所选图片",
        )

        return DecodedImage(
            bitmap = rotateForExif(uri, bitmap),
            sourceSize = ImageSize(bounds.outWidth, bounds.outHeight),
        )
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        while (
            width / sampleSize > maxDimension ||
                height / sampleSize > maxDimension ||
                width.toLong() * height.toLong() / sampleSize / sampleSize > maxPixels
        ) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun rotateForExif(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) {
            return bitmap
        }
        val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            .also { rotated -> if (rotated !== bitmap) bitmap.recycle() }
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
