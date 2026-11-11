package dev.pivisolutions.dictus.ime.input

/**
 * Whether a run of text with no whitespace is typed as an email address or a URL (#332, iOS #919).
 *
 * Its words are never autocorrected, whatever the field: `helene.bethmont@gmail.com` must not
 * become `Hélène.bethmont@Gmail.com`. A full stop at the end of a sentence (`Bonjour.`, `etc.`,
 * `M.`) is not followed by anything glued to it, so it is no address.
 */
object AddressLikeToken {
    private val separators = setOf('.', ':', '/', '_')
    private val gluedMarks = setOf('/', '_', '@')

    fun matches(token: String): Boolean {
        if ('@' in token || "://" in token || token.startsWith("www.", ignoreCase = true)) return true
        // `prenom.nom`, `john_doe`, `https:/`: a separator with a letter, a digit or `/ _ @` glued
        // after it. Another mark after it (`Bonjour...`, `etc.,`) is still punctuation.
        return (0 until token.length - 1).any { index ->
            token[index] in separators && token[index + 1].let { it.isLetterOrDigit() || it in gluedMarks }
        }
    }
}
