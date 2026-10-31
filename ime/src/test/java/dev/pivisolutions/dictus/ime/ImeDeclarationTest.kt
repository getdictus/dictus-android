package dev.pivisolutions.dictus.ime

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Pins how Dictus appears in the system keyboard picker (#195): one entry named « Dictus », as
 * on iOS (`DictusKeyboard/Info.plist`: `CFBundleDisplayName` Dictus, `PrimaryLanguage` mul).
 *
 * The layout and the keyboard language are per-language preferences inside Dictus (#134), not
 * system subtypes. A plain JVM test: it reads the declaration and the sources from the tree.
 */
class ImeDeclarationTest {

    @Test
    fun `the input method declares no subtype`() {
        val subtypes = methodXml().getElementsByTagName("subtype").length

        assertEquals("Subtypes declared in ime/src/main/res/xml/method.xml", 0, subtypes)
    }

    @Test
    fun `the picker entry is named Dictus in every locale`() {
        val labels = imeNameFiles().associate { it.relativeTo(repoRoot).path to imeName(it) }

        assertEquals(labels.keys.associateWith { "Dictus" }, labels)
    }

    @Test
    fun `no code reads the system subtype`() {
        val readers = kotlinSources().filter { file ->
            SUBTYPE_APIS.any { api -> file.readText().contains(api) }
        }.map { it.relativeTo(repoRoot).path }

        assertEquals(emptyList<String>(), readers)
    }

    private val repoRoot: File by lazy {
        generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
    }

    private fun methodXml() = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(File(repoRoot, "ime/src/main/res/xml/method.xml"))

    private fun imeNameFiles(): List<File> =
        File(repoRoot, "core/src/main/res").listFiles { dir -> dir.name.startsWith("values") }.orEmpty()
            .map { File(it, "strings.xml") }
            .filter { it.isFile && imeName(it) != null }

    private fun imeName(file: File): String? {
        val strings = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            .getElementsByTagName("string")
        return (0 until strings.length).map { strings.item(it) as Element }
            .firstOrNull { it.getAttribute("name") == "ime_name" }?.textContent
    }

    private fun kotlinSources(): List<File> =
        repoRoot.listFiles().orEmpty()
            .map { File(it, "src/main") }
            .filter(File::isDirectory)
            .flatMap { dir -> dir.walk().filter { it.extension == "kt" }.toList() }

    private companion object {
        val SUBTYPE_APIS = listOf("InputMethodSubtype", "getCurrentInputMethodSubtype", "onCurrentInputMethodSubtypeChanged")
    }
}
