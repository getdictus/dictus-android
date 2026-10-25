package dev.pivisolutions.dictus.onboarding

import dev.pivisolutions.dictus.onboarding.OnboardingStep.Completion
import dev.pivisolutions.dictus.onboarding.OnboardingStep.FirstDictation
import dev.pivisolutions.dictus.onboarding.OnboardingStep.KeyboardLayout
import dev.pivisolutions.dictus.onboarding.OnboardingStep.KeyboardSetup
import dev.pivisolutions.dictus.onboarding.OnboardingStep.Microphone
import dev.pivisolutions.dictus.onboarding.OnboardingStep.ModelPreparation
import dev.pivisolutions.dictus.onboarding.OnboardingStep.Welcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The onboarding's order, bar, back, Skip and resume rules (#196).
 *
 * Ported from iOS `DictusCore/Tests/DictusCoreTests/OnboardingStepTests.swift`, on Android's
 * current order. The iOS cases about steps Android does not have yet (language, Pro
 * scenes, stop-standby, the legacy page index) wait for the sub-issues that add them.
 */
class OnboardingStepTest {

    // --- The order ---

    @Test
    fun `the order is Android's current flow`() {
        assertEquals(
            listOf(Welcome, Microphone, KeyboardSetup, KeyboardLayout, ModelPreparation, FirstDictation, Completion),
            OnboardingStep.entries,
        )
    }

    @Test
    fun `next walks the flow and ends after the completion`() {
        assertEquals(Microphone, Welcome.next)
        assertEquals(KeyboardSetup, Microphone.next)
        assertEquals(KeyboardLayout, KeyboardSetup.next)
        assertEquals(ModelPreparation, KeyboardLayout.next)
        assertEquals(FirstDictation, ModelPreparation.next)
        assertEquals(Completion, FirstDictation.next)
        assertNull(Completion.next)
    }

    // --- Shell: progress bar ---

    @Test
    fun `the progress bar covers every step between the intro and the completion`() {
        assertEquals(
            listOf(Microphone, KeyboardSetup, KeyboardLayout, ModelPreparation, FirstDictation),
            OnboardingStep.progressSteps,
        )
        assertFalse(Welcome.showsProgress)
        assertFalse(Completion.showsProgress)
        assertNull(Welcome.progressIndex)
        assertNull(Completion.progressIndex)
    }

    @Test
    fun `each step with the bar fills its own segment`() {
        assertEquals(0, Microphone.progressIndex)
        assertEquals(1, KeyboardSetup.progressIndex)
        assertEquals(2, KeyboardLayout.progressIndex)
        assertEquals(3, ModelPreparation.progressIndex)
        assertEquals(4, FirstDictation.progressIndex)
    }

    // --- Shell: Skip ---

    @Test
    fun `only the first dictation offers Skip`() {
        assertEquals(listOf(FirstDictation), OnboardingStep.entries.filter { it.isSkippable })
    }

    // --- Shell: going back ---

    @Test
    fun `the steps that go back and where to`() {
        val backs = OnboardingStep.entries.mapNotNull { step -> step.stepBack?.let { step to it } }.toMap()
        assertEquals(mapOf(Microphone to Welcome), backs)
    }

    @Test
    fun `no step goes back from the keyboard setup on`() {
        // As on iOS: once #200 moves this flow to iOS's order, the keyboard setup follows
        // the step that starts the model download, and going back would cancel or
        // duplicate it.
        for (step in OnboardingStep.entries.filter { it.ordinal >= KeyboardSetup.ordinal }) {
            assertNull("$step", step.stepBack)
        }
    }

    @Test
    fun `going back returns to the step forward navigation came from`() {
        for (step in OnboardingStep.entries) {
            val back = step.stepBack ?: continue
            assertEquals("$step", step, back.next)
        }
    }

    // --- Persistence ---

    @Test
    fun `a fresh install starts at the welcome screen`() {
        assertEquals(Welcome, OnboardingStep.resumed(null))
    }

    @Test
    fun `every step resumes at itself`() {
        for (step in OnboardingStep.entries) {
            assertEquals("$step", step, OnboardingStep.resumed(step.storedName))
        }
    }

    @Test
    fun `an unknown stored name starts over`() {
        assertEquals(Welcome, OnboardingStep.resumed("someFutureStep"))
        assertEquals(Welcome, OnboardingStep.resumed(""))
    }

    @Test
    fun `the stored names are stable`() {
        // A renamed step would send every install mid-onboarding back to the welcome screen.
        assertEquals(
            listOf(
                "welcome", "microphone", "keyboardSetup", "keyboardLayout", "modelPreparation",
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
