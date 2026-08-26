package com.robinying.paddlevision

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModelStoreInstrumentedTest {
    @Test
    fun prepareReinstallsAHashMismatchedPrivateModelCopy() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = ModelStore(context)
        val modelPath = "models/object/ssd_mobilenet_v1_pascalvoc_for_cpu/model.nb"
        val installed = File(store.prepare().rootDirectory, modelPath)
        installed.writeText("corrupted model")

        val repaired = File(store.prepare().rootDirectory, modelPath)
        val packagedHash = context.assets.open(modelPath).use(::sha256)

        assertEquals(packagedHash, sha256(repaired))
    }

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
