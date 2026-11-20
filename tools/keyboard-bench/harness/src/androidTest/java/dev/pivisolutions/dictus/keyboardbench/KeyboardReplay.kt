package dev.pivisolutions.dictus.keyboardbench

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * Replays a committed touch run on the keyboard the device has selected, Dictus or Gboard (#382).
 *
 * The keyboard is not compiled in: the taps go through the system's input dispatch into the IME
 * window, so the hit test is the keyboard's own and every space runs the autocorrect the space bar
 * runs. What each tap typed is read back from the field.
 *
 * Arguments (`am instrument -e <name> <value>`):
 * - `lang`: `fr` or `en`, the run to replay (`runs/<lang>.tsv` and `corpus/<lang>.txt`, bundled in the test APK).
 * - `out`: the output directory name, under the app's external files directory.
 * - `mode`: `replay` (default), `keys` (geometry only) or `probe` (taps swept across key boundaries).
 * - `sentences`: optional comma-separated sentence numbers, to replay a few.
 */
@RunWith(AndroidJUnit4::class)
class KeyboardReplay {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val args = InstrumentationRegistry.getArguments()
    private val ua: UiAutomation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)

    data class Tap(val sentence: Int, val index: Int, val key: String, val dx: Double, val dy: Double, val tMs: Long)

    /** A key as the keyboard exposes it to accessibility: its label and its centre in screen pixels. */
    data class Key(
        val label: String,
        val bounds: Rect,
        val cx: Double = bounds.exactCenterX().toDouble(),
        val cy: Double = bounds.exactCenterY().toDouble(),
    ) {
    }

    /**
     * The letter page as the replay aims at it. One key unit across is the distance between the
     * centres of two neighbouring letters, and one unit down the distance between two letter rows:
     * the run's offsets are in those units on every keyboard, as on iOS.
     */
    data class Geometry(val keys: Map<String, Key>, val pitchX: Double, val pitchY: Double) {
        fun point(key: String, dx: Double, dy: Double): Pair<Float, Float> {
            val k = keys[key] ?: error("no key $key on this keyboard: ${keys.keys.sorted()}")
            return Pair((k.cx + dx * pitchX).toFloat(), (k.cy + dy * pitchY).toFloat())
        }
    }

    /** What one tap did to the field. */
    data class Outcome(val resolved: String, val after: String, val downToTextUs: Long, val upToTextUs: Long)

    @Test
    fun run() {
        val lang = args.getString("lang") ?: error("pass -e lang fr|en")
        val outName = args.getString("out") ?: error("pass -e out <dir name>")
        val mode = args.getString("mode") ?: "replay"
        ActivityScenario.launch(FieldActivity::class.java).use { scenario ->
            lateinit var activity: FieldActivity
            scenario.onActivity { activity = it }
            val out = File(activity.getExternalFilesDir(null), outName).apply { mkdirs() }
            reset(activity)
            val (geometry, raw) = switchToLayoutOf(lang)
            writeKeys(File(out, "keys.tsv"), geometry, raw)
            when (mode) {
                "keys" -> Unit
                "replay" -> replay(activity, geometry, lang, out)
                "probe" -> probe(activity, geometry, File(out, "probe.tsv"))
                else -> error("unknown mode $mode")
            }
            status("done: ${out.absolutePath}")
        }
    }

    // region Geometry

    private fun imeRoot(): AccessibilityNodeInfo? {
        val info = ua.serviceInfo
        if (info.flags and AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS == 0) {
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            ua.serviceInfo = info
        }
        return ua.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }?.root
    }

    private fun waitForKeyboard(): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + KEYBOARD_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            imeRoot()?.let { root -> if (labelledNodes(root).any { it.label == "a" }) return root }
            SystemClock.sleep(100)
        }
        error("no keyboard with an `a` key on screen")
    }

    /** Every node with a label, in screen pixels. Labels are lowercased: the shift state is not under test. */
    private fun labelledNodes(root: AccessibilityNodeInfo): List<Key> {
        val found = mutableListOf<Key>()
        fun walk(node: AccessibilityNodeInfo) {
            val label = (node.text ?: node.contentDescription)?.toString()?.trim()?.lowercase()
            if (!label.isNullOrEmpty()) {
                val bounds = Rect().also(node::getBoundsInScreen)
                if (!bounds.isEmpty) found.add(Key(label, bounds))
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(::walk)
        }
        walk(root)
        return found
    }

    /**
     * The geometry once the keyboard has stopped moving: it slides in after an input restart,
     * and a read during the slide put every key 57 px too high on Dictus.
     */
    private fun readGeometry(): Pair<Geometry, List<Key>> {
        var last = readGeometryOnce()
        repeat(STABLE_TRIES) {
            SystemClock.sleep(STABLE_GAP_MS)
            val next = readGeometryOnce()
            if (next.first == last.first) return next
            last = next
        }
        error("the keyboard kept moving")
    }

    private fun readGeometryOnce(): Pair<Geometry, List<Key>> {
        val raw = labelledNodes(waitForKeyboard())
        // A letter can also be a suggestion above the keys: the key is the lowest node with that label.
        val letters = raw.filter { it.label.length == 1 && it.label[0] in 'a'..'z' }
            .groupBy { it.label }
            .mapValues { (_, nodes) -> nodes.maxBy { it.cy } }
        require(letters.size == 26) { "found ${letters.size} letter keys: ${letters.keys.sorted()}" }
        val rows = letters.values.groupBy { k -> letters.values.filter { abs(it.cy - k.cy) < 10 }.minOf { it.cy } }
            .toSortedMap().values.map { row -> row.sortedBy { it.cx } }
        require(rows.size == 3) { "letters sit on ${rows.size} rows, expected 3" }
        // Gboard's nodes are touch areas, and the one at each end of a row runs to the screen
        // edge: its centre is not where the key is drawn. The typist aims at the drawn key, one
        // pitch from its neighbour.
        val pitchX = median(rows.flatMap { row -> row.zipWithNext { a, b -> b.cx - a.cx }.drop(1).dropLast(1) })
        val aimed = rows.flatMap { row ->
            row.mapIndexed { i, k ->
                when (i) {
                    0 -> k.copy(cx = row[1].cx - pitchX)
                    row.lastIndex -> k.copy(cx = row[i - 1].cx + pitchX)
                    else -> k
                }
            }
        }.associateBy { it.label }
        val pitchY = median(rows.zipWithNext { a, b -> b.first().cy - a.first().cy })
        // The space bar: its label, or failing that the widest key below the letters.
        val lowest = rows.last().first().cy
        val space = raw.filter { it.label in SPACE_LABELS && it.cy > lowest }.maxByOrNull { it.bounds.width() }
            ?: raw.filter { it.cy > lowest + pitchY / 2 }.maxByOrNull { it.bounds.width() }
            ?: error("no space bar below the letters")
        return Pair(Geometry(aimed + ("space" to space), pitchX, pitchY), raw)
    }

    /**
     * The run is typed on the layout of its language, AZERTY for fr and QWERTY for en, as on
     * iOS. A keyboard left on the other language would replay the right taps on the wrong keys.
     * Gboard with two languages switches with its language key, which this taps until the
     * layout matches; Dictus is switched before the run (`dictus_prefs.py`).
     */
    private fun switchToLayoutOf(lang: String): Pair<Geometry, List<Key>> {
        val expected = mapOf("fr" to "az", "en" to "qw")[lang] ?: error("unknown lang $lang")
        var read = readGeometry()
        repeat(LANGUAGE_SWITCHES) {
            if (layoutStart(read.first) == expected) return read
            val key = read.second.firstOrNull { LANGUAGE_KEY.containsMatchIn(it.label) } ?: return@repeat
            val downTime = SystemClock.uptimeMillis()
            inject(MotionEvent.ACTION_DOWN, downTime, key.cx.toFloat(), key.cy.toFloat())
            SystemClock.sleep(HOLD_MS)
            inject(MotionEvent.ACTION_UP, downTime, key.cx.toFloat(), key.cy.toFloat())
            SystemClock.sleep(SETTLE_MS)
            read = readGeometry()
        }
        val start = layoutStart(read.first)
        check(start == expected) { "the keyboard's first row starts with `$start`, the $lang run needs `$expected`" }
        return read
    }

    private fun layoutStart(geometry: Geometry): String {
        val letters = geometry.keys.filterKeys { it.length == 1 }.values
        val firstRow = letters.filter { abs(it.cy - letters.minOf { k -> k.cy }) < 10 }
        return firstRow.sortedBy { it.cx }.take(2).joinToString("") { it.label }
    }

    private fun writeKeys(file: File, geometry: Geometry, raw: List<Key>) {
        val density = instrumentation.targetContext.resources.displayMetrics.density
        val rows = mutableListOf(
            "# pitch_x ${"%.2f".format(geometry.pitchX)} px, pitch_y ${"%.2f".format(geometry.pitchY)} px, density $density",
            "key\tlabel\tleft\ttop\tright\tbottom\tcx\tcy",
        )
        geometry.keys.toSortedMap().forEach { (key, k) ->
            rows.add("$key\t${k.label}\t${k.bounds.left}\t${k.bounds.top}\t${k.bounds.right}\t${k.bounds.bottom}\t" +
                "${"%.1f".format(k.cx)}\t${"%.1f".format(k.cy)}")
        }
        rows.add("# every labelled node in the keyboard window")
        raw.forEach { k ->
            rows.add("-\t${k.label.replace('\t', ' ').replace('\n', ' ')}\t${k.bounds.left}\t${k.bounds.top}\t" +
                "${k.bounds.right}\t${k.bounds.bottom}\t${"%.1f".format(k.cx)}\t${"%.1f".format(k.cy)}")
        }
        file.writeText(rows.joinToString("\n", postfix = "\n"))
    }

    // endregion

    // region Touch

    private fun inject(action: Int, downTime: Long, x: Float, y: Float) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        check(ua.injectInputEvent(event, true)) { "injection refused at $x,$y" }
        event.recycle()
    }

    /**
     * One tap, then whatever the keyboard did to the field until it settles: the first change
     * after the touch went down, then [QUIET_MS] with no further change, or [NOTHING_MS] with none.
     */
    private fun tap(activity: FieldActivity, x: Float, y: Float, before: String, seen: IntArray): Outcome {
        val downTime = SystemClock.uptimeMillis()
        val downNs = System.nanoTime()
        inject(MotionEvent.ACTION_DOWN, downTime, x, y)
        SystemClock.sleep(HOLD_MS)
        val upNs = System.nanoTime()
        inject(MotionEvent.ACTION_UP, downTime, x, y)
        var changes: List<FieldActivity.Change>
        while (true) {
            SystemClock.sleep(2)
            changes = activity.changesFrom(seen[0])
            val now = System.nanoTime()
            if (changes.isNotEmpty() && now - changes.last().atNanos >= QUIET_MS * 1_000_000) break
            if (changes.isEmpty() && now - upNs >= NOTHING_MS * 1_000_000) break
        }
        seen[0] += changes.size
        val after = changes.lastOrNull()?.text ?: before
        val first = changes.firstOrNull()?.atNanos
        return Outcome(
            resolved = classify(before, after),
            after = after,
            downToTextUs = first?.let { (it - downNs) / 1000 } ?: -1,
            upToTextUs = first?.let { (it - upNs) / 1000 } ?: -1,
        )
    }

    /** Empties the field and waits for the keyboard to come back on its letter page. */
    private fun reset(activity: FieldActivity) {
        instrumentation.runOnMainSync { activity.reset() }
        SystemClock.sleep(SETTLE_MS)
        if (imeRoot() == null) instrumentation.runOnMainSync { activity.showKeyboard() }
        waitForKeyboard()
    }

    // endregion

    // region Replay

    private fun replay(activity: FieldActivity, geometry: Geometry, lang: String, out: File) {
        val corpus = instrumentation.context.assets.open("$lang.txt").bufferedReader().readLines().filter { it.isNotBlank() }
        val run = readRun(lang)
        val only = args.getString("sentences")?.split(",")?.map { it.trim().toInt() }?.toSet()
        val taps = mutableListOf("lang\tsentence\ttap\tintended\tresolved\tx\ty\tdown_us\tup_us\tafter\tlate")
        val commits = mutableListOf("lang\tsentence\tintended\ttyped\tfinal")
        val texts = mutableListOf("lang\tsentence\tintended\tfinal")
        for ((s, sentenceTaps) in run.groupBy { it.sentence }) {
            if (only != null && s !in only) continue
            val intended = corpus[s % corpus.size].trim()
            val intendedWords = intended.split(" ")
            reset(activity)
            val keys = readGeometry().first
            if (keys != geometry) status("$lang $s: the keys are not where keys.tsv has them")
            val seen = intArrayOf(activity.changeCount())
            var text = ""
            var wordIndex = 0
            val tokenWords = mutableListOf<Int>()
            var previousDown = 0L
            var previousT = 0L
            for (tap in sentenceTaps) {
                // The run's rhythm, never faster than the keyboard settles.
                val due = previousDown + (tap.tMs - previousT)
                while (previousDown != 0L && SystemClock.uptimeMillis() < due) SystemClock.sleep(1)
                previousDown = SystemClock.uptimeMillis()
                previousT = tap.tMs
                val (x, y) = keys.point(tap.key, tap.dx, tap.dy)
                if (tap.key != "space" && tokenWords.lastOrNull() != wordIndex) tokenWords.add(wordIndex)
                // A change that landed after the previous tap settled belongs to no tap: take it
                // into the text before this one, and count it, so this tap reports only its own.
                val late = activity.changesFrom(seen[0])
                if (late.isNotEmpty()) {
                    seen[0] += late.size
                    text = late.last().text
                }
                val outcome = tap(activity, x, y, text, seen)
                if (outcome.resolved == "space") {
                    commit(text, outcome.after)?.let { (typed, final) ->
                        val meant = tokenWords.joinToString(" ") { intendedWords.getOrElse(it) { "" } }
                        commits.add("$lang\t$s\t$meant\t$typed\t$final")
                    }
                    tokenWords.clear()
                }
                if (tap.key == "space") wordIndex++
                taps.add("$lang\t$s\t${tap.index}\t${tap.key}\t${outcome.resolved}\t${"%.1f".format(x)}\t" +
                    "${"%.1f".format(y)}\t${outcome.downToTextUs}\t${outcome.upToTextUs}\t${clean(outcome.after)}\t${late.size}")
                text = outcome.after
            }
            // A last read: a keyboard may still be finishing the last commit.
            SystemClock.sleep(SETTLE_MS)
            text = activity.changesFrom(0).lastOrNull()?.text ?: text
            texts.add("$lang\t$s\t$intended\t${clean(text.trim())}")
            status("$lang $s: ${text.trim()}")
        }
        File(out, "taps.tsv").writeText(taps.joinToString("\n", postfix = "\n"))
        File(out, "commits.tsv").writeText(commits.joinToString("\n", postfix = "\n"))
        File(out, "texts.tsv").writeText(texts.joinToString("\n", postfix = "\n"))
    }

    private fun readRun(lang: String): List<Tap> =
        instrumentation.context.assets.open("$lang.tsv").bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .drop(1)
            .map { line ->
                val f = line.split("\t")
                require(f.size == 6) { "bad run row: $line" }
                Tap(f[0].toInt(), f[1].toInt(), f[2], f[3].toDouble(), f[4].toDouble(), f[5].toLong())
            }

    // endregion

    // region Probe

    /**
     * Sweeps taps across the boundaries around the keys of the top row: between neighbours along
     * the row, and down into the row below. Each line says what a tap at that offset typed.
     */
    private fun probe(activity: FieldActivity, geometry: Geometry, file: File) {
        val rows = mutableListOf("boundary\taxis\toffset_px\tx\ty\tresolved")
        val top = geometry.keys.values.filter { it.label.length == 1 }.minOf { it.cy }
        val row = geometry.keys.filterKeys { it.length == 1 }.values.filter { abs(it.cy - top) < 10 }.sortedBy { it.cx }
        val sweeps = mutableListOf<Triple<String, String, Pair<Double, Double>>>()
        row.zipWithNext { a, b -> sweeps.add(Triple("${a.label}/${b.label}", "x", Pair((a.cx + b.cx) / 2, a.cy))) }
        row.forEach { k -> sweeps.add(Triple("${k.label}/below", "y", Pair(k.cx, k.cy + geometry.pitchY / 2))) }
        val step = PROBE_STEP_PX
        val reach = (PROBE_REACH * geometry.pitchX).toInt()
        var count = 0
        var text = ""
        var seen = intArrayOf(0)
        for ((name, axis, centre) in sweeps) {
            for (offset in -reach..reach step step) {
                if (count++ % PROBE_RESET_EVERY == 0) {
                    reset(activity)
                    seen = intArrayOf(activity.changeCount())
                    text = ""
                }
                val x = (if (axis == "x") centre.first + offset else centre.first).toFloat()
                val y = (if (axis == "y") centre.second + offset else centre.second).toFloat()
                val outcome = tap(activity, x, y, text, seen)
                text = outcome.after
                rows.add("$name\t$axis\t$offset\t${"%.1f".format(x)}\t${"%.1f".format(y)}\t${outcome.resolved}")
            }
            status("probe $name")
        }
        file.writeText(rows.joinToString("\n", postfix = "\n"))
    }

    // endregion

    private fun status(message: String) {
        instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "$message\n") })
    }

    companion object {
        private val SPACE_LABELS = setOf("space", "espace", "barre d'espace", "english", "français", "french")
        private const val HOLD_MS = 40L
        private const val QUIET_MS = 40L
        private const val NOTHING_MS = 400L
        private const val SETTLE_MS = 700L
        private const val KEYBOARD_TIMEOUT_MS = 10_000L
        private val LANGUAGE_KEY = Regex("language|langue")
        private const val LANGUAGE_SWITCHES = 4
        private const val STABLE_TRIES = 20
        private const val STABLE_GAP_MS = 250L
        private const val PROBE_STEP_PX = 2
        private const val PROBE_REACH = 0.3
        private const val PROBE_RESET_EVERY = 12

        fun median(values: List<Double>): Double = values.sorted().let { it[it.size / 2] }

        fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')

        /**
         * What a tap typed, from the field before and after it. Case is ignored: auto-capitals are
         * not under test. A space that also corrected the word before it is still a space.
         */
        fun classify(before: String, after: String): String {
            val b = before.lowercase()
            val a = after.lowercase()
            if (a == b) return "none"
            if (a.length == b.length + 1 && a.startsWith(b)) return if (a.last() == ' ') "space" else a.last().toString()
            if (a.endsWith(" ") && !b.endsWith(" ") && a.count { it == ' ' } > b.count { it == ' ' }) return "space"
            return "edit"
        }

        /**
         * The word the space bar committed, as typed and as left in the field: the token after
         * the last space before the tap, and what replaced it, up to the new trailing space. A
         * split of glued words ("hour late") keeps its inner space.
         */
        fun commit(before: String, after: String): Pair<String, String>? {
            val start = before.lastIndexOf(' ') + 1
            val typed = before.substring(start)
            if (typed.isEmpty()) return null
            val prefix = before.substring(0, start)
            val final = if (after.lowercase().startsWith(prefix.lowercase())) {
                after.substring(start).trimEnd()
            } else {
                after.trimEnd().substringAfterLast(' ')
            }
            return Pair(clean(typed), clean(final))
        }
    }
}
