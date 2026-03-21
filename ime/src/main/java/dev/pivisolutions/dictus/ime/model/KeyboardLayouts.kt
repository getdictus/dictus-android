package dev.pivisolutions.dictus.ime.model

/**
 * Complete keyboard layout data for all layers, ported from the iOS Dictus app.
 *
 * Each layout is a List<List<KeyDefinition>> representing rows of keys.
 * Row 1-2: character keys. Row 3: shift + characters + delete. Row 4: utility keys.
 *
 * The AZERTY layout is the default for French users (fr_FR locale).
 * The QWERTY layout is used for English (en_US locale).
 * Numbers and symbols layers are shared across both layouts.
 */
object KeyboardLayouts {

    val azertyLetters: List<List<KeyDefinition>> = listOf(
        listOf("A", "Z", "E", "R", "T", "Y", "U", "I", "O", "P")
            .map { KeyDefinition(label = it, output = it.lowercase()) },
        listOf("Q", "S", "D", "F", "G", "H", "J", "K", "L", "M")
            .map { KeyDefinition(label = it, output = it.lowercase()) },
        listOf(
            KeyDefinition("\u21E7", type = KeyType.SHIFT, widthMultiplier = 1.5f),
            KeyDefinition("W", output = "w"),
            KeyDefinition("X", output = "x"),
            KeyDefinition("C", output = "c"),
            KeyDefinition("V", output = "v"),
            KeyDefinition("B", output = "b"),
            KeyDefinition("N", output = "n"),
            KeyDefinition("'", type = KeyType.ACCENT_ADAPTIVE),
            KeyDefinition("\u232B", type = KeyType.DELETE, widthMultiplier = 1.5f),
        ),
        listOf(
            KeyDefinition("?123", type = KeyType.LAYER_SWITCH, widthMultiplier = 1.2f),
            KeyDefinition("\uD83D\uDE0A", type = KeyType.EMOJI, widthMultiplier = 1.2f),
            KeyDefinition("space", output = " ", type = KeyType.SPACE, widthMultiplier = 4.5f),
            KeyDefinition("\u21B5", type = KeyType.RETURN, widthMultiplier = 1.8f),
        ),
    )

    val qwertyLetters: List<List<KeyDefinition>> = listOf(
        listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P")
            .map { KeyDefinition(label = it, output = it.lowercase()) },
        listOf("A", "S", "D", "F", "G", "H", "J", "K", "L")
            .map { KeyDefinition(label = it, output = it.lowercase()) },
        listOf(
            KeyDefinition("\u21E7", type = KeyType.SHIFT, widthMultiplier = 1.5f),
            KeyDefinition("Z", output = "z"),
            KeyDefinition("X", output = "x"),
            KeyDefinition("C", output = "c"),
            KeyDefinition("V", output = "v"),
            KeyDefinition("B", output = "b"),
            KeyDefinition("N", output = "n"),
            KeyDefinition("M", output = "m"),
            KeyDefinition("\u232B", type = KeyType.DELETE, widthMultiplier = 1.5f),
        ),
        listOf(
            KeyDefinition("?123", type = KeyType.LAYER_SWITCH, widthMultiplier = 1.2f),
            KeyDefinition("\uD83D\uDE0A", type = KeyType.EMOJI, widthMultiplier = 1.2f),
            KeyDefinition("space", output = " ", type = KeyType.SPACE, widthMultiplier = 4.5f),
            KeyDefinition("\u21B5", type = KeyType.RETURN, widthMultiplier = 1.8f),
        ),
    )

    val numbersRows: List<List<KeyDefinition>> = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
            .map { KeyDefinition(label = it, output = it) },
        listOf("-", "/", ":", ";", "(", ")", "&", "@")
            .map { KeyDefinition(label = it, output = it) },
        listOf(
            KeyDefinition("#+=", type = KeyType.LAYER_SWITCH, widthMultiplier = 1.5f),
            KeyDefinition(".", output = "."),
            KeyDefinition(",", output = ","),
            KeyDefinition("?", output = "?"),
            KeyDefinition("!", output = "!"),
            KeyDefinition("'", output = "'"),
            KeyDefinition("\u232B", type = KeyType.DELETE, widthMultiplier = 1.5f),
        ),
        listOf(
            KeyDefinition("ABC", type = KeyType.LAYER_SWITCH, widthMultiplier = 1.2f),
            KeyDefinition("\uD83D\uDE0A", type = KeyType.EMOJI, widthMultiplier = 1.2f),
            KeyDefinition("space", output = " ", type = KeyType.SPACE, widthMultiplier = 4.5f),
            KeyDefinition("\u21B5", type = KeyType.RETURN, widthMultiplier = 1.8f),
        ),
    )

    val symbolsRows: List<List<KeyDefinition>> = listOf(
        listOf("[", "]", "{", "}", "#", "%", "^", "*", "+", "=")
            .map { KeyDefinition(label = it, output = it) },
        listOf("_", "\\", "|", "~", "<", ">", "$", "&", "@")
            .map { KeyDefinition(label = it, output = it) },
        listOf(
            KeyDefinition("123", type = KeyType.LAYER_SWITCH, widthMultiplier = 1.5f),
            KeyDefinition(".", output = "."),
            KeyDefinition(",", output = ","),
            KeyDefinition("?", output = "?"),
            KeyDefinition("!", output = "!"),
            KeyDefinition("'", output = "'"),
            KeyDefinition("\u232B", type = KeyType.DELETE, widthMultiplier = 1.5f),
        ),
        listOf(
            KeyDefinition("ABC", type = KeyType.LAYER_SWITCH, widthMultiplier = 1.2f),
            KeyDefinition("\uD83D\uDE0A", type = KeyType.EMOJI, widthMultiplier = 1.2f),
            KeyDefinition("space", output = " ", type = KeyType.SPACE, widthMultiplier = 4.5f),
            KeyDefinition("\u21B5", type = KeyType.RETURN, widthMultiplier = 1.8f),
        ),
    )

    /**
     * Returns the letter layout for the given layout name.
     * Defaults to AZERTY if the layout name is not recognized.
     */
    fun lettersForLayout(layout: String): List<List<KeyDefinition>> = when (layout) {
        "azerty" -> azertyLetters
        "qwerty" -> qwertyLetters
        else -> azertyLetters
    }
}
