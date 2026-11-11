package dev.pivisolutions.dictus.service

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** An app take's audio on disk as it records, until its stop hands the file over (#251). */
class TakeFilesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val takes by lazy { TakeFiles(tmp.root.resolve("takes")) }

    @Test
    fun `each chunk is on disk before the capture stops`() {
        val writer = takes.startLive()!!
        val first = floatArrayOf(0.1f, -0.2f, 0.3f)
        val second = floatArrayOf(0.4f, -1f, 1f, 9f)

        writer.append(first, first.size)
        val file = takes.orphans().single()
        assertArrayEquals(first, TakeFiles.read(file), 0f)

        // Only the first count samples of the read buffer belong to the take.
        writer.append(second, 3)
        assertArrayEquals(floatArrayOf(0.1f, -0.2f, 0.3f, 0.4f, -1f, 1f), TakeFiles.read(file), 0f)
        writer.close()
    }

    @Test
    fun `a sample cut by the process kill is dropped`() {
        val writer = takes.startLive()!!
        writer.append(floatArrayOf(0.5f, 0.25f), 2)
        writer.close()
        val file = takes.orphans().single()
        file.appendBytes(byteArrayOf(1, 2))

        assertArrayEquals(floatArrayOf(0.5f, 0.25f), TakeFiles.read(file), 0f)
    }

    @Test
    fun `the live file is handed over once`() {
        takes.startLive()!!.close()

        val file = takes.detachLive()

        assertEquals(takes.orphans().single(), file)
        assertNull(takes.detachLive())
    }

    @Test
    fun `no take directory means no orphan`() {
        assertEquals(emptyList<Any>(), takes.orphans())
    }
}
