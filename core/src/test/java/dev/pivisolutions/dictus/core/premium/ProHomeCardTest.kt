package dev.pivisolutions.dictus.core.premium

import org.junit.Assert.assertEquals
import org.junit.Test

/** The Home Pro card rule (#207), iOS `ProHomeCard.content`. */
class ProHomeCardTest {

    @Test
    fun `a hidden paywall hides the card, Pro or not`() {
        assertEquals(ProHomeCardContent.Hidden, ProHomeCard.content(paywallVisible = false, isPro = true, activeFeatures = 1))
        assertEquals(ProHomeCardContent.Hidden, ProHomeCard.content(paywallVisible = false, isPro = false, activeFeatures = 1))
    }

    @Test
    fun `with the paywall visible a Pro user gets the member card with the count`() {
        assertEquals(ProHomeCardContent.Member(1), ProHomeCard.content(paywallVisible = true, isPro = true, activeFeatures = 1))
        assertEquals(ProHomeCardContent.Member(0), ProHomeCard.content(paywallVisible = true, isPro = true, activeFeatures = 0))
    }

    @Test
    fun `a user without Pro gets no card until the paywall exists`() {
        assertEquals(ProHomeCardContent.Hidden, ProHomeCard.content(paywallVisible = true, isPro = false, activeFeatures = 1))
    }

    @Test
    fun `Android ships with the paywall hidden`() {
        assertEquals(false, PremiumFlags.PAYWALL_VISIBLE)
    }
}
