package dev.pivisolutions.dictus.core.whisper

import org.junit.Assert.assertEquals
import org.junit.Test

class TextPostProcessorTest {

    @Test
    fun `empty input returns empty string`() {
        assertEquals("", TextPostProcessor.process(""))
    }

    @Test
    fun `whitespace-only input returns empty string`() {
        assertEquals("", TextPostProcessor.process("   "))
    }

    @Test
    fun `trims whitespace and appends period-space when no trailing punctuation`() {
        assertEquals("hello world. ", TextPostProcessor.process("  hello world  "))
    }

    @Test
    fun `text without punctuation gets period-space appended`() {
        assertEquals("Bonjour. ", TextPostProcessor.process("Bonjour"))
    }

    @Test
    fun `text ending with period gets space appended`() {
        assertEquals("Bonjour. ", TextPostProcessor.process("Bonjour."))
    }

    @Test
    fun `text ending with question mark gets space appended`() {
        assertEquals("Vraiment? ", TextPostProcessor.process("Vraiment?"))
    }

    @Test
    fun `text ending with exclamation gets space appended`() {
        assertEquals("Super! ", TextPostProcessor.process("Super!"))
    }

    @Test
    fun `text ending with ellipsis gets space appended`() {
        assertEquals("Etc... ", TextPostProcessor.process("Etc..."))
    }
}
