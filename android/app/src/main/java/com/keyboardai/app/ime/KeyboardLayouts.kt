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
)

/** Rows narrower than the widest row are centered by the view. */
data class KeyboardLayer(val rows: List<List<Key>>)

object KeyboardLayouts {

    private fun charKey(c: Char) = Key(c.code, c.toString())

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

    val LETTERS = KeyboardLayer(
        listOf(
            charRow("qwertyuiop"),
            charRow("asdfghjkl"),
            listOf(shift) + charRow("zxcvbnm") + listOf(delete),
            listOf(toSymbols, charKey(','), lang, space, charKey('.'), enter),
        )
    )

    val SYMBOLS = KeyboardLayer(
        listOf(
            charRow("1234567890"),
            charRow("@#\$_&-+()/"),
            listOf(toSymbols2) + charRow("*\"':;!?") + listOf(delete),
            listOf(toLetters, charKey(','), lang, space, charKey('.'), enter),
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
