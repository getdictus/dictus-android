package dev.pivisolutions.dictus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImeComponentMatchTest {

    @Test
    fun `matches an exact Dictus package component`() {
        assertTrue(
            isImeComponentForPackage(
                flattenedComponent = "dev.pivisolutions.dictus/dev.pivisolutions.dictus.ime.DictusImeService",
                expectedPackage = "dev.pivisolutions.dictus",
            ),
        )
    }

    @Test
    fun `rejects a sibling package sharing the Dictus prefix`() {
        assertFalse(
            isImeComponentForPackage(
                flattenedComponent = "dev.pivisolutions.dictus.fake/.FakeImeService",
                expectedPackage = "dev.pivisolutions.dictus",
            ),
        )
    }

    @Test
    fun `rejects null and malformed component values`() {
        assertFalse(isImeComponentForPackage(null, "dev.pivisolutions.dictus"))
        assertFalse(isImeComponentForPackage("dev.pivisolutions.dictus", "dev.pivisolutions.dictus"))
    }
}
