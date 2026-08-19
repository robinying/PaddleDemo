package com.robinying.paddlevision

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/** Installs model assets in private storage and rejects incomplete asset bundles. */
class ModelStore(private val context: Context) {
    fun prepare(): PreparedModels {
        val root = File(context.filesDir, MODEL_DIRECTORY)
        val expectedAssets = REQUIRED_ASSETS
        expectedAssets.forEach { assetPath ->
            val destination = File(root, assetPath)
            if (!destination.isFile || destination.length() == 0L) {
                copyAssetAtomically(assetPath, destination)
            }
        }
        return PreparedModels(
            rootDirectory = root,
            ocrDictionary = File(root, OCR_DICTIONARY),
        )
    }

    private fun copyAssetAtomically(assetPath: String, destination: File) {
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, "${destination.name}.part")
        temporary.delete()
        try {
            context.assets.open(assetPath).use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            if (temporary.length() == 0L) {
                throw VisionInferenceException(VisionErrorCode.ASSET_MISSING, "模型文件为空")
            }
            if (!temporary.renameTo(destination)) {
                throw VisionInferenceException(VisionErrorCode.ASSET_MISSING, "无法安装模型文件")
            }
        } catch (exception: VisionInferenceException) {
            temporary.delete()
            throw exception
        } catch (exception: Exception) {
            temporary.delete()
            throw VisionInferenceException(
                VisionErrorCode.ASSET_MISSING,
                "模型资源未完整安装：${exception.message ?: "未知错误"}",
            )
        }
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) {
                    break
                }
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    data class PreparedModels(
        val rootDirectory: File,
        val ocrDictionary: File,
    )

    private companion object {
        const val MODEL_DIRECTORY = "models-v2.10-rc"
        const val OCR_DICTIONARY = "dictionaries/ppocr_keys_v1.txt"
        val REQUIRED_ASSETS = listOf(
            "models/ocr/ch_ppocr_mobile_v2.0_det_slim_opt.nb",
            "models/ocr/ch_ppocr_mobile_v2.0_cls_slim_opt.nb",
            "models/ocr/ch_ppocr_mobile_v2.0_rec_slim_opt.nb",
            "models/object/ssd_mobilenet_v1_pascalvoc_for_cpu/model.nb",
            "models/face/model.nb",
            OCR_DICTIONARY,
        )
    }
}
