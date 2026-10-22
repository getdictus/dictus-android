package dev.pivisolutions.dictus

import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
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
        val result = run(60, "bash", scripts.resolve("test-derive-android-version.sh").path, mergeStderr = true)
        assertEquals(result.output, 0, result.exitCode)
    }

    @Test
    fun `release v1_3_0 keeps versionCode 10300999`() {
        val result = run(10, "bash", scripts.resolve("derive-android-version.sh").path, "v1.3.0")
        assertEquals("version_name=1.3.0\nversion_code=10300999\n", result.output)
    }

    private class Result(val exitCode: Int, val output: String)

    /**
     * Stdout goes to a temp file rather than a pipe, so waitFor's timeout holds even if
     * the script hangs: reading a pipe first would block until the script exits.
     */
    private fun run(timeoutSeconds: Long, vararg command: String, mergeStderr: Boolean = false): Result {
        val outputFile = File.createTempFile("derive-android-version", ".out")
        try {
            val process = ProcessBuilder(*command)
                .redirectErrorStream(mergeStderr)
                .redirectOutput(outputFile)
                .start()
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                fail("${command.joinToString(" ")} timed out after ${timeoutSeconds}s:\n${outputFile.readText()}")
            }
            return Result(process.exitValue(), outputFile.readText())
        } finally {
            outputFile.delete()
        }
    }
}
