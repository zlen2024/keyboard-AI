package com.keyboardai.app.ime

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager

class KeyboardAIService : InputMethodService(), KeyboardView.Listener {

    private var keyboardView: KeyboardView? = null
    private lateinit var suggestionEngine: SuggestionEngine
    private var lastShiftTapTime = 0L
    private var lastSpaceTime = 0L

    override fun onCreate() {
        super.onCreate()
        suggestionEngine = SuggestionEngine(this)
    }

    override fun onCreateInputView(): View {
        return KeyboardView(this).also {
            it.listener = this
            keyboardView = it
        }
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val view = keyboardView ?: return
        view.layer = if (isNumberField(info)) KeyboardLayouts.SYMBOLS else KeyboardLayouts.LETTERS
        view.enterLabel = enterLabelFor(info)
        view.shiftState = ShiftState.OFF
        lastSpaceTime = 0L
        updateAutoShift()
        updateSuggestions()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd
        )
        updateAutoShift()
        updateSuggestions()
    }

    // ---- KeyboardView.Listener ----

    override fun onKeyPressed(key: Key) {
        when (key.code) {
            KeyCodes.SHIFT -> handleShift()
            KeyCodes.DELETE -> sendDelete()
            KeyCodes.SPACE -> handleSpace()
            KeyCodes.ENTER -> handleEnter()
            KeyCodes.MODE_CHANGE -> toggleSymbols()
            KeyCodes.SYM_SHIFT -> toggleSymbolsPage()
            KeyCodes.LANG_SWITCH -> switchToNextKeyboard()
            else -> commitCharacter(key)
        }
    }

    override fun onDeleteRepeated() = sendDelete()

    override fun onSuggestionPicked(word: String) {
        val ic = currentInputConnection ?: return
        val prefix = currentWordPrefix()
        ic.beginBatchEdit()
        if (prefix.isNotEmpty()) ic.deleteSurroundingText(prefix.length, 0)
        ic.commitText("$word ", 1)
        ic.endBatchEdit()
        suggestionEngine.learn(word)
    }

    override fun onAlternatePicked(text: String) {
        commitText(text)
        val view = keyboardView ?: return
        if (view.shiftState == ShiftState.SHIFTED) view.shiftState = ShiftState.OFF
    }

    override fun onCursorMove(delta: Int) {
        val keyCode = if (delta > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        repeat(minOf(kotlin.math.abs(delta), 20)) {
            sendDownUpKeyEvents(keyCode)
        }
    }

    // ---- key handlers ----

    private fun handleShift() {
        val view = keyboardView ?: return
        val now = SystemClock.uptimeMillis()
        val isDoubleTap = now - lastShiftTapTime < DOUBLE_TAP_MS
        lastShiftTapTime = now

        view.shiftState = when (view.shiftState) {
            ShiftState.OFF -> ShiftState.SHIFTED
            ShiftState.SHIFTED -> if (isDoubleTap) ShiftState.CAPS_LOCK else ShiftState.OFF
            ShiftState.CAPS_LOCK -> ShiftState.OFF
        }
    }

    private fun handleSpace() {
        val now = SystemClock.uptimeMillis()
        val ic = currentInputConnection

        learnCurrentWord()

        // Double-space inserts ". " — but only right after a word.
        if (now - lastSpaceTime < DOUBLE_SPACE_MS && ic != null) {
            val before = ic.getTextBeforeCursor(2, 0)
            if (before != null && before.length == 2 &&
                before[1] == ' ' && before[0].isLetterOrDigit()
            ) {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                ic.endBatchEdit()
                lastSpaceTime = 0L
                return
            }
        }
        lastSpaceTime = now
        commitText(" ")
    }

    private fun commitCharacter(key: Key) {
        val view = keyboardView ?: return
        val text = if (view.shiftState != ShiftState.OFF) key.label.uppercase() else key.label
        if (!text[0].isLetterOrDigit()) learnCurrentWord()
        commitText(text)
        if (view.shiftState == ShiftState.SHIFTED) {
            view.shiftState = ShiftState.OFF
        }
    }

    private fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    private fun sendDelete() {
        // KEYCODE_DEL handles selections and lets editors run their own
        // backspace behavior; plain deleteSurroundingText would not.
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }

    private fun handleEnter() {
        learnCurrentWord()
        val info = currentInputEditorInfo
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val noEnterAction =
            info?.imeOptions?.and(EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0

        if (action != EditorInfo.IME_ACTION_NONE &&
            action != EditorInfo.IME_ACTION_UNSPECIFIED &&
            !noEnterAction
        ) {
            currentInputConnection?.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    private fun toggleSymbols() {
        val view = keyboardView ?: return
        view.layer = if (view.layer === KeyboardLayouts.LETTERS) {
            KeyboardLayouts.SYMBOLS
        } else {
            KeyboardLayouts.LETTERS
        }
        view.shiftState = ShiftState.OFF
        if (view.layer === KeyboardLayouts.LETTERS) updateAutoShift()
    }

    private fun toggleSymbolsPage() {
        val view = keyboardView ?: return
        view.layer = if (view.layer === KeyboardLayouts.SYMBOLS) {
            KeyboardLayouts.SYMBOLS_SHIFTED
        } else {
            KeyboardLayouts.SYMBOLS
        }
    }

    private fun switchToNextKeyboard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            switchToNextInputMethod(false)
        } else {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            @Suppress("DEPRECATION")
            imm.switchToNextInputMethod(window.window?.attributes?.token, false)
        }
    }

    // ---- suggestions ----

    /** The partial word immediately before the cursor. */
    private fun currentWordPrefix(): String {
        val before = currentInputConnection?.getTextBeforeCursor(MAX_WORD_LOOKBACK, 0) ?: return ""
        return before.takeLastWhile { it.isLetter() || it == '\'' }.toString()
    }

    private fun updateSuggestions() {
        keyboardView?.suggestions = suggestionEngine.suggest(currentWordPrefix(), 3)
    }

    private fun learnCurrentWord() {
        val word = currentWordPrefix()
        if (word.isNotEmpty()) suggestionEngine.learn(word)
    }

    // ---- auto-capitalization ----

    /** Shift on at the start of a sentence when the editor asks for it. */
    private fun updateAutoShift() {
        val view = keyboardView ?: return
        if (view.shiftState == ShiftState.CAPS_LOCK) return
        if (view.layer !== KeyboardLayouts.LETTERS) return

        val info = currentInputEditorInfo ?: return
        if (info.inputType == InputType.TYPE_NULL) return

        val caps = currentInputConnection?.getCursorCapsMode(info.inputType) ?: 0
        view.shiftState = if (caps != 0) ShiftState.SHIFTED else ShiftState.OFF
    }

    // ---- helpers ----

    private fun isNumberField(info: EditorInfo): Boolean {
        return when (info.inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_PHONE,
            InputType.TYPE_CLASS_DATETIME -> true
            else -> false
        }
    }

    private fun enterLabelFor(info: EditorInfo): String {
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return "⏎"
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO -> "Go"
            EditorInfo.IME_ACTION_SEARCH -> "🔍"
            EditorInfo.IME_ACTION_SEND -> "Send"
            EditorInfo.IME_ACTION_NEXT -> "Next"
            EditorInfo.IME_ACTION_DONE -> "Done"
            else -> "⏎"
        }
    }

    private companion object {
        const val DOUBLE_TAP_MS = 300L
        const val DOUBLE_SPACE_MS = 500L
        const val MAX_WORD_LOOKBACK = 48
    }
}
