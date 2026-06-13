package com.keyboardai.app.ime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.keyboardai.app.ai.ChatTurn
import com.keyboardai.app.ai.ModelManager
import com.keyboardai.app.ai.PromptBuilder
import com.keyboardai.app.ai.memory.MemoryStore
import com.keyboardai.app.ai.memory.ProfileStore
import com.keyboardai.app.ai.vision.CaptureActivity
import com.keyboardai.app.ai.vision.ScreenCapture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KeyboardAIService : InputMethodService(), KeyboardView.Listener, AiBarView.Listener {

    private var keyboardView: KeyboardView? = null
    private var aiBar: AiBarView? = null
    private lateinit var suggestionEngine: SuggestionEngine
    private lateinit var profileStore: ProfileStore
    private lateinit var memory: MemoryStore

    private var lastShiftTapTime = 0L
    private var lastSpaceTime = 0L

    // AI assistant state
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val promptBuffer = StringBuilder()
    private var aiMode = false
    private var generating = false
    private var generateJob: Job? = null
    private var attachedImagePath: String? = null

    // Form autofill state
    private var autofilling = false
    private var autofillJob: Job? = null
    /** True while a pending screenshot is meant to feed form autofill (not the ✨ prompt). */
    private var autofillViaScreenshot = false

    private val captureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val file = ScreenCapture.captureFile(this@KeyboardAIService)
            val ok = file.exists() && file.length() > 0
            if (autofillViaScreenshot) {
                autofillViaScreenshot = false
                if (ok) {
                    startAutofill(fieldQuestion(), file.absolutePath)
                } else {
                    aiBar?.finishWorking(getString(R.string.ai_autofill_no_capture))
                }
                return
            }
            if (ok) {
                attachedImagePath = file.absolutePath
                aiBar?.setHasImage(true)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        suggestionEngine = SuggestionEngine(this)
        profileStore = ProfileStore(this)
        memory = MemoryStore(this)
        ContextCompat.registerReceiver(
            this,
            captureReceiver,
            IntentFilter(ScreenCapture.ACTION_CAPTURE_READY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onCreateInputView(): View {
        val kb = KeyboardView(this).also {
            it.listener = this
            keyboardView = it
        }
        val bar = AiBarView(this).also {
            it.listener = this
            aiBar = it
        }
        observeModelState()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(bar, LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(kb, LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun observeModelState() {
        scope.launch {
            ModelManager.state.collect { st ->
                if (!generating && !autofilling) return@collect
                when (st) {
                    is ModelManager.State.Downloading ->
                        aiBar?.setStatus("Downloading model ${(st.fraction * 100).toInt()}%")
                    is ModelManager.State.Unpacking ->
                        aiBar?.setStatus("Preparing model ${(st.fraction * 100).toInt()}%")
                    ModelManager.State.Loading -> aiBar?.setStatus("Loading model…")
                    is ModelManager.State.Error -> aiBar?.setStatus("Error: ${st.message}")
                    else -> {}
                }
            }
        }
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        cancelAutofill()
        exitAiMode()
        val view = keyboardView ?: return
        view.layer = if (isNumberField(info)) KeyboardLayouts.SYMBOLS else KeyboardLayouts.LETTERS
        view.enterLabel = enterLabelFor(info)
        view.shiftState = ShiftState.OFF
        lastSpaceTime = 0L
        updateAutoShift()
        updateSuggestions()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        // A fully dismissed keyboard ends the conversation session.
        memory.clearSession()
        cancelAutofill()
        exitAiMode()
    }

    private fun cancelAutofill() {
        autofillJob?.cancel()
        autofilling = false
        autofillViaScreenshot = false
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(captureReceiver) }
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd
        )
        if (aiMode) return
        updateAutoShift()
        updateSuggestions()
    }

    // ---- AiBarView.Listener ----

    override fun onToggleAiMode() {
        aiMode = true
        promptBuffer.clear()
        attachedImagePath = null
        aiBar?.setAiMode(true)
        keyboardView?.suggestions = emptyList()
    }

    override fun onCancel() {
        generateJob?.cancel()
        exitAiMode()
    }

    override fun onScreenshot() {
        if (!aiMode) onToggleAiMode()
        val intent = Intent(this, CaptureActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    override fun onGenerate() {
        if (generating) {
            generateJob?.cancel()
            return
        }
        triggerGenerate()
    }

    /**
     * One-tap form autofill. Figures out what the focused field is asking and
     * writes the right value from the user's saved info. If the editor exposes a
     * label/hint (many sign-up forms) we answer straight away; otherwise — the
     * common case for Google Forms, where the question is a separate on-screen
     * element — we grab a screenshot so the vision model can read the question.
     */
    override fun onAutofill() {
        if (autofilling) return
        if (aiMode) exitAiMode() // autofill writes into the field, not the prompt
        val question = fieldQuestion()
        if (question.isNotBlank()) {
            startAutofill(question, imagePath = null)
        } else {
            autofillViaScreenshot = true
            aiBar?.setWorking(getString(R.string.ai_autofill_reading))
            startActivity(
                Intent(this, CaptureActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun startAutofill(question: String, imagePath: String?) {
        autofillJob?.cancel()
        autofillJob = scope.launch {
            autofilling = true
            aiBar?.setWorking(getString(R.string.ai_autofill_working))
            val ic = currentInputConnection
            try {
                ModelManager.prepare(applicationContext)
                val system = PromptBuilder.formFillPrompt(profileStore.load(), memory)
                val instruction = autofillInstruction(question, currentFieldText(), imagePath != null)
                val answer = StringBuilder()
                ModelManager.engine()
                    .generate(system, emptyList(), instruction, imagePath)
                    .collect { answer.append(it) }

                val value = answer.toString().trim().trim('"').trim()
                if (value.isEmpty() || value.equals(UNKNOWN_MARKER, ignoreCase = true)) {
                    aiBar?.finishWorking(getString(R.string.ai_autofill_unknown))
                } else {
                    replaceFieldText(ic, value)
                    if (question.isNotBlank()) memory.addDailyFact("$question → $value")
                    aiBar?.finishWorking(null)
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                aiBar?.finishWorking(getString(R.string.ai_autofill_error, t.message ?: "failed"))
            } finally {
                autofilling = false
            }
        }
    }

    /** The instruction message sent to the model for a single form field. */
    private fun autofillInstruction(question: String, existing: String, hasImage: Boolean): String =
        buildString {
            if (question.isNotBlank()) {
                append("Form field label/question: \"$question\".")
            } else if (hasImage) {
                append(
                    "The attached screenshot shows a form. Identify the question for the " +
                        "field the user is currently editing (usually the one highlighted or " +
                        "with an empty input) and answer it."
                )
            } else {
                append("Provide the value for the current form field.")
            }
            if (existing.isNotBlank()) {
                append(" The field currently contains: \"$existing\" — replace it.")
            }
            append(" Output only the value to type.")
        }

    /** Reads the field's own label/hint from the editor, if it provides one. */
    private fun fieldQuestion(): String {
        val info = currentInputEditorInfo ?: return ""
        val hint = info.hintText?.toString()?.trim().orEmpty()
        if (hint.isNotEmpty()) return hint
        return info.label?.toString()?.trim().orEmpty()
    }

    /** The text currently in the focused field (both sides of the cursor). */
    private fun currentFieldText(): String {
        val ic = currentInputConnection ?: return ""
        val before = ic.getTextBeforeCursor(MAX_FIELD_READ, 0)?.toString().orEmpty()
        val after = ic.getTextAfterCursor(MAX_FIELD_READ, 0)?.toString().orEmpty()
        return (before + after).trim()
    }

    /** Replaces whatever is in the field with [value]. */
    private fun replaceFieldText(ic: android.view.inputmethod.InputConnection?, value: String) {
        ic ?: return
        ic.beginBatchEdit()
        ic.performContextMenuAction(android.R.id.selectAll)
        ic.commitText(value, 1)
        ic.endBatchEdit()
    }

    private fun exitAiMode() {
        aiMode = false
        generating = false
        promptBuffer.clear()
        attachedImagePath = null
        aiBar?.setGenerating(false)
        aiBar?.setAiMode(false)
    }

    private fun triggerGenerate() {
        val prompt = promptBuffer.toString().trim()
        if (prompt.isEmpty()) return
        val imagePath = attachedImagePath

        generateJob?.cancel()
        generateJob = scope.launch {
            generating = true
            aiBar?.setGenerating(true)
            val ic = currentInputConnection
            try {
                ModelManager.prepare(applicationContext)
                val system = PromptBuilder.systemPrompt(profileStore.load(), memory)
                val history = memory.sessionTurns()
                val answer = StringBuilder()
                ModelManager.engine()
                    .generate(system, history, prompt, imagePath)
                    .collect { chunk ->
                        answer.append(chunk)
                        ic?.commitText(chunk, 1)
                    }
                memory.recordTurn(ChatTurn(fromUser = true, text = prompt))
                memory.recordTurn(ChatTurn(fromUser = false, text = answer.toString()))
                exitAiMode()
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                aiBar?.setStatus("AI error: ${t.message ?: "failed"}")
                generating = false
                aiBar?.setGenerating(false)
            }
        }
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
        if (isComposingPrompt()) {
            commitText(" ")
            return
        }
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
        if (!isComposingPrompt() && !text[0].isLetterOrDigit()) learnCurrentWord()
        commitText(text)
        if (view.shiftState == ShiftState.SHIFTED) {
            view.shiftState = ShiftState.OFF
        }
    }

    /** True while the user is typing into the AI prompt rather than the app. */
    private fun isComposingPrompt(): Boolean = aiMode && !generating

    private fun commitText(text: String) {
        if (isComposingPrompt()) {
            promptBuffer.append(text)
            aiBar?.setPrompt(promptBuffer.toString())
        } else {
            currentInputConnection?.commitText(text, 1)
        }
    }

    private fun sendDelete() {
        if (isComposingPrompt()) {
            if (promptBuffer.isNotEmpty()) {
                promptBuffer.deleteCharAt(promptBuffer.length - 1)
                aiBar?.setPrompt(promptBuffer.toString())
            }
            return
        }
        // KEYCODE_DEL handles selections and lets editors run their own
        // backspace behavior; plain deleteSurroundingText would not.
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }

    private fun handleEnter() {
        if (isComposingPrompt()) {
            triggerGenerate()
            return
        }
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
        if (aiMode) {
            keyboardView?.suggestions = emptyList()
            return
        }
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
        const val MAX_FIELD_READ = 2000
        const val UNKNOWN_MARKER = "(unknown)"
    }
}
