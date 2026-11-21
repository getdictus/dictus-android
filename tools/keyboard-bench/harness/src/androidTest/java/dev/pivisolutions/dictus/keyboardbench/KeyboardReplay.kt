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
 * - `mode`: `replay` (default), `keys` (geometry only), `probe` (taps swept across key boundaries) or
 *   `map` (taps 2 px apart along lines around the space bar, the edge keys, the top row and the
 *   bottom of the screen, #370).
 * - `sentences`: optional comma-separated sentence numbers, to replay a few.
 * - `lines`: map only, a regex: the lines whose name it matches, to map a few.
 * - `snap`: replay only, a touch-area change simulated by moving each tap, the keyboard unchanged
 *   (#370). `gaps`: a tap between key areas moves into the nearest one, as tiled touch areas would
 *   take it. `below`: a tap below the bottom row moves into the nearest bottom-row key. Both:
 *   `gaps,below`. The key areas are the keyboard's labelled nodes, or `rects`.
 * - `rects`: replay with `snap`, and map (the space bar's lines), the key areas as `left,top,right,bottom;...` screen pixels, for a
 *   keyboard whose nodes are its labels rather than its keys (Dictus).
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
                "map" -> map(activity, geometry, raw, lang, out)
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
        val rows = letters.values.groupBy { k -> letters.values.filter { abs(it.cy - k.cy) < ROW_TOLERANCE_PX }.minOf { it.cy } }
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
        val firstRow = letters.filter { abs(it.cy - letters.minOf { k -> k.cy }) < ROW_TOLERANCE_PX }
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

    /** Empties the field, or sets it to [text], and waits for the keyboard to come back on its letter page. */
    private fun reset(activity: FieldActivity, text: String = "") {
        instrumentation.runOnMainSync { activity.reset(text) }
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
        val snap = args.getString("snap")?.split(",")?.map { it.trim() }?.toSet().orEmpty()
        var snapped = 0
        for ((s, sentenceTaps) in run.groupBy { it.sentence }) {
            if (only != null && s !in only) continue
            val intended = corpus[s % corpus.size].trim()
            val intendedWords = intended.split(" ")
            reset(activity)
            val (keys, rawKeys) = readGeometry()
            val keyAreas = args.getString("rects")?.let(::parseRects) ?: keyRects(keys, rawKeys)
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
                val aimed = keys.point(tap.key, tap.dx, tap.dy)
                val (x, y) = if (snap.isEmpty()) aimed else snapPoint(aimed, keyAreas, snap)
                if ((x to y) != aimed) snapped++
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
        if (snap.isNotEmpty()) status("snap $snap: $snapped taps moved")
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

    // region Snap

    /** `left,top,right,bottom` rectangles separated by `;`: the touch areas as measured (#370). */
    private fun parseRects(text: String): List<Rect> = text.split(";").filter { it.isNotBlank() }.map { r ->
        val (l, t, rr, b) = r.split(",").map { it.trim().toInt() }
        Rect(l, t, rr, b)
    }

    /** The key nodes: every labelled node from the top letter row down, the suggestion bar excluded. */
    private fun keyRects(geometry: Geometry, raw: List<Key>): List<Rect> {
        val top = geometry.keys.values.filter { it.label.length == 1 }.minOf { it.bounds.top }
        return raw.filter { it.cy > top }.map { it.bounds }
    }

    /**
     * Where a keyboard with the [modes] touch areas would take a tap that lands in no key area: the
     * nearest point 1 px inside the nearest key area. A tap inside a key area, or above the keys,
     * stays where it is.
     */
    private fun snapPoint(p: Pair<Float, Float>, rects: List<Rect>, modes: Set<String>): Pair<Float, Float> {
        val (x, y) = p
        if (rects.any { it.contains(x.toInt(), y.toInt()) }) return p
        val top = rects.minOf { it.top }
        val bottom = rects.maxOf { it.bottom }
        val allowed = when {
            y < top -> false
            y >= bottom -> "below" in modes
            else -> "gaps" in modes
        }
        if (!allowed) return p
        val nearest = rects.minBy { r ->
            val dx = maxOf(r.left - x, 0f, x - r.right)
            val dy = maxOf(r.top - y, 0f, y - r.bottom)
            dx * dx + dy * dy
        }
        return Pair(
            x.coerceIn(nearest.left + 1f, nearest.right - 2f),
            y.coerceIn(nearest.top + 1f, nearest.bottom - 2f),
        )
    }

    // endregion

    // region Map

    /** Every third pixel of [area] on screen, to tell a tap that changed the keys from a dead one. */
    private fun pixels(area: Rect): IntArray? {
        val shot = ua.takeScreenshot() ?: return null
        val out = mutableListOf<Int>()
        for (y in area.top until minOf(area.bottom, shot.height) step 3) {
            for (x in area.left until minOf(area.right, shot.width) step 3) out.add(shot.getPixel(x, y))
        }
        shot.recycle()
        return out.toIntArray()
    }

    /** [pixels] once two captures 300 ms apart agree: the keyboard slides in after a new session. */
    private fun stablePixels(area: Rect): IntArray? {
        var last = pixels(area)
        repeat(STABLE_TRIES) {
            SystemClock.sleep(300)
            val next = pixels(area)
            if (changedShare(next, last) <= KEYS_CHANGED_SHARE) return next
            last = next
        }
        return last
    }

    private fun changedShare(a: IntArray?, b: IntArray?): Double {
        if (a == null || b == null || a.size != b.size) return 1.0
        val changed = a.indices.count { i ->
            val p = a[i]
            val q = b[i]
            maxOf(abs((p shr 16 and 255) - (q shr 16 and 255)), abs((p shr 8 and 255) - (q shr 8 and 255)), abs((p and 255) - (q and 255))) > 24
        }
        return changed.toDouble() / a.size
    }

    private fun otherWindows(): List<String> =
        ua.windows.filter { it.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD }.map { "${it.type}:${it.title}" }.sorted()

    /** Taps [MAP_STEP_PX] apart from ([x0], [y0]) to ([x1], [y1]), on a horizontal or vertical line. */
    data class Line(val name: String, val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
        fun points(): List<Pair<Int, Int>> {
            val n = maxOf(abs(x1 - x0), abs(y1 - y0)) / MAP_STEP_PX
            return (0..n).map { i ->
                Pair(x0 + Integer.signum(x1 - x0) * i * MAP_STEP_PX, y0 + Integer.signum(y1 - y0) * i * MAP_STEP_PX)
            }
        }
    }

    /**
     * Taps along lines around the zones of #370, and what each typed: a character, `space`,
     * `delete`, `enter`, `modifier` (the keys changed: shift, a page or the layout), `hidden` (the
     * keyboard went away), `system` (another window opened), `edit`
     * (any other change to the field), `none`, or `bar` for a point on a suggestion-bar element, which is never
     * tapped (the microphone is there). The field holds a word before each tap, so delete has
     * something to delete and the keyboard is not on its automatic capital.
     */
    private fun map(activity: FieldActivity, geometry: Geometry, raw: List<Key>, lang: String, out: File) {
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        activity.windowManager.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val imeWindow = Rect().also { r ->
            ua.windows.first { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }.getBoundsInScreen(r)
        }
        val letterLabels = ('a'..'z').map { it.toString() }.toSet()
        val letters = geometry.keys.filterKeys { it in letterLabels }.values
        val rows = letters.groupBy { k -> letters.filter { abs(it.cy - k.cy) < ROW_TOLERANCE_PX }.minOf { it.cy } }
            .toSortedMap().values.map { r -> r.sortedBy { it.cx } }
        val topRow = rows.first()
        val row3 = rows.last()
        val keysTop = topRow.minOf { it.bounds.top }
        val bar = raw.filter { it.cy < keysTop }
        val barBottom = bar.maxOfOrNull { it.bounds.bottom } ?: imeWindow.top
        val space = geometry.keys.getValue("space")
        val bottomRow = raw.filter { abs(it.cy - space.cy) < geometry.pitchY / 3 && it.bounds != space.bounds }
        val row3Specials = raw.filter { abs(it.cy - row3.first().cy) < ROW_TOLERANCE_PX && it.label !in letterLabels }
        val pitchX = geometry.pitchX
        val lines = mutableListOf<Line>()
        // Top row: from each key's centre up to the suggestion bar.
        topRow.forEach { k -> lines.add(Line("top:${k.label}", k.cx.toInt(), k.cy.toInt(), k.cx.toInt(), barBottom + 1)) }
        // One column through the four rows, from the bar to the screen's bottom edge.
        geometry.keys["r"]?.let { k -> lines.add(Line("column:r", k.cx.toInt(), barBottom + 1, k.cx.toInt(), height - 1)) }
        // Space bar: down from the letter row above to the screen's bottom edge, near both ends and
        // in the middle; across both ends at three heights.
        // Dictus's space node is its label: with `rects`, the drawn key holding it.
        val measured = args.getString("rects")?.let(::parseRects).orEmpty()
        val sb = measured.firstOrNull { it.contains(space.cx.toInt(), space.cy.toInt()) } ?: space.bounds
        for ((name, x) in listOf("left" to sb.left + 6, "centre" to sb.centerX(), "right" to sb.right - 7)) {
            lines.add(Line("spaceV:$name", x, row3.first().cy.toInt(), x, height - 1))
        }
        for ((name, y) in listOf("top" to sb.top + 6, "mid" to sb.centerY(), "bottom" to sb.bottom - 7)) {
            lines.add(Line("spaceH:left:$name", (sb.left - 0.6 * pitchX).toInt(), y, (sb.left + 0.4 * pitchX).toInt(), y))
            lines.add(Line("spaceH:right:$name", (sb.right + 0.6 * pitchX).toInt(), y, (sb.right - 0.4 * pitchX).toInt(), y))
        }
        // The rest of the bottom row: down from a quarter row below each key's centre, or with
        // `rects` from the drawn key's bottom edge (what is under the key, not the key: a page
        // switch on a small screen did not always come back), to the screen's bottom edge.
        bottomRow.forEach { k ->
            val y0 = measured.firstOrNull { it.contains(k.cx.toInt(), k.cy.toInt()) }?.bottom
                ?: (k.cy + 0.25 * geometry.pitchY).toInt()
            lines.add(Line("bottomV:${k.label}", k.cx.toInt(), y0, k.cx.toInt(), height - 1))
        }
        // Edge keys: from the screen edge to the key's drawn centre, at three heights. The aimed
        // centre of an end letter is one pitch from its neighbour (readGeometryOnce).
        val edges = mutableListOf<Pair<Key, Boolean>>()
        rows.take(2).forEach { r -> edges.add(r.first() to true); edges.add(r.last() to false) }
        row3Specials.minByOrNull { it.cx }?.let { edges.add(it to true) }
        row3Specials.maxByOrNull { it.cx }?.let { edges.add(it to false) }
        for ((k, left) in edges) {
            for ((name, dy) in listOf("high" to -0.3, "mid" to 0.0, "low" to 0.3)) {
                val y = (k.cy + dy * geometry.pitchY).toInt()
                lines.add(Line("edge:${k.label}:$name", if (left) 0 else width - 1, y, k.cx.toInt(), y))
            }
        }

        val rowsOut = mutableListOf(
            "# screen $width x $height px, density ${metrics.density}, ime window ${imeWindow.toShortString()}, " +
                "bar bottom $barBottom, pitch_x ${"%.2f".format(geometry.pitchX)}, pitch_y ${"%.2f".format(geometry.pitchY)}",
            "line\tx\ty\tresolved",
        )
        val prefill = "bonjour"
        var text = prefill
        var seen = intArrayOf(0)
        var actions = 0
        val keyArea = Rect(0, keysTop, width, space.bounds.bottom)
        var basePixels: IntArray? = null
        var windows: List<String> = emptyList()
        var count = 0
        var dirty = true
        // The letter page, shift off and on: the case is not under test, and the first capture can
        // be on the automatic capital.
        val letterPages = mutableListOf<IntArray?>()
        // The symbols page, for comparison: on Dictus it only changes the labels, so it is about as
        // far from the letters as the capitals are, and no fixed share tells them apart.
        val otherPages = mutableListOf<IntArray?>()
        fun onLetters(now: IntArray?): Boolean {
            val letters = letterPages.minOf { changedShare(now, it) }
            val other = otherPages.minOfOrNull { changedShare(now, it) } ?: 1.0
            return letters < other && letters <= PAGE_CHANGED_SHARE * 2
        }
        fun press(key: Key) {
            val down = SystemClock.uptimeMillis()
            inject(MotionEvent.ACTION_DOWN, down, key.cx.toFloat(), key.cy.toFloat())
            SystemClock.sleep(HOLD_MS)
            inject(MotionEvent.ACTION_UP, down, key.cx.toFloat(), key.cy.toFloat())
            SystemClock.sleep(SETTLE_MS)
        }
        // The keyboard hidden by the navigation bar's arrow does not come back on an implicit show:
        // a tap in the field brings it back, as for a user.
        fun tapField() {
            val down = SystemClock.uptimeMillis()
            val y = (imeWindow.top / 3).toFloat()
            inject(MotionEvent.ACTION_DOWN, down, width / 2f, y)
            SystemClock.sleep(HOLD_MS)
            inject(MotionEvent.ACTION_UP, down, width / 2f, y)
            SystemClock.sleep(SETTLE_MS)
        }
        var firstWindows: List<String>? = null
        // The way back to the letter page: shift off (Dictus keeps it across a new session), or, from
        // a symbols or emoji page, the bottom-left key on both keyboards.
        val backKeys = listOfNotNull(row3Specials.minByOrNull { it.cx }, bottomRow.minByOrNull { it.cx })
        val backTaps = List(6) { backKeys[it % backKeys.size] }
        fun restore() {
            // A system window opened by the navigation bar's keyboard button: closed by a tap
            // outside it, high on the screen, which does nothing when there is none. Not BACK:
            // the window list also changes for other reasons, and BACK then leaves the field.
            if (firstWindows != null && otherWindows() != firstWindows) {
                val down = SystemClock.uptimeMillis()
                val y = (imeWindow.top / 8).toFloat()
                inject(MotionEvent.ACTION_DOWN, down, width / 2f, y)
                SystemClock.sleep(HOLD_MS)
                inject(MotionEvent.ACTION_UP, down, width / 2f, y)
                SystemClock.sleep(SETTLE_MS)
            }
            instrumentation.runOnMainSync { activity.reset(prefill) }
            SystemClock.sleep(SETTLE_MS)
            if (imeRoot() == null) tapField()
            for (attempt in 0..backTaps.size) {
                val now = stablePixels(keyArea)
                if (letterPages.isEmpty() || onLetters(now)) break
                status("map: not on the letter page (attempt $attempt): letters " +
                    letterPages.joinToString { "%.3f".format(changedShare(now, it)) } +
                    ", symbols " + otherPages.joinToString { "%.3f".format(changedShare(now, it)) } +
                    ", keyboard ${if (imeRoot() == null) "hidden" else "shown"}")
                check(attempt < backTaps.size) { "the keyboard did not come back to its letter page" }
                if (imeRoot() == null) {
                    tapField()
                } else {
                    press(backTaps[attempt])
                }
            }
            switchToLayoutOf(lang)
            SystemClock.sleep(SETTLE_MS)
            seen = intArrayOf(activity.changeCount())
            actions = activity.editorActionCount()
            text = activity.changesFrom(0).lastOrNull()?.text ?: prefill
            basePixels = stablePixels(keyArea)
            if (letterPages.isEmpty()) {
                letterPages.add(basePixels)
                press(backKeys.first())
                letterPages.add(stablePixels(keyArea))
                press(backKeys.first())
                if (backKeys.size > 1) {
                    press(backKeys.last())
                    otherPages.add(stablePixels(keyArea))
                    press(backKeys.last())
                }
                basePixels = stablePixels(keyArea)
            }
            windows = otherWindows()
            if (firstWindows == null) firstWindows = windows
            dirty = false
        }
        val only = args.getString("lines")?.let(::Regex)
        val mapFile = File(out, "map.tsv")
        mapFile.writeText(rowsOut.joinToString("\n", postfix = "\n"))
        rowsOut.clear()
        for (line in lines) {
            if (only != null && !only.containsMatchIn(line.name)) continue
            for ((px, py) in line.points()) {
                if (px < 0 || px >= width || py < 0 || py >= height) continue
                if (bar.any { it.bounds.contains(px, py) }) {
                    rowsOut.add("${line.name}\t$px\t$py\tbar")
                    continue
                }
                if (dirty || count++ % MAP_RESET_EVERY == 0) restore()
                // A change that landed after the previous tap settled belongs to that tap, as in replay.
                val late = activity.changesFrom(seen[0])
                if (late.isNotEmpty()) {
                    seen[0] += late.size
                    text = late.last().text
                }
                val before = text
                val outcome = tap(activity, px.toFloat(), py.toFloat(), before, seen)
                text = outcome.after
                var resolved = outcome.resolved
                if (resolved == "edit" && outcome.after.length < before.length &&
                    before.lowercase().startsWith(outcome.after.lowercase())
                ) {
                    resolved = "delete"
                }
                if (resolved == "none") {
                    val enter = activity.editorActionCount() != actions
                    val system = !enter && otherWindows() != windows
                    val keysChanged = !enter && !system && changedShare(pixels(keyArea), basePixels) > KEYS_CHANGED_SHARE
                    // The accessibility cache can lag behind a page switch, so the keys' capture
                    // decides, and only a keyboard window gone is told apart.
                    resolved = when {
                        enter -> "enter"
                        system -> "system"
                        !keysChanged -> "none"
                        imeRoot() == null -> "hidden"
                        else -> "modifier"
                    }
                    if (resolved != "none") dirty = true
                }
                if (resolved == "edit" || resolved == "delete") dirty = dirty || text.length < 3
                // A key that wrote can change another key (Dictus's apostrophe turns into « à »
                // after an a): the next dead tap is judged against the keys as they are now.
                if (!dirty && outcome.after != before) basePixels = pixels(keyArea)
                rowsOut.add("${line.name}\t$px\t$py\t$resolved")
            }
            // Written line by line, so a run that fails keeps what it measured.
            mapFile.appendText(rowsOut.joinToString("\n", postfix = "\n"))
            rowsOut.clear()
            status("map ${line.name}")
        }
        restore()
        ua.takeScreenshot()?.let { shot ->
            File(out, "screen.png").outputStream().use { shot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
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
        val row = geometry.keys.filterKeys { it.length == 1 }.values.filter { abs(it.cy - top) < ROW_TOLERANCE_PX }.sortedBy { it.cx }
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
        /**
         * Two letters whose centres are this close sit on one row. Dictus's nodes are its labels,
         * whose centres move by a few pixels between capitals; rows are 80 px apart or more.
         */
        private const val ROW_TOLERANCE_PX = 30
        private const val PROBE_STEP_PX = 2
        private const val PROBE_REACH = 0.3
        private const val PROBE_RESET_EVERY = 12
        const val MAP_STEP_PX = 2
        private const val MAP_RESET_EVERY = 20
        private const val KEYS_CHANGED_SHARE = 0.01

        /**
         * How far the keys can be from a letter-page capture and still be the letter page (shift
         * on or off, Dictus's apostrophe turned into « à »), and nearer it than the symbols page.
         */
        private const val PAGE_CHANGED_SHARE = 0.08

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
