package dev.pivisolutions.dictus.asr

import timber.log.Timber
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Splits a long take into windows Parakeet decodes one by one, and stitches their text.
 *
 * WHY (#330): decoded in one block, a 60 s French take drifts into pseudo-English or comes
 * back empty in most runs on the Pixel 4, while takes of a few seconds are fine. iOS never
 * decodes 60 s in one block: FluidAudio cuts about 15 s windows aligned on silences, with
 * 2 s of overlap and a fresh decoder state per window. This is the same scheme.
 *
 * A take of up to [MAX_SEGMENT_S] is one window, decoded as before. A longer one is cut at
 * the quietest 100 ms (RMS energy) near evenly spaced targets, so no segment between two
 * cuts exceeds [MAX_SEGMENT_S]. Each window extends [MARGIN_S] past its cuts on both sides
 * (2 s of overlap), so a word cut at a window edge is still heard whole by its neighbour.
 *
 * Stitching works on whole words and times, not on matching text: a window keeps the
 * words that start between its two cuts, and the margins only give the decoder context.
 * Two windows do not time a word the same way (about 0.15 s apart on the corpus), so a
 * word near a cut can be kept by both. When the next window starts with the words the
 * previous one kept last, inside the overlap, they are the same speech decoded twice and
 * are kept once, unless the previous window also heard those words again after the cut:
 * then they were said twice.
 *
 * A window with speech can come back empty (#330: one en-long run on the Pixel lost a
 * passage). It is decoded once more, on a fresh stream, over a window [MARGIN_S] wider on
 * each side. Wider, not the same samples: sherpa-onnx decodes the same input to the same
 * output, so a plain retry would come back empty again.
 *
 * Pure functions, no native call: unit tested without sherpa-onnx.
 */
internal object ParakeetWindows {

    const val SAMPLE_RATE = 16_000

    // Longest stretch between two cuts; a take up to this long stays one window.
    const val MAX_SEGMENT_S = 15f

    // Context each window takes past its cuts; twice this is the overlap.
    const val MARGIN_S = 1f

    // How far a cut may move from its even-spacing target to land on a silence.
    private const val SEARCH_BEFORE_S = 2.5f
    private const val SEARCH_AFTER_S = 1.5f

    // Energy is summed over QUIET_FRAMES frames of FRAME samples (5 x 20 ms).
    private const val FRAME = SAMPLE_RATE / 50
    private const val QUIET_FRAMES = 5

    // Longest run of words checked for a repeat at a cut; 2 s of overlap holds about 6.
    private const val MAX_SEAM_WORDS = 6

    // RMS above which a window's kept stretch holds speech (-40 dBFS); below it, an empty
    // result is right and is not decoded again.
    private const val SPEECH_RMS = 0.01

    /**
     * A window of samples [start, end) to decode on its own. Its words starting in
     * [keepFrom, keepUntil) (sample indices of the take) go into the stitched text.
     */
    data class Window(val start: Int, val end: Int, val keepFrom: Int, val keepUntil: Int)

    /**
     * The text of a take of [samples]: decoded in one block when short, window by window
     * otherwise, each window through [decode] (a fresh stream per call).
     */
    fun transcribe(samples: FloatArray, decode: (FloatArray) -> ParakeetDecode): String {
        val windows = plan(samples)
        if (windows.size == 1) return decode(samples).text
        Timber.d("Parakeet decoding %d windows: %s", windows.size,
            windows.joinToString { "%.1f-%.1f s".format(it.start.toFloat() / SAMPLE_RATE, it.end.toFloat() / SAMPLE_RATE) })
        val decoded = windows.map { window ->
            val result = decode(samples.copyOfRange(window.start, window.end))
            if (result.text.isNotBlank() || !hasSpeech(samples, window)) return@map window to result
            val wider = widened(window, samples.size)
            Timber.w("Parakeet window %.1f-%.1f s came back empty, decoding %.1f-%.1f s once more",
                window.start.toFloat() / SAMPLE_RATE, window.end.toFloat() / SAMPLE_RATE,
                wider.start.toFloat() / SAMPLE_RATE, wider.end.toFloat() / SAMPLE_RATE)
            wider to decode(samples.copyOfRange(wider.start, wider.end))
        }
        return stitch(decoded.map { it.first }, decoded.map { it.second })
    }

    /** [window] with [MARGIN_S] more context on each side; it keeps the same words. */
    fun widened(window: Window, size: Int): Window {
        val margin = (MARGIN_S * SAMPLE_RATE).toInt()
        return window.copy(start = max(0, window.start - margin), end = min(size, window.end + margin))
    }

    /** Whether the stretch [window] keeps words from is louder than silence. */
    fun hasSpeech(samples: FloatArray, window: Window): Boolean {
        var energy = 0.0
        for (i in window.keepFrom until window.keepUntil) energy += samples[i] * samples[i]
        return sqrt(energy / (window.keepUntil - window.keepFrom)) >= SPEECH_RMS
    }

    /** The windows of a take of [samples]: one for a short take. */
    fun plan(samples: FloatArray): List<Window> {
        val cuts = cuts(samples)
        val margin = (MARGIN_S * SAMPLE_RATE).toInt()
        val bounds = listOf(0) + cuts + listOf(samples.size)
        return bounds.zipWithNext().map { (from, until) ->
            Window(
                start = if (from == 0) 0 else max(0, from - margin),
                end = if (until == samples.size) until else min(samples.size, until + margin),
                keepFrom = from,
                keepUntil = until,
            )
        }
    }

    /** Sample indices where the take is cut, in order; empty for a short take. */
    fun cuts(samples: FloatArray): List<Int> {
        val maxSegment = (MAX_SEGMENT_S * SAMPLE_RATE).toInt()
        val cuts = mutableListOf<Int>()
        var previous = 0
        while (samples.size - previous > maxSegment) {
            val remaining = samples.size - previous
            // Even spacing keeps the last window from being a few seconds of tail.
            val parts = ceil(remaining.toDouble() / maxSegment).toInt()
            val target = previous + remaining / parts
            val from = target - (SEARCH_BEFORE_S * SAMPLE_RATE).toInt()
            val until = min(target + (SEARCH_AFTER_S * SAMPLE_RATE).toInt(), previous + maxSegment)
            val cut = quietestPoint(samples, from, until, target)
            cuts += cut
            previous = cut
        }
        return cuts
    }

    // Centre of the quietest QUIET_FRAMES-frame span whose centre lies in [from, until];
    // the one closest to target among equally quiet spans.
    private fun quietestPoint(samples: FloatArray, from: Int, until: Int, target: Int): Int {
        val span = FRAME * QUIET_FRAMES
        var best = target
        var bestEnergy = Double.MAX_VALUE
        var centre = from
        while (centre <= until) {
            val start = max(0, centre - span / 2)
            val end = min(samples.size, start + span)
            var energy = 0.0
            for (i in start until end) energy += samples[i] * samples[i]
            if (energy < bestEnergy || (energy == bestEnergy && abs(centre - target) < abs(best - target))) {
                bestEnergy = energy
                best = centre
            }
            centre += FRAME
        }
        return best
    }

    /** The text of a take from the decode of each of its [windows], in order. */
    fun stitch(windows: List<Window>, decodes: List<ParakeetDecode>): String {
        require(windows.size == decodes.size) { "${windows.size} windows, ${decodes.size} decodes" }
        if (windows.size == 1) return decodes.single().text
        val kept = mutableListOf<Word>()
        // What the previous window heard after its cut, and where its audio ended.
        var heardAfterCut = emptyList<Word>()
        var previousEnd = 0f
        windows.zip(decodes).forEach { (window, decode) ->
            val offset = window.start.toFloat() / SAMPLE_RATE
            val from = window.keepFrom.toFloat() / SAMPLE_RATE
            val until = window.keepUntil.toFloat() / SAMPLE_RATE
            // Without a time per token the window cannot be trimmed to its cuts: keep its
            // whole text, which may repeat a word or two of the overlap rather than lose any.
            val all = if (decode.timestamps.size != decode.tokens.size) {
                listOf(Word(decode.text.trim(), from)).filter { it.text.isNotEmpty() }
            } else {
                words(decode).map { it.copy(time = it.time + offset) }
            }
            val words = all.filter { it.time >= from && it.time < until }
            kept += words.drop(repeatedAtSeam(kept, words, heardAfterCut, previousEnd))
            heardAfterCut = all.filter { it.time >= until }
            previousEnd = window.end.toFloat() / SAMPLE_RATE
        }
        return kept.joinToString(" ") { it.text }
    }

    /**
     * How many of [next]'s first words repeat the end of [kept] across a cut: the longest
     * run (up to [MAX_SEAM_WORDS]) that [kept] ends with and [next] starts with, before
     * [previousEnd] (so the previous window heard that audio too). Zero when the previous
     * window heard the same run again after the cut ([heardAfterCut]): the words were said
     * twice.
     */
    fun repeatedAtSeam(kept: List<Word>, next: List<Word>, heardAfterCut: List<Word>, previousEnd: Float): Int {
        val first = next.firstOrNull() ?: return 0
        if (kept.isEmpty() || first.time >= previousEnd) return 0
        return (minOf(MAX_SEAM_WORDS, kept.size, next.size) downTo 1).firstOrNull { n ->
            val run = next.take(n).map { normalized(it.text) }
            kept.takeLast(n).map { normalized(it.text) } == run && heardAfterCut.take(n).map { normalized(it.text) } != run
        } ?: 0
    }

    /** A word and the time its first token starts. */
    data class Word(val text: String, val time: Float)

    /**
     * The words of a decode with the start time of their first token. A token starting with
     * « ▁ » (SentencePiece) or a space opens a word; the others, punctuation included,
     * continue the current one.
     */
    fun words(decode: ParakeetDecode): List<Word> {
        val words = mutableListOf<Word>()
        var text = StringBuilder()
        var time = 0f
        decode.tokens.zip(decode.timestamps).forEach { (token, start) ->
            val piece = token.replace('▁', ' ')
            if (piece.startsWith(" ") && text.isNotBlank()) {
                words += Word(text.toString().trim(), time)
                text = StringBuilder()
            }
            if (text.isBlank()) time = start
            text.append(piece)
        }
        if (text.isNotBlank()) words += Word(text.toString().trim(), time)
        return words
    }

    private fun normalized(word: String) = word.lowercase().filter { it.isLetterOrDigit() }
}
