package dev.pivisolutions.dictus.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

/** ModelIntegrity against real files: exact size and SHA-256 for every file (#169). */
class ModelIntegrityTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val encoderBytes = ByteArray(4096) { (it * 7).toByte() }
    private val tokensBytes = "a 0\nb 1\n".toByteArray()
    private val manifest = listOf(manifestOf("encoder.int8.onnx", encoderBytes), manifestOf("tokens.txt", tokensBytes))

    private fun manifestOf(name: String, bytes: ByteArray) = ModelFile(
        name,
        bytes.size.toLong(),
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
    )

    private fun installedDir(): File = tmp.newFolder("model").also {
        File(it, "encoder.int8.onnx").writeBytes(encoderBytes)
        File(it, "tokens.txt").writeBytes(tokensBytes)
    }

    @Test
    fun `exact files pass`() {
        val dir = installedDir()
        assertNull(ModelIntegrity.problemWithDirectory(dir, manifest))
        assertTrue(ModelIntegrity.sizesMatch(dir, manifest))
    }

    @Test
    fun `truncated file fails`() {
        val dir = installedDir()
        File(dir, "encoder.int8.onnx").writeBytes(encoderBytes.copyOf(encoderBytes.size - 1))
        val problem = ModelIntegrity.problemWithDirectory(dir, manifest)
        assertEquals("encoder.int8.onnx: 4095 bytes, expected 4096", problem)
        assertFalse(ModelIntegrity.sizesMatch(dir, manifest))
    }

    @Test
    fun `oversize file fails`() {
        val dir = installedDir()
        File(dir, "tokens.txt").appendBytes(byteArrayOf(0))
        assertNotNull(ModelIntegrity.problemWithDirectory(dir, manifest))
        assertFalse(ModelIntegrity.sizesMatch(dir, manifest))
    }

    @Test
    fun `missing file fails`() {
        val dir = installedDir()
        File(dir, "tokens.txt").delete()
        assertEquals("tokens.txt: missing", ModelIntegrity.problemWithDirectory(dir, manifest))
        assertFalse(ModelIntegrity.sizesMatch(dir, manifest))
    }

    @Test
    fun `extra file fails`() {
        val dir = installedDir()
        File(dir, "decoder.onnx").writeBytes(byteArrayOf(1, 2, 3))
        assertEquals("unexpected files: [decoder.onnx]", ModelIntegrity.problemWithDirectory(dir, manifest))
    }

    @Test
    fun `same size but different content fails on the hash`() {
        val dir = installedDir()
        File(dir, "encoder.int8.onnx").writeBytes(encoderBytes.copyOf().also { it[100] = (it[100] + 1).toByte() })
        assertTrue(ModelIntegrity.sizesMatch(dir, manifest))
        val problem = ModelIntegrity.problemWithDirectory(dir, manifest)
        assertTrue(problem, problem!!.startsWith("encoder.int8.onnx: sha256 "))
    }

    @Test
    fun `single file check`() {
        val file = tmp.newFile("ggml-tiny.bin").apply { writeBytes(encoderBytes) }
        assertNull(ModelIntegrity.problemWithFile(file, manifestOf("ggml-tiny.bin", encoderBytes)))
        file.writeBytes(encoderBytes.copyOf(10))
        assertNotNull(ModelIntegrity.problemWithFile(file, manifestOf("ggml-tiny.bin", encoderBytes)))
    }
}
