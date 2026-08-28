package dev.pivisolutions.dictus.ime.input

import android.text.InputType
import android.view.inputmethod.EditorInfo
import dev.pivisolutions.dictus.core.service.TranscriptionRetention
import org.junit.Assert.assertEquals
import org.junit.Test

class ImeTranscriptionRetentionSessionTest {
    @Test
    fun `ordinary learning-enabled editor begins persistent session`() {
        val session = ImeTranscriptionRetentionSession()

        session.begin(editorEligible = true, personalizedLearningAllowed = true)

        assertEquals(TranscriptionRetention.PERSIST_HISTORY, session.consume())
    }

    @Test
    fun `private and no-learning editors begin ephemeral sessions`() {
        listOf(
            false to false,
            true to false,
            false to true,
        ).forEach { (eligible, learning) ->
            val session = ImeTranscriptionRetentionSession()
            session.begin(eligible, learning)
            assertEquals(TranscriptionRetention.EPHEMERAL, session.consume())
        }
    }

    @Test
    fun `password no-suggestions and no-learning editor metadata stay ephemeral`() {
        val cases = listOf(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD to 0,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD to 0,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD to 0,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS to 0,
            InputType.TYPE_CLASS_TEXT to EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING,
        )

        cases.forEach { (inputType, imeOptions) ->
            val policy = EditorEligibilityPolicy.resolve(inputType, imeOptions)
            assertEquals(
                TranscriptionRetention.EPHEMERAL,
                ImeTranscriptionRetentionSession.resolve(
                    policy.suggestionEligible,
                    policy.personalizedLearningAllowed,
                ),
            )
        }
    }

    @Test
    fun `moving from private to ordinary editor never upgrades captured audio`() {
        val session = ImeTranscriptionRetentionSession()
        session.begin(editorEligible = false, personalizedLearningAllowed = false)

        session.restrict(editorEligible = true, personalizedLearningAllowed = true)

        assertEquals(TranscriptionRetention.EPHEMERAL, session.consume())
    }

    @Test
    fun `moving from ordinary to private editor downgrades captured audio`() {
        val session = ImeTranscriptionRetentionSession()
        session.begin(editorEligible = true, personalizedLearningAllowed = true)

        session.restrict(editorEligible = false, personalizedLearningAllowed = false)

        assertEquals(TranscriptionRetention.EPHEMERAL, session.consume())
    }

    @Test
    fun `consume and cancel reset fail closed`() {
        val session = ImeTranscriptionRetentionSession()
        session.begin(editorEligible = true, personalizedLearningAllowed = true)
        assertEquals(TranscriptionRetention.PERSIST_HISTORY, session.consume())
        assertEquals(TranscriptionRetention.EPHEMERAL, session.consume())

        session.begin(editorEligible = true, personalizedLearningAllowed = true)
        session.reset()
        assertEquals(TranscriptionRetention.EPHEMERAL, session.consume())
    }
}
