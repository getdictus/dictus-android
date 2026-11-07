package dev.pivisolutions.dictus.onboarding

import dev.pivisolutions.dictus.onboarding.OnboardingStep.Completion
import dev.pivisolutions.dictus.onboarding.OnboardingStep.FirstDictation
import dev.pivisolutions.dictus.onboarding.OnboardingStep.KeyboardSetup
import dev.pivisolutions.dictus.onboarding.OnboardingStep.Language
import dev.pivisolutions.dictus.onboarding.OnboardingStep.Microphone
import dev.pivisolutions.dictus.onboarding.OnboardingStep.ModelPreparation
import dev.pivisolutions.dictus.onboarding.OnboardingStep.Welcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The onboarding's order, skips, bar, back, Skip and resume rules (#196, #200).
 *
 * Ported from iOS `DictusCore/Tests/DictusCoreTests/OnboardingStepTests.swift`. The iOS cases
 * about steps Android does not have (Pro scenes, stop-standby, the legacy page index) wait
 * for the sub-issues that add them.
 */
class OnboardingStepTest {

    // --- The order ---

    @Test
    fun `the order is iOS's, the microphone right before the first dictation`() {
        assertEquals(
            listOf(Welcome, Language, KeyboardSetup, ModelPreparation, Microphone, FirstDictation, Completion),
            OnboardingStep.entries,
        )
    }

    @Test
    fun `next walks the flow and ends after the completion`() {
        assertEquals(Language, Welcome.next())
        assertEquals(KeyboardSetup, Language.next())
        assertEquals(ModelPreparation, KeyboardSetup.next())
        assertEquals(Microphone, ModelPreparation.next())
        assertEquals(FirstDictation, Microphone.next())
        assertEquals(Completion, FirstDictation.next())
        assertNull(Completion.next())
    }

    // --- Skips (#200) ---

    @Test
    fun `a ready model passes over the model wait`() {
        assertEquals(Microphone, KeyboardSetup.next(skipping = setOf(ModelPreparation)))
    }

    @Test
    fun `a granted microphone passes over the microphone step`() {
        assertEquals(FirstDictation, ModelPreparation.next(skipping = setOf(Microphone)))
    }

    @Test
    fun `a ready model and a granted microphone go from the keyboard to the first dictation`() {
        assertEquals(FirstDictation, KeyboardSetup.next(skipping = setOf(ModelPreparation, Microphone)))
    }

    @Test
    fun `only the model wait and the microphone can be passed over`() {
        assertEquals(
            listOf(ModelPreparation, Microphone),
            OnboardingStep.entries.filter { it.isSkippedWhenSatisfied },
        )
    }

    @Test
    fun `a step that cannot be passed over is shown even when listed as satisfied`() {
        val everything = OnboardingStep.entries.toSet()
        assertEquals(KeyboardSetup, Language.next(skipping = everything))
        assertEquals(FirstDictation, KeyboardSetup.next(skipping = everything))
        assertEquals(Completion, FirstDictation.next(skipping = everything))
    }

    // --- Shell: progress bar ---

    @Test
    fun `the progress bar covers every step between the intro and the completion`() {
        assertEquals(
            listOf(Language, KeyboardSetup, ModelPreparation, Microphone, FirstDictation),
            OnboardingStep.progressSteps,
        )
        assertFalse(Welcome.showsProgress)
        assertFalse(Completion.showsProgress)
        assertNull(Welcome.progressIndex)
        assertNull(Completion.progressIndex)
    }

    @Test
    fun `each step with the bar fills its own segment`() {
        assertEquals(0, Language.progressIndex)
        assertEquals(1, KeyboardSetup.progressIndex)
        assertEquals(2, ModelPreparation.progressIndex)
        assertEquals(3, Microphone.progressIndex)
        assertEquals(4, FirstDictation.progressIndex)
    }

    @Test
    fun `a passed-over step keeps its segment, so the bar does not change length`() {
        // As on iOS: the bar would otherwise depend on how fast the download was. The first
        // dictation reached straight from the keyboard still fills the last segment.
        assertEquals(5, OnboardingStep.progressSteps.size)
        assertEquals(OnboardingStep.progressSteps.last(), KeyboardSetup.next(skipping = setOf(ModelPreparation, Microphone)))
    }

