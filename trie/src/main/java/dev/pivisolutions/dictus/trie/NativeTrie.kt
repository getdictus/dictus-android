package dev.pivisolutions.dictus.trie

import android.content.Context
import java.io.Closeable
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

enum class TrieKeyboardLayout(internal val nativeValue: Int) {
    AZERTY(0),
    QWERTY(1),
}

/** Owns one native mmap-backed dictionary. Instances must be closed. */
class NativeTrie private constructor(
    private var nativeHandle: Long,
) : Closeable {
    companion object {
        private const val MAX_INPUT_LENGTH = 32
        private const val MAX_EDIT_DISTANCE = 2f
        private val installLock = Any()
        private val assetHashes = mapOf(
            "en_spellcheck.dict" to "2721a68a1dca369b6a23d149405c99bbff5071caa973843b896fad088912b11c",
            "fr_spellcheck.dict" to "4f52b3cd584ff1844ad09719d21751d93ebc76e506bde0b9b958d876b886f92d",
        )

        init {
            System.loadLibrary("dictus_trie")
        }

        fun open(
            context: Context,
            assetName: String,
            layout: TrieKeyboardLayout,
        ): NativeTrie {
            require(assetName in setOf("fr_spellcheck.dict", "en_spellcheck.dict")) {
                "Unsupported dictionary asset"
            }
            val nativeTrie = NativeTrie(0L)
            val handle = nativeTrie.nativeCreate()
            check(handle != 0L) { "Unable to allocate native trie" }
            nativeTrie.nativeHandle = handle
            try {
                val file = copyAssetAtomically(context, assetName)
                check(nativeTrie.nativeLoad(handle, file.absolutePath)) {
                    "Invalid trie dictionary"
                }
                nativeTrie.nativeSetLayout(handle, layout.nativeValue)
                return nativeTrie
            } catch (error: Throwable) {
                nativeTrie.close()
                throw error
            }
        }

        private fun copyAssetAtomically(context: Context, assetName: String): File {
            return synchronized(installLock) {
                val directory = File(context.noBackupFilesDir, "trie").apply {
                    check(mkdirs() || isDirectory) { "Unable to create trie directory" }
                }
                val destination = File(directory, assetName)
                val temporary = File.createTempFile("$assetName.", ".tmp", directory)
                try {
                    context.assets.open(assetName).use { input ->
                        temporary.outputStream().use { output -> input.copyTo(output) }
                    }
                    check(sha256(temporary) == assetHashes.getValue(assetName)) {
                        "Invalid trie dictionary asset"
                    }
                    Files.move(
                        temporary.toPath(),
                        destination.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                    destination
                } finally {
                    temporary.delete()
                }
            }
        }

        private fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        }
    }

    @Synchronized
    fun wordExists(word: String): Boolean {
        check(nativeHandle != 0L) { "Trie is closed" }
        return nativeWordExists(nativeHandle, word)
    }

    @Synchronized
    fun correct(
        word: String,
        maxEditDistance: Float = 2f,
        maxResults: Int = 5,
    ): List<String> {
        check(nativeHandle != 0L) { "Trie is closed" }
        require(maxEditDistance > 0f && maxEditDistance <= MAX_EDIT_DISTANCE)
        require(maxResults in 1..20)
        if (word.isEmpty() || word.length > MAX_INPUT_LENGTH) return emptyList()
        return nativeCorrect(nativeHandle, word, maxEditDistance, maxResults).toList()
    }

    @Synchronized
    override fun close() {
        if (nativeHandle != 0L) {
            nativeDestroy(nativeHandle)
            nativeHandle = 0L
        }
    }

    private external fun nativeCreate(): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeLoad(handle: Long, path: String): Boolean
    private external fun nativeSetLayout(handle: Long, layout: Int)
    private external fun nativeWordExists(handle: Long, word: String): Boolean
    private external fun nativeCorrect(
        handle: Long,
        word: String,
        maxEditDistance: Float,
        maxResults: Int,
    ): Array<String>
}