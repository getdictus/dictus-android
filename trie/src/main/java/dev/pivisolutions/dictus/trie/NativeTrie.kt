package dev.pivisolutions.dictus.trie

import android.content.Context
import java.io.Closeable
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

enum class TrieKeyboardLayout(internal val nativeValue: Int) {
    AZERTY(0),
    QWERTY(1),
}

data class NgramPrediction(
    val word: String,
    val score: Int,
)

/** Owns one native mmap-backed dictionary. Instances must be closed. */
class NativeTrie private constructor(
    private var nativeHandle: Long,
) : Closeable {
    var hasNgram: Boolean = false
        private set
    companion object {
        private const val MAX_INPUT_LENGTH = 32
        private const val MAX_EDIT_DISTANCE = 2f
        private val installLock = Any()
        private val assetHashes = mapOf(
            "de_spellcheck.dict" to "663b8945ac8d94b4c1da322965454e4ae52fc9adac04b4206b41b96b1199a18a",
            "en_spellcheck.dict" to "2721a68a1dca369b6a23d149405c99bbff5071caa973843b896fad088912b11c",
            "es_spellcheck.dict" to "39015e063ea69282ff6ac3099e852fe06b94118f41a69a680f75582899bb9ab4",
            "fr_spellcheck.dict" to "4f52b3cd584ff1844ad09719d21751d93ebc76e506bde0b9b958d876b886f92d",
            "de_ngrams.dict" to "89219a332ed98b651abde529f9156ebf9f3e251355d9a880468bddbbfb3a303d",
            "en_ngrams.dict" to "eecdf421c71c39e9f7822cb48a8624efb9ae8d86c6a01e976c6fa506f2fc71bd",
            "es_ngrams.dict" to "d48691aa2c8c95fff9f831f32e3ae565917f17352ea696f1fd6d655fb320750e",
            "fr_ngrams.dict" to "ba8f8b3ea9ade673eeb158e4aaeebcb129647938b8cb619189393cbb1c6da712",
        )
        private val ngramAssets = mapOf(
            "de_spellcheck.dict" to "de_ngrams.dict",
            "en_spellcheck.dict" to "en_ngrams.dict",
            "es_spellcheck.dict" to "es_ngrams.dict",
            "fr_spellcheck.dict" to "fr_ngrams.dict",
        )

        init {
            System.loadLibrary("dictus_trie")
        }

        fun open(
            context: Context,
            assetName: String,
            layout: TrieKeyboardLayout,
        ): NativeTrie {
            require(
                assetName in setOf(
                    "de_spellcheck.dict",
                    "en_spellcheck.dict",
                    "es_spellcheck.dict",
                    "fr_spellcheck.dict",
                ),
            ) {
                "Unsupported dictionary asset"
            }
            val spellFile = copyAssetAtomically(context, assetName)
            // N-grams are optional enrichment. Integrity, copy, or native format failure must
            // never prevent the matching spell trie from becoming the complete activation.
            val ngramFile = runCatching {
                copyAssetAtomically(context, ngramAssets.getValue(assetName))
            }.getOrNull()
            return openPath(spellFile, layout, ngramFile)
        }

        /** Test seam for malformed native fixtures; production callers use verified assets. */
        internal fun openPathForTesting(file: File, layout: TrieKeyboardLayout): NativeTrie =
            openPath(file, layout, null)

        /** Test seam for production-pair lifecycle and corrupt optional n-gram fallback. */
        internal fun openPathsForTesting(
            spellFile: File,
            layout: TrieKeyboardLayout,
            ngramFile: File?,
        ): NativeTrie = openPath(spellFile, layout, ngramFile)

        private fun openPath(
            file: File,
            layout: TrieKeyboardLayout,
            ngramFile: File?,
        ): NativeTrie {
            val nativeTrie = NativeTrie(0L)
            val handle = nativeTrie.nativeCreate()
            check(handle != 0L) { "Unable to allocate native trie" }
            nativeTrie.nativeHandle = handle
            try {
                check(nativeTrie.nativeLoad(handle, file.absolutePath)) {
                    "Invalid trie dictionary"
                }
                nativeTrie.nativeSetLayout(handle, layout.nativeValue)
                val loadedNgram = ngramFile != null && nativeTrie.nativeLoadNgram(
                    handle,
                    ngramFile.absolutePath.toByteArray(StandardCharsets.UTF_8),
                )
                if (loadedNgram) {
                    nativeTrie.hasNgram = true
                }
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
                    check(destination.setReadOnly() || !destination.canWrite()) {
                        "Unable to protect installed dictionary asset"
                    }
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
        val canonical = word.canonicalLookup()
        if (canonical.isEmpty() || canonical.length > MAX_INPUT_LENGTH) return false
        return nativeWordExists(nativeHandle, canonical)
    }

    /** Returns the dictionary's encoded uint16 log-frequency score, or zero when absent. */
    @Synchronized
    fun frequency(word: String): Int {
        check(nativeHandle != 0L) { "Trie is closed" }
        val canonical = word.canonicalLookup()
        if (canonical.isEmpty() || canonical.length > MAX_INPUT_LENGTH) return 0
        return nativeFrequency(nativeHandle, canonical)
    }

    /** Raw maximum frequency used when the dictionary encoded its log-frequency scores. */
    @Synchronized
    fun maxFrequency(): Long {
        check(nativeHandle != 0L) { "Trie is closed" }
        return nativeMaxFrequency(nativeHandle)
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
        val canonical = word.canonicalLookup()
        if (canonical.isEmpty() || canonical.length > MAX_INPUT_LENGTH) return emptyList()
        return nativeCorrect(
            nativeHandle,
            canonical,
            maxEditDistance,
            maxResults,
        ).toList()
    }

    /** Returns bounded, deterministic highest-frequency completions, excluding [prefix] itself. */
    @Synchronized
    fun complete(prefix: String, maxResults: Int = 3): List<String> {
        check(nativeHandle != 0L) { "Trie is closed" }
        require(maxResults in 1..20)
        val canonical = prefix.canonicalLookup()
        if (canonical.isEmpty() || canonical.length > MAX_INPUT_LENGTH) return emptyList()
        return nativeComplete(nativeHandle, canonical, maxResults).toList()
    }

    /** Test-only path seam for malformed and replacement native fixtures. */
    @Synchronized
    internal fun loadNgramForTesting(file: File): Boolean {
        check(nativeHandle != 0L) { "Trie is closed" }
        return nativeLoadNgram(
            nativeHandle,
            file.absolutePath.toByteArray(StandardCharsets.UTF_8),
        ).also { hasNgram = it }
    }

    @Synchronized
    internal fun unloadNgramForTesting() {
        check(nativeHandle != 0L) { "Trie is closed" }
        nativeUnloadNgram(nativeHandle)
        hasNgram = false
    }

    @Synchronized
    internal fun isNgramLoadedForTesting(): Boolean {
        check(nativeHandle != 0L) { "Trie is closed" }
        return nativeIsNgramLoaded(nativeHandle)
    }

    @Synchronized
    fun predictAfterWord(word: String, maxResults: Int = 3): List<NgramPrediction> {
        check(nativeHandle != 0L) { "Trie is closed" }
        require(maxResults in 1..16)
        val canonical = word.canonicalLookup()
        if (!canonical.isNgramContext()) return emptyList()
        return nativePredictAfterWord(
            nativeHandle,
            canonical.toByteArray(StandardCharsets.UTF_8),
            maxResults,
        ).toList()
    }

    @Synchronized
    fun predictAfterWords(
        firstWord: String,
        secondWord: String,
        maxResults: Int = 3,
    ): List<NgramPrediction> {
        check(nativeHandle != 0L) { "Trie is closed" }
        require(maxResults in 1..16)
        val canonicalFirst = firstWord.canonicalLookup()
        val canonicalSecond = secondWord.canonicalLookup()
        if (!canonicalFirst.isNgramContext() || !canonicalSecond.isNgramContext()) {
            return emptyList()
        }
        return nativePredictAfterWords(
            nativeHandle,
            canonicalFirst.toByteArray(StandardCharsets.UTF_8),
            canonicalSecond.toByteArray(StandardCharsets.UTF_8),
            maxResults,
        ).toList()
    }

    @Synchronized
    fun bigramScore(previousWord: String, word: String): Int {
        check(nativeHandle != 0L) { "Trie is closed" }
        val canonicalPrevious = previousWord.canonicalLookup()
        val canonicalWord = word.canonicalLookup()
        if (!canonicalPrevious.isNgramContext() || !canonicalWord.isNgramContext()) return 0
        return nativeBigramScore(
            nativeHandle,
            canonicalPrevious.toByteArray(StandardCharsets.UTF_8),
            canonicalWord.toByteArray(StandardCharsets.UTF_8),
        )
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
    private external fun nativeFrequency(handle: Long, word: String): Int
    private external fun nativeMaxFrequency(handle: Long): Long
    private external fun nativeCorrect(
        handle: Long,
        word: String,
        maxEditDistance: Float,
        maxResults: Int,
    ): Array<String>
    private external fun nativeComplete(
        handle: Long,
        prefix: String,
        maxResults: Int,
    ): Array<String>
    private external fun nativeLoadNgram(handle: Long, path: ByteArray): Boolean
    private external fun nativeUnloadNgram(handle: Long)
    private external fun nativeIsNgramLoaded(handle: Long): Boolean
    private external fun nativePredictAfterWord(
        handle: Long,
        word: ByteArray,
        maxResults: Int,
    ): Array<NgramPrediction>
    private external fun nativePredictAfterWords(
        handle: Long,
        firstWord: ByteArray,
        secondWord: ByteArray,
        maxResults: Int,
    ): Array<NgramPrediction>
    private external fun nativeBigramScore(
        handle: Long,
        previousWord: ByteArray,
        word: ByteArray,
    ): Int

    private fun String.canonicalLookup(): String =
        Normalizer.normalize(lowercase(Locale.ROOT), Normalizer.Form.NFC)

    private fun String.isNgramContext(): Boolean =
        isNotEmpty() && '\u0000' !in this &&
            toByteArray(StandardCharsets.UTF_8).size <= 255
}