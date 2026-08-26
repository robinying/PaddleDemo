package com.robinying.paddlevision

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/** Installs verified model assets in private storage. */
class ModelStore(private val context: android.content.Context) {
    fun prepare(): PreparedModels {
        val root = File(context.filesDir, MODEL_DIRECTORY)
        REQUIRED_ASSETS.forEach { (assetPath, expectedHash) ->
            val destination = File(root, assetPath)
            if (!destination.matchesHash(expectedHash)) {
                copyAssetAtomically(assetPath, destination, expectedHash)
            }
        }
        return PreparedModels(
            rootDirectory = root,
            ocrDictionary = File(root, OCR_DICTIONARY),
        )
    }

    private fun copyAssetAtomically(assetPath: String, destination: File, expectedHash: String) {
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, "${destination.name}.part")
        temporary.delete()
        try {
            context.assets.open(assetPath).use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            if (!temporary.matchesHash(expectedHash)) {
                throw VisionInferenceException(VisionErrorCode.ASSET_MISSING, "模型资源完整性校验失败")
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

    private fun File.matchesHash(expectedHash: String): Boolean = isFile && length() > 0L && sha256(this) == expectedHash

    data class PreparedModels(
        val rootDirectory: File,
        val ocrDictionary: File,
    )

    private companion object {
        const val MODEL_DIRECTORY = "models-v2.10-rc"
        const val OCR_DICTIONARY = "dictionaries/ppocr_keys_v1.txt"
        val REQUIRED_ASSETS = mapOf(
            "models/ocr/ch_ppocr_mobile_v2.0_det_slim_opt.nb" to
                "62f649256f2f338a1ac4dcc2f3a78e48af24b19adb56f203f7242a00846793cc",
            "models/ocr/ch_ppocr_mobile_v2.0_cls_slim_opt.nb" to
                "93073baa6df79bcb83c63570d73d6db7f20aae15dda45398f6b3304e10067c8d",
            "models/ocr/ch_ppocr_mobile_v2.0_rec_slim_opt.nb" to
                "d785a3e4dceee7de16a3c411998209a68c04cdbfb266114fff92a3cbcbd12571",
            "models/object/ssd_mobilenet_v1_pascalvoc_for_cpu/model.nb" to
                "655ab6f3650ca82d3e50598b949e134a512d756b728b697b6abdfe83ed1721a8",
            "models/face/model.nb" to
                "b0577813cc04992f83365282dbf2325e421249c0acae113d346ae07904d1fc46",
            OCR_DICTIONARY to
                "28b2362ad4ab2dc38769aa72feb535e3a9ddb3fd2a7585a05920e6393b1dc7f7",
        )
    }
}
