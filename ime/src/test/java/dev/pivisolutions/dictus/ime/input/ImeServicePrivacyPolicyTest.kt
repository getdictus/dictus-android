package dev.pivisolutions.dictus.ime.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeServicePrivacyPolicyTest {
    @Test
    fun `ineligible editor has zero context read intent`() {
        var reads = 0

        val result = ImeServicePrivacyPolicy.readEditorContextIfEligible(
            editorEligible = false,
            fallback = "default",
        ) {
            reads++
            "private editor context"
        }

        assertEquals("default", result)
        assertEquals(0, reads)
    }

    @Test
    fun `eligible editor performs the requested context read`() {
        var reads = 0

        val result = ImeServicePrivacyPolicy.readEditorContextIfEligible(true, "default") {
            reads++
            "context-derived"
        }

        assertEquals("context-derived", result)
        assertEquals(1, reads)
    }

    @Test
    fun `no personalized learning gates every service learning entry point`() {
        PersonalizedLearningEntryPoint.entries.forEach { entryPoint ->
            var mutations = 0

            val ran = ImeServicePrivacyPolicy.runPersonalizedLearningIfAllowed(
                personalizedLearningAllowed = false,
                entryPoint = entryPoint,
            ) { mutations++ }

            assertFalse(entryPoint.name, ran)
            assertEquals(entryPoint.name, 0, mutations)
        }
    }

    @Test
    fun `allowed personalized learning reaches every explicit service entry point`() {
        PersonalizedLearningEntryPoint.entries.forEach { entryPoint ->
            var mutations = 0

            val ran = ImeServicePrivacyPolicy.runPersonalizedLearningIfAllowed(
                personalizedLearningAllowed = true,
                entryPoint = entryPoint,
            ) { mutations++ }

            assertTrue(entryPoint.name, ran)
            assertEquals(entryPoint.name, 1, mutations)
        }
    }
}