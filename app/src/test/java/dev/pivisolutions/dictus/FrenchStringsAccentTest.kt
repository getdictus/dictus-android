package dev.pivisolutions.dictus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Fails when a French string resource, in any module, spells a common word without its accent
 * ("GENERAL" instead of "GÉNÉRAL", "A PROPOS" instead of "À PROPOS"; #185).
 *
 * A plain JVM test: it reads the `values-fr` XML files from the source tree, so a new module or a
 * new file is covered without being listed here.
 */
class FrenchStringsAccentTest {

    @Test
    fun `French strings carry their accents`() {
        val offenders = frenchResourceFiles().flatMap { file ->
            frenchTexts(file).mapNotNull { (name, text) ->
                unaccentedWord(text)?.let { "${file.relativeTo(repoRoot)}: $name = \"$text\" (\"$it\")" }
            }
        }

        assertEquals("Unaccented French words:\n" + offenders.joinToString("\n"), emptyList<String>(), offenders)
    }

    @Test
    fun `every module's French resources are scanned`() {
        val modules = frenchResourceFiles().map { it.relativeTo(repoRoot).path.substringBefore(File.separator) }.toSet()

        assertTrue("Scanned modules: $modules", modules.containsAll(listOf("app", "core", "ime")))
    }

    @Test
    fun `the denylist matches whole words regardless of case`() {
        assertEquals("a propos", unaccentedWord("A PROPOS"))
        assertEquals("general", unaccentedWord("Réglages généraux, General"))
        assertEquals(null, unaccentedWord("À PROPOS"))
        assertEquals(null, unaccentedWord("GÉNÉRAL"))
        // Whole words only: "generalement" is not "general".
        assertEquals(null, unaccentedWord("generalement"))
    }

    private val repoRoot: File by lazy {
        generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
    }

    private fun frenchResourceFiles(): List<File> =
        repoRoot.listFiles().orEmpty()
            .map { File(it, "src/main/res") }
            .filter(File::isDirectory)
            .flatMap { res -> res.listFiles { dir -> dir.name.startsWith("values-fr") }.orEmpty().toList() }
            .flatMap { dir -> dir.listFiles { file -> file.extension == "xml" }.orEmpty().toList() }

    /** Every `<string>`, plural `<item>` and string-array `<item>`, keyed by resource name. */
    private fun frenchTexts(file: File): List<Pair<String, String>> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val children = root.childNodes
        return (0 until children.length).map(children::item).filterIsInstance<Element>().flatMap { element ->
            val name = element.getAttribute("name")
            when (element.tagName) {
                "string" -> listOf(name to decode(element.textContent))
                "plurals", "string-array" -> {
                    val items = element.getElementsByTagName("item")
                    (0 until items.length).map { name to decode(items.item(it).textContent) }
                }
                else -> emptyList()
            }
        }
    }

    /** Resolves the escapes aapt resolves, so "GÉNÉRAL" is read as "GÉNÉRAL". */
    private fun decode(raw: String): String =
        Regex("""\\u([0-9a-fA-F]{4})""").replace(raw) { it.groupValues[1].toInt(16).toChar().toString() }
            .replace("\\'", "'")
            .replace("\\\"", "\"")

    private fun unaccentedWord(text: String): String? =
        UNACCENTED_WORDS.firstOrNull { word ->
            Regex("""(?<!\p{L})${Regex.escape(word)}(?!\p{L})""", RegexOption.IGNORE_CASE).containsMatchIn(text)
        }

    private companion object {
        // Words whose accent-less spelling never appears in correct French copy.
        val UNACCENTED_WORDS = listOf(
            "a propos", "general", "generale", "generaux",
            "parametre", "parametres", "reglage", "reglages",
            "telecharger", "telechargement", "telecharge", "telecharges",
            "modele", "modeles", "systeme", "donnees", "securite",
            "selectionner", "selectionne", "reessayer", "arreter", "ecoute",
            "dictee", "debut", "theme", "precision", "echec", "demarrer",
            "francais", "evenement", "resultat", "derniere", "memoire",
        )
    }
}
