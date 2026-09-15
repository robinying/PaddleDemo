package com.robinying.paddlevision

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

internal const val OCR_DETECTOR_ASSET = "models/ocr/ch_ppocr_mobile_v2.0_det_slim_opt.nb"
internal const val OCR_RECOGNIZER_ASSET = "models/ocr/ch_ppocr_mobile_v2.0_rec_slim_opt.nb"
internal const val OCR_DICTIONARY_ASSET = "dictionaries/ppocr_keys_v1.txt"
internal const val OBJECT_MODEL_ASSET = "models/object/ssd_mobilenet_v1_pascalvoc_for_cpu/model.nb"
internal const val FACE_MODEL_ASSET = "models/face/model.nb"

/**
 * Installs verified model assets in private storage.
 *
 * [prepare] validates only the assets the requested task actually needs, and remembers
 * which paths it already verified so a 26.7 MB SHA-256 sweep does not repeat on every
 * run. The private copies cannot change while the process lives, and every copy is still
 * hash-checked when it is first installed or revalidated.
 */
class ModelStore(context: Context) {
    private val appContext = context.applicationContext

    /** Asset paths verified in this process, guarded by [verifiedLock]. */
    private val verifiedPaths = mutableSetOf<String>()
    private val verifiedLock = Any()

    fun prepare(task: VisionTask): PreparedModels {
        val root = File(appContext.filesDir, MODEL_DIRECTORY)
        assetsFor(task).forEach { assetPath ->
            if (isVerifiedInThisProcess(assetPath)) {
                return@forEach
            }
            val destination = File(root, assetPath)
            if (!destination.matchesHash(assetPath)) {
                copyAssetAtomically(assetPath, destination)
            }
            markVerified(assetPath)
        }
        return PreparedModels(rootDirectory = root)
    }

    /** Assets the given task loads at runtime. */
    private fun assetsFor(task: VisionTask): List<String> = when (task) {
        VisionTask.OCR -> listOf(OCR_DETECTOR_ASSET, OCR_RECOGNIZER_ASSET, OCR_DICTIONARY_ASSET)
        VisionTask.OBJECT -> listOf(OBJECT_MODEL_ASSET)
        VisionTask.FACE -> listOf(FACE_MODEL_ASSET)
    }

    private fun isVerifiedInThisProcess(assetPath: String): Boolean = synchronized(verifiedLock) {
        assetPath in verifiedPaths
    }

    private fun markVerified(assetPath: String) {
        synchronized(verifiedLock) { verifiedPaths += assetPath }
    }

    private fun copyAssetAtomically(assetPath: String, destination: File) {
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, "${destination.name}.part")
        temporary.delete()
        try {
            appContext.assets.open(assetPath).use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            if (!temporary.matchesHash(assetPath)) {
                throw VisionInferenceException(
                    VisionErrorCode.ASSET_MISSING,
                    UiText(R.string.error_asset_integrity_failed),
                )
            }
            if (!temporary.renameTo(destination)) {
                throw VisionInferenceException(
                    VisionErrorCode.ASSET_MISSING,
                    UiText(R.string.error_asset_install_failed),
                )
            }
        } catch (exception: VisionInferenceException) {
            temporary.delete()
            throw exception
        } catch (exception: Exception) {
            temporary.delete()
            throw VisionInferenceException(
                VisionErrorCode.ASSET_MISSING,
                UiText(
                    R.string.error_asset_install_incomplete,
                    listOf(exception.message ?: UiText(R.string.error_unknown)),
                ),
                exception,
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

    private fun File.matchesHash(assetPath: String): Boolean {
        val expectedHash = EXPECTED_HASHES[assetPath] ?: return false
        return isFile && length() > 0L && sha256(this) == expectedHash
    }

    data class PreparedModels(
        val rootDirectory: File,
    )

    private companion object {
        const val MODEL_DIRECTORY = "models-v2.10-rc"

        val EXPECTED_HASHES = mapOf(
            OCR_DETECTOR_ASSET to
                "62f649256f2f338a1ac4dcc2f3a78e48af24b19adb56f203f7242a00846793cc",
            OCR_RECOGNIZER_ASSET to
                "d785a3e4dceee7de16a3c411998209a68c04cdbfb266114fff92a3cbcbd12571",
            OBJECT_MODEL_ASSET to
                "655ab6f3650ca82d3e50598b949e134a512d756b728b697b6abdfe83ed1721a8",
            FACE_MODEL_ASSET to
                "b0577813cc04992f83365282dbf2325e421249c0acae113d346ae07904d1fc46",
            OCR_DICTIONARY_ASSET to
                "28b2362ad4ab2dc38769aa72feb535e3a9ddb3fd2a7585a05920e6393b1dc7f7",
        )
    }
}
