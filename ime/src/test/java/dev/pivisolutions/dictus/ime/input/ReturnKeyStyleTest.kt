package dev.pivisolutions.dictus.ime.input

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import dev.pivisolutions.dictus.ime.language.SupportedLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.lang.reflect.Proxy

/** The return key follows the field's action, as iOS ReturnKeyStyleTests (dictus-ios #1025). */
@RunWith(RobolectricTestRunner::class)
class ReturnKeyStyleTest {

    @Test
    fun everyActionMapsToItsRoleAndIsPerformed() {
        val expected = mapOf(
            EditorInfo.IME_ACTION_SEND to ReturnKeyRole.SEND,
            EditorInfo.IME_ACTION_GO to ReturnKeyRole.GO,
            EditorInfo.IME_ACTION_SEARCH to ReturnKeyRole.SEARCH,
            EditorInfo.IME_ACTION_DONE to ReturnKeyRole.DONE,
            EditorInfo.IME_ACTION_NEXT to ReturnKeyRole.NEXT,
        )
        expected.forEach { (action, role) ->
            assertEquals("action $action", ReturnKeyStyle(role, action), ReturnKeyStyle.forEditor(action))
        }
    }

    @Test
    fun noActionIsThePlainReturnKeySendingEnter() {
        assertEquals(ReturnKeyStyle.NEWLINE, ReturnKeyStyle.forEditor(EditorInfo.IME_ACTION_UNSPECIFIED))
        assertEquals(ReturnKeyStyle.NEWLINE, ReturnKeyStyle.forEditor(EditorInfo.IME_ACTION_NONE))
        assertNull(ReturnKeyStyle.NEWLINE.editorAction)
    }

    /** A multi-line field: TextView and Compose add the flag to whatever action is declared. */
    @Test
    fun noEnterActionFlagKeepsANewLine() {
        val multiLineSend = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION
        assertEquals(ReturnKeyStyle.NEWLINE, ReturnKeyStyle.forEditor(multiLineSend))
    }

    @Test
    fun otherFlagsDoNotHideTheAction() {
        val search = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        assertEquals(ReturnKeyRole.SEARCH, ReturnKeyStyle.forEditor(search).role)
    }

    /** iOS has no glyph for previous: plain key, but the host's action is still performed. */
    @Test
    fun anActionWithoutAGlyphDrawsThePlainKeyAndStillActs() {
        val previous = ReturnKeyStyle.forEditor(EditorInfo.IME_ACTION_PREVIOUS)
        assertEquals(ReturnKeyRole.NEWLINE, previous.role)
        assertEquals(EditorInfo.IME_ACTION_PREVIOUS, previous.editorAction)
    }

    @Test
    fun onlyTheActingRolesAreBlue() {
        val blue = setOf(ReturnKeyRole.SEND, ReturnKeyRole.GO, ReturnKeyRole.SEARCH, ReturnKeyRole.DONE)
        ReturnKeyRole.entries.forEach { role -> assertEquals(role.name, role in blue, role.isAction) }
    }

    @Test
    fun frenchLabels() {
        assertEquals("retour", label(ReturnKeyRole.NEWLINE, SupportedLanguage.FRENCH))
        assertEquals("envoyer", label(ReturnKeyRole.SEND, SupportedLanguage.FRENCH))
        assertEquals("accéder", label(ReturnKeyRole.GO, SupportedLanguage.FRENCH))
        assertEquals("rechercher", label(ReturnKeyRole.SEARCH, SupportedLanguage.FRENCH))
        assertEquals("suivant", label(ReturnKeyRole.NEXT, SupportedLanguage.FRENCH))
        assertEquals("OK", label(ReturnKeyRole.DONE, SupportedLanguage.FRENCH))
    }

    @Test
    fun englishLabels() {
        assertEquals("return", label(ReturnKeyRole.NEWLINE, SupportedLanguage.ENGLISH))
        assertEquals("send", label(ReturnKeyRole.SEND, SupportedLanguage.ENGLISH))
        assertEquals("go", label(ReturnKeyRole.GO, SupportedLanguage.ENGLISH))
        assertEquals("search", label(ReturnKeyRole.SEARCH, SupportedLanguage.ENGLISH))
        assertEquals("next", label(ReturnKeyRole.NEXT, SupportedLanguage.ENGLISH))
        assertEquals("done", label(ReturnKeyRole.DONE, SupportedLanguage.ENGLISH))
    }

    @Test
    fun spanishAndGermanLabels() {
        assertEquals("intro", label(ReturnKeyRole.NEWLINE, SupportedLanguage.SPANISH))
        assertEquals("enviar", label(ReturnKeyRole.SEND, SupportedLanguage.SPANISH))
        assertEquals("buscar", label(ReturnKeyRole.SEARCH, SupportedLanguage.SPANISH))
        assertEquals("Eingabe", label(ReturnKeyRole.NEWLINE, SupportedLanguage.GERMAN))
        assertEquals("Senden", label(ReturnKeyRole.SEND, SupportedLanguage.GERMAN))
        assertEquals("Suchen", label(ReturnKeyRole.SEARCH, SupportedLanguage.GERMAN))
    }

    /** Every role has its own word in every language: none falls back to the plain key's. */
    @Test
    fun everyRoleHasALabelInEveryLanguage() {
        SupportedLanguage.entries.forEach { language ->
            ReturnKeyRole.entries.forEach { role ->
                val label = label(role, language)
                assertFalse("$language $role", label.isEmpty())
                if (role != ReturnKeyRole.NEWLINE) {
                    assertNotEquals("$language $role fell back", language.profile.returnLabel, label)
                }
            }
        }
    }

    private fun label(role: ReturnKeyRole, language: SupportedLanguage) =
        ReturnKeyStyle(role, editorAction = null).label(language)
    /** A search, go or send field gets its action, not an Enter key event it may ignore. */
    @Test
    fun aTapOnAnActionKeyPerformsTheFieldsAction() {
        listOf(
            EditorInfo.IME_ACTION_SEND,
            EditorInfo.IME_ACTION_GO,
            EditorInfo.IME_ACTION_SEARCH,
            EditorInfo.IME_ACTION_DONE,
            EditorInfo.IME_ACTION_NEXT,
        ).forEach { action ->
            val calls = mutableListOf<String>()
            ReturnKeyStyle.forEditor(action).sendTo(recordingConnection(calls))
            assertEquals("action $action", listOf("performEditorAction $action"), calls)
        }
    }

    @Test
    fun aTapOnThePlainKeySendsEnter() {
        val calls = mutableListOf<String>()
        ReturnKeyStyle.forEditor(EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION)
            .sendTo(recordingConnection(calls))
        assertEquals(
            listOf("sendKeyEvent ${KeyEvent.ACTION_DOWN} ${KeyEvent.KEYCODE_ENTER}", "sendKeyEvent ${KeyEvent.ACTION_UP} ${KeyEvent.KEYCODE_ENTER}"),
            calls,
        )
    }

    private fun recordingConnection(calls: MutableList<String>): InputConnection = Proxy.newProxyInstance(
        InputConnection::class.java.classLoader,
        arrayOf(InputConnection::class.java),
    ) { _, method, args ->
        when (method.name) {
            "performEditorAction" -> calls += "performEditorAction ${args[0]}"
            "sendKeyEvent" -> (args[0] as KeyEvent).let { calls += "sendKeyEvent ${it.action} ${it.keyCode}" }
        }
        if (method.returnType == Boolean::class.javaPrimitiveType) true else null
    } as InputConnection
}
