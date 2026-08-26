package com.rakshaksetu.app.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ModelDownloadManagerTest {

    private fun makeZip(files: Map<String, ByteArray>): File {
        val zip = File.createTempFile("test_model", ".zip")
        ZipOutputStream(zip.outputStream().buffered()).use { zos ->
            files.forEach { (name, bytes) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return zip
    }

    @Test
    fun `valid vosk structure passes validation`() {
        val dir = createTempDir("vosk_test").resolve("model")
        dir.resolve("conf").mkdirs()
        dir.resolve("graph").mkdirs()
        dir.resolve("am").mkdirs()
        dir.resolve("am/final.mdl").writeBytes(byteArrayOf(1, 2, 3))
        assertNotNull(ModelDownloadManager.validateAsrDir(dir))
    }

    @Test
    fun `empty or missing directory fails validation`() {
        assertNull(ModelDownloadManager.validateAsrDir(null))
        assertNull(ModelDownloadManager.validateAsrDir(createTempDir("empty")))
    }

    @Test
    fun `zip extracts into target root`() {
        val root = createTempDir("extract_root")
        val zip = makeZip(
            mapOf(
                "vosk-model-small-x/conf/model.conf" to "a=b".toByteArray(),
                "vosk-model-small-x/am/final.mdl" to byteArrayOf(9, 9),
                "vosk-model-small-x/graph/phones.txt" to "sil".toByteArray()
            )
        )

        ModelDownloadManager.unzipSafely(zip, root, "vosk-model-small-x")

        val extracted = File(root, "vosk-model-small-x")
        assertTrue(extracted.isDirectory)
        assertTrue(File(extracted, "am/final.mdl").exists())
        assertTrue(File(extracted, "graph/phones.txt").exists())
        assertNotNull(ModelDownloadManager.validateAsrDir(extracted))
        zip.delete()
    }

    @Test
    fun `zip-slip entries are rejected`() {
        val root = createTempDir("slip_root")
        val evilZip = File.createTempFile("evil", ".zip")

        ZipOutputStream(evilZip.outputStream().buffered()).use { zos ->
            zos.putNextEntry(ZipEntry("../../../../escaped_payload.txt"))
            zos.write("pwned".toByteArray())
            zos.closeEntry()
        }

        try {
            ModelDownloadManager.unzipSafely(evilZip, root, "innocent_dir")
            assertFalse("Zip-slip must be blocked", File(root.parentFile?.parentFile, "escaped_payload.txt").exists() ||
                File(root.parentFile, "escaped_payload.txt").exists())
        } catch (expected: SecurityException) {
            // Contract: extraction aborts on traversal attempt
        }
    }
}