    // --- Shell: Skip ---

    @Test
    fun `the microphone and the first dictation offer Skip`() {
        assertEquals(listOf(Microphone, FirstDictation), OnboardingStep.entries.filter { it.isSkippable })
    }

    @Test
    fun `declining the microphone passes over the first dictation`() {
        // #220: the first dictation cannot record without the microphone.
        assertEquals(Completion, Microphone.afterSkip())
        assertEquals(Completion, FirstDictation.afterSkip())
    }

    // --- Shell: going back ---

    @Test
    fun `the steps that go back and where to`() {
        val backs = OnboardingStep.entries.mapNotNull { step -> step.stepBack?.let { step to it } }.toMap()
        assertEquals(mapOf(Language to Welcome), backs)
    }

    @Test
    fun `no step goes back after the language step`() {
        // As on iOS: confirming the language starts the model download, and going back
        // past it would cancel or duplicate that download.
        for (step in OnboardingStep.entries.filter { it.ordinal > Language.ordinal }) {
            assertNull("$step", step.stepBack)
        }
    }

    @Test
    fun `going back returns to the step forward navigation came from, whatever was passed over`() {
        val skipSets = listOf(emptySet(), setOf(ModelPreparation), setOf(Microphone), setOf(ModelPreparation, Microphone))
        for (step in OnboardingStep.entries) {
            val back = step.stepBack ?: continue
            for (satisfied in skipSets) assertEquals("$step $satisfied", step, back.next(satisfied))
        }
    }

    // --- Persistence ---

    @Test
    fun `a fresh install starts at the welcome screen`() {
        assertEquals(Welcome, OnboardingStep.resumed(null))
    }

    @Test
    fun `every step but the microphone resumes at itself`() {
        for (step in OnboardingStep.entries - Microphone) {
            assertEquals("$step", step, OnboardingStep.resumed(step.storedName))
        }
    }

    @Test
    fun `a stored microphone resumes at the keyboard setup`() {
        // #200: "microphone" was written by the order before #200, where the microphone came
        // right after the language step and before the keyboard. Resuming it as the
        // microphone step would pass over the keyboard setup and the model wait. The name is
        // kept, so a run of the new order stopped on the microphone also lands here: the
        // keyboard step reads « Continuer », and the steps after it apply their skips.
        assertEquals(KeyboardSetup, OnboardingStep.resumed("microphone"))
    }

    @Test
    fun `the steps of the order before #200 resume at the nearest step of the new one`() {
        // Old order: welcome, language, microphone, keyboardSetup, modelPreparation,
        // firstDictation, completion. Everything done before a stored step is still done
        // before it in the new order, the microphone apart, whose step is passed over once
        // granted.
        val resumedFrom = listOf(
            "welcome", "language", "microphone", "keyboardSetup", "modelPreparation", "firstDictation", "completion",
        ).associateWith { OnboardingStep.resumed(it) }
        assertEquals(
            mapOf(
                "welcome" to Welcome,
                "language" to Language,
                "microphone" to KeyboardSetup,
                "keyboardSetup" to KeyboardSetup,
                "modelPreparation" to ModelPreparation,
                "firstDictation" to FirstDictation,
                "completion" to Completion,
            ),
            resumedFrom,
        )
    }

    @Test
    fun `an unknown stored name starts over`() {
        assertEquals(Welcome, OnboardingStep.resumed("someFutureStep"))
        assertEquals(Welcome, OnboardingStep.resumed(""))
    }

    @Test
    fun `the removed ABC 123 step resumes at the step that followed it`() {
        // #270: the step is gone, as on iOS (#213). An install stopped on it had set up the
        // keyboard and not started the model, so it resumes at the model preparation.
        assertEquals(ModelPreparation, OnboardingStep.resumed("keyboardLayout"))
    }

    @Test
    fun `the stored names are stable`() {
        // A renamed step would send every install mid-onboarding back to the welcome screen.
        assertEquals(
            listOf(
                "welcome", "language", "keyboardSetup", "modelPreparation", "microphone",
                "firstDictation", "completion",
            ),
            OnboardingStep.entries.map { it.storedName },
        )
    }

    @Test
    fun `stored names are unique`() {
        val names = OnboardingStep.entries.map { it.storedName }
        assertTrue(names.size == names.toSet().size)
    }
}
