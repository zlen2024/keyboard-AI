package com.keyboardai.app.ime

/**
 * Negative key codes are special (non-printing) keys. Positive codes are the
 * Unicode code point the key commits.
 */
object KeyCodes {
    const val SHIFT = -1
    const val DELETE = -2
    const val MODE_CHANGE = -3   // letters <-> symbols
    const val SPACE = -4
    const val ENTER = -5
    const val LANG_SWITCH = -6   // globe: next input method
    const val SYM_SHIFT = -7     // symbols page 1 <-> page 2
}

data class Key(
    val code: Int,
    val label: String,
    val widthWeight: Float = 1f,
    /** Small label drawn in the key's top-right corner. */
    val hint: String? = null,
    /** Characters offered in the long-press popup. */
    val alternates: List<String> = emptyList(),
)

/** Rows narrower than the widest row are centered by the view. */
data class KeyboardLayer(val rows: List<List<Key>>)

object KeyboardLayouts {

    private val accents = mapOf(
        'a' to "àáâäãåā",
        'c' to "çć",
        'e' to "èéêëēę",
        'g' to "ğ",
        'i' to "ìíîïī",
        'n' to "ñń",
        'o' to "òóôöõø",
        's' to "śš",
        'u' to "ùúûüū",
        'y' to "ÿý",
        'z' to "žźż",
    )

    private fun charKey(c: Char) = Key(
        code = c.code,
        label = c.toString(),
        alternates = accents[c]?.map { it.toString() } ?: emptyList(),
    )

    private fun charRow(chars: String): List<Key> = chars.map { charKey(it) }

    private val shift = Key(KeyCodes.SHIFT, "⇧", 1.5f)
    private val delete = Key(KeyCodes.DELETE, "⌫", 1.5f)
    private val space = Key(KeyCodes.SPACE, "Keyboard AI", 4f)
    private val enter = Key(KeyCodes.ENTER, "⏎", 1.5f)
    private val lang = Key(KeyCodes.LANG_SWITCH, "🌐")
    private val toSymbols = Key(KeyCodes.MODE_CHANGE, "?123", 1.5f)
    private val toLetters = Key(KeyCodes.MODE_CHANGE, "ABC", 1.5f)
    private val toSymbols2 = Key(KeyCodes.SYM_SHIFT, "=\\<", 1.5f)
    private val toSymbols1 = Key(KeyCodes.SYM_SHIFT, "?123", 1.5f)

    private val comma = Key(
        ','.code, ",",
        hint = "!",
        alternates = listOf("!", ";", ":", "'", "\""),
    )
    private val period = Key(
        '.'.code, ".",
        hint = "?",
        alternates = listOf("?", "!", "…", ",", "-"),
    )

    val LETTERS = KeyboardLayer(
        listOf(
            charRow("1234567890"),
            charRow("qwertyuiop"),
            charRow("asdfghjkl"),
            listOf(shift) + charRow("zxcvbnm") + listOf(delete),
            listOf(toSymbols, comma, lang, space, period, enter),
        )
    )

    val SYMBOLS = KeyboardLayer(
        listOf(
            charRow("1234567890"),
            charRow("@#\$_&-+()/"),
            listOf(toSymbols2) + charRow("*\"':;!?") + listOf(delete),
            listOf(toLetters, comma, lang, space, period, enter),
        )
    )

    val SYMBOLS_SHIFTED = KeyboardLayer(
        listOf(
            charRow("~`|•√π÷×¶∆"),
            charRow("£€¥^°={}\\"),
            listOf(toSymbols1) + charRow("%©®™✓[]") + listOf(delete),
            listOf(toLetters, charKey('<'), lang, space, charKey('>'), enter),
        )
    )
}
