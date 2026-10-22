package dev.pivisolutions.dictus

import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs the shell tests of derive-android-version.sh, the single source of the app version
 * for release and dev builds (#165). Living here puts them in testDebugUnitTest, the task
 * CI already runs.
 */
class VersionDerivationScriptTest {

    private val scripts: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { it.resolve(".github/scripts") }
        .first { it.resolve("derive-android-version.sh").isFile }

    @Test
    fun `release and dev version derivation tests pass`() {
        val process = ProcessBuilder("bash", scripts.resolve("test-derive-android-version.sh").path)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        assertTrue("script timed out", process.waitFor(60, TimeUnit.SECONDS))
        assertEquals(output, 0, process.exitValue())
    }

    @Test
    fun `release v1_3_0 keeps versionCode 10300999`() {
        val process = ProcessBuilder("bash", scripts.resolve("derive-android-version.sh").path, "v1.3.0").start()
        val output = process.inputStream.bufferedReader().readText()
        assertTrue(process.waitFor(10, TimeUnit.SECONDS))
        assertEquals("version_name=1.3.0\nversion_code=10300999\n", output)
    }
}
