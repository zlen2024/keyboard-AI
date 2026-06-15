package com.keyboardai.app.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.keyboardai.app.R

/**
 * The strip above the keyboard that drives the AI assistant. Plain Android
 * widgets (not the canvas keyboard) so it stays simple and robust. The service
 * owns all state and calls the setters here; taps are reported via [listener].
 */
@SuppressLint("ViewConstructor")
class AiBarView(context: Context) : LinearLayout(context) {

    interface Listener {
        fun onToggleAiMode()
        fun onGenerate()
        fun onCancel()
        fun onAutofill()
        fun onToggleVision()
    }

    var listener: Listener? = null

    private val promptText: TextView
    private val leadingButton: TextView
    private val autofillButton: TextView
    private val visionButton: TextView
    private val actionButton: TextView

    private var aiMode = false
    private var generating = false
    private var visionOn = true
    /** A blocking task (form autofill, model load) is running; buttons are locked. */
    private var working = false
    /** A pinned message (working status, result, or error) shown until the next interaction. */
    private var statusMsg: String? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(ContextCompat.getColor(context, R.color.kb_background))
        val pad = dp(6)
        setPadding(dp(8), pad, dp(8), pad)

        leadingButton = pillButton("✨").apply {
            setOnClickListener {
                if (aiMode) listener?.onCancel() else listener?.onToggleAiMode()
            }
        }
        autofillButton = pillButton("📝").apply {
            setOnClickListener { if (!working) listener?.onAutofill() }
        }
        promptText = TextView(context).apply {
            setTextColor(ContextCompat.getColor(context, R.color.kb_text_dim))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.START
            text = context.getString(R.string.ai_hint)
            val lp = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            lp.marginStart = dp(8)
            lp.marginEnd = dp(8)
            layoutParams = lp
            setOnClickListener { if (!aiMode) listener?.onToggleAiMode() }
        }
        visionButton = pillButton("👁").apply {
            setOnClickListener { listener?.onToggleVision() }
        }
        actionButton = pillButton("➤").apply {
            setOnClickListener { listener?.onGenerate() }
        }

        addView(leadingButton)
        addView(autofillButton)
        addView(promptText)
        addView(visionButton)
        addView(actionButton)
        render()
    }

    private fun pillButton(label: String): TextView = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        setTextColor(ContextCompat.getColor(context, R.color.kb_text))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        val size = dp(40)
        layoutParams = LayoutParams(size, size)
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = true
        isFocusable = true
    }

    // ---- state from the service ----

    fun setAiMode(active: Boolean) {
        aiMode = active
        statusMsg = null
        if (!active) generating = false
        render()
    }

    fun setPrompt(text: String) {
        if (aiMode && !generating) {
            promptText.text = text.ifEmpty { context.getString(R.string.ai_prompt_hint) }
        }
    }

    fun setGenerating(value: Boolean) {
        generating = value
        if (value) statusMsg = null
        render()
    }

    fun setVisionEnabled(value: Boolean) {
        visionOn = value
        render()
    }

    /** Shows a transient status (download %, errors) without changing the working state. */
    fun setStatus(status: String?) {
        statusMsg = status
        render()
    }

    /** Marks a blocking task (autofill, model load) as running and pins its [message]. */
    fun setWorking(message: String) {
        working = true
        statusMsg = message
        render()
    }

    /** Ends the working task, optionally leaving a final [result] message pinned. */
    fun finishWorking(result: String?) {
        working = false
        statusMsg = result
        render()
    }

    private fun render() {
        leadingButton.text = if (aiMode) "✕" else "✨"
        autofillButton.visibility = if (working || generating || aiMode) View.GONE else View.VISIBLE
        actionButton.visibility = if (aiMode && !working) View.VISIBLE else View.GONE
        visionButton.visibility = if (working || generating) View.GONE else View.VISIBLE
        visionButton.text = if (visionOn) "👁" else "🚫"
        visionButton.alpha = if (visionOn) 1f else 0.5f
        actionButton.text = if (generating) "■" else "➤"
        actionButton.setTextColor(
            ContextCompat.getColor(
                context,
                if (generating) R.color.kb_text else R.color.kb_key_accent,
            )
        )

        val pinned = statusMsg
        if (pinned != null) {
            promptText.text = pinned
            promptText.setTextColor(ContextCompat.getColor(context, R.color.kb_text_dim))
        } else if (!aiMode) {
            promptText.text = context.getString(R.string.ai_hint)
        } else if (!generating) {
            promptText.text = context.getString(R.string.ai_prompt_hint)
        }
    }
}
