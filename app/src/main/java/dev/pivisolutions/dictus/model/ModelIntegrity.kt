package dev.pivisolutions.dictus.model

import java.io.File
import java.security.MessageDigest

/**
 * Checks installed model files against their [ModelFile] manifest (#169).
 *
 * Two levels, because they cost very different amounts:
 * - [sizesMatch]: every file exists with its exact size. A few `stat` calls, so the model list
 *   can run it on every refresh.
 * - [problemWithFile] / [problemWithDirectory]: exact size, then the SHA-256 of the content.
 *   Reads every byte (about 670 MB for Parakeet 0.6B v3), so it runs once, at the end of an
 *   install, before the files are moved to where the engine loads them.
 *
 * The problem functions return null when the files are good, otherwise a reason for the log.
 */
object ModelIntegrity {

    /** True when every file of [expected] exists in [dir] with its exact size. */
    fun sizesMatch(dir: File, expected: List<ModelFile>): Boolean =
        expected.all { File(dir, it.name).let { file -> file.isFile && file.length() == it.sizeBytes } }

    /** Null when [file] has exactly the size and SHA-256 of [expected], else the reason. */
    fun problemWithFile(file: File, expected: ModelFile): String? {
        if (!file.isFile) return "${expected.name}: missing"
        val size = file.length()
        if (size != expected.sizeBytes) return "${expected.name}: $size bytes, expected ${expected.sizeBytes}"
        val sha256 = sha256(file)
        if (sha256 != expected.sha256) return "${expected.name}: sha256 $sha256, expected ${expected.sha256}"
        return null
    }

    /**
     * Null when [dir] holds exactly the files of [expected] and each passes [problemWithFile].
     *
     * WHY reject extra files: ParakeetProvider picks the first `decoder*.onnx` and `joiner*.onnx`
     * it finds, so a stray file next to the right one could be the one it loads.
     */
    fun problemWithDirectory(dir: File, expected: List<ModelFile>): String? {
        val extra = dir.list().orEmpty().toSet() - expected.map { it.name }.toSet()
        if (extra.isNotEmpty()) return "unexpected files: ${extra.sorted()}"
        return expected.firstNotNullOfOrNull { problemWithFile(File(dir, it.name), it) }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n == -1) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
