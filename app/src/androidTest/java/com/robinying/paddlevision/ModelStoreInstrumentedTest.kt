package com.robinying.paddlevision

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModelStoreInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun prepareReinstallsAHashMismatchedPrivateModelCopy() {
        val store = ModelStore(context)
        val installed = File(store.prepare(VisionTask.OBJECT).rootDirectory, OBJECT_MODEL_ASSET)
        installed.writeText("corrupted model")

        val repaired = File(ModelStore(context).prepare(VisionTask.OBJECT).rootDirectory, OBJECT_MODEL_ASSET)

        assertEquals(hashOfPackagedAsset(OBJECT_MODEL_ASSET), sha256(repaired))
    }

    @Test
    fun prepareOnlyVerifiesTheAssetsTheRequestedTaskLoads() {
        val root = ModelStore(context).prepare(VisionTask.FACE).rootDirectory
        val faceModel = File(root, FACE_MODEL_ASSET)
        assertTrue("The face task must install the face model", faceModel.isFile)
        assertTrue(faceModel.delete())

        val objectPrepared = ModelStore(context).prepare(VisionTask.OBJECT)

        assertTrue(
            "Preparing the object task must not hash or install OCR and face assets",
            !File(objectPrepared.rootDirectory, FACE_MODEL_ASSET).exists(),
        )
        // Leave the private directory in a complete state for the other smoke tests.
        ModelStore(context).prepare(VisionTask.FACE)
    }

    @Test
    fun aStoreInstanceHashesEachAssetAtMostOnce() {
        val store = ModelStore(context)
        val root = store.prepare(VisionTask.OBJECT).rootDirectory
        val model = File(root, OBJECT_MODEL_ASSET)
        model.writeText("corrupted model")

        val packagedHash = hashOfPackagedAsset(OBJECT_MODEL_ASSET)
        store.prepare(VisionTask.OBJECT)

        assertNotEquals(
            "A second prepare() on the same instance must reuse the earlier verification",
            packagedHash,
            sha256(model),
        )

        val freshStoreRepaired = File(ModelStore(context).prepare(VisionTask.OBJECT).rootDirectory, OBJECT_MODEL_ASSET)
        assertEquals(
            "A new process-scoped store must still catch and repair the corruption",
            packagedHash,
            sha256(freshStoreRepaired),
        )
    }

    private fun hashOfPackagedAsset(assetPath: String): String =
        context.assets.open(assetPath).use(::sha256)

    private fun sha256(file: File): String = FileInputStream(file).use(::sha256)

    private fun sha256(input: java.io.InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
