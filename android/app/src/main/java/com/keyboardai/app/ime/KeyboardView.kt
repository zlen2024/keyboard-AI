package com.keyboardai.app.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import androidx.core.content.ContextCompat
import com.keyboardai.app.R
import kotlin.math.max
import kotlin.math.min

enum class ShiftState { OFF, SHIFTED, CAPS_LOCK }

/**
 * Canvas-drawn keyboard with an integrated suggestion strip. Owns visuals and
 * touch handling; input logic (shift cycling, layer switching, text commits)
 * lives in the service, which mutates [layer], [shiftState], [enterLabel] and
 * [suggestions].
 */
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        fun onKeyPressed(key: Key)
        /** Called repeatedly while delete is held down. */
        fun onDeleteRepeated()
        fun onSuggestionPicked(word: String)
        /** A character chosen from a long-press popup. */
        fun onAlternatePicked(text: String)
        /** Spacebar cursor mode: move the cursor [delta] characters. */
        fun onCursorMove(delta: Int)
    }

    var listener: Listener? = null

    var layer: KeyboardLayer = KeyboardLayouts.LETTERS
        set(value) {
            field = value
            computeKeyBounds()
            invalidate()
        }

    var shiftState: ShiftState = ShiftState.OFF
        set(value) {
            field = value
            invalidate()
        }

    var enterLabel: String = "⏎"
        set(value) {
            field = value
            invalidate()
        }

    var suggestions: List<String> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    private data class KeySlot(val key: Key, val bounds: RectF)

    private val slots = mutableListOf<KeySlot>()
    private var pressedSlot: KeySlot? = null
    private var pressedSuggestionIndex = -1

    // Long-press state
    private var alternatesSlot: KeySlot? = null
    private var alternateIndex = -1
    private var cursorMode = false
    private var cursorLastX = 0f

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val stripHeight = dp(44f)
    private val rowHeight = dp(48f)
    private val keyGap = dp(4f)
    private val sidePadding = dp(3f)
    private val bottomPadding = dp(4f)
    private val keyCorner = dp(9f)
    private var bottomInset = 0

    private val bgColor = ContextCompat.getColor(context, R.color.kb_background)
    private val keyColor = ContextCompat.getColor(context, R.color.kb_key)
    private val specialKeyColor = ContextCompat.getColor(context, R.color.kb_key_special)
    private val accentColor = ContextCompat.getColor(context, R.color.kb_key_accent)
    private val pressedColor = ContextCompat.getColor(context, R.color.kb_key_pressed)
    private val textColor = ContextCompat.getColor(context, R.color.kb_text)
    private val dimTextColor = ContextCompat.getColor(context, R.color.kb_text_dim)

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        color = dimTextColor
    }

    private val handlerMain = Handler(Looper.getMainLooper())
    private var deleteRepeating = false
    private val deleteRepeater = object : Runnable {
        override fun run() {
            deleteRepeating = true
            listener?.onDeleteRepeated()
            handlerMain.postDelayed(this, DELETE_REPEAT_MS)
        }
    }
    private val longPressRunnable = Runnable { onLongPress() }

    // ---- sizing & insets ----

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        updateBottomInset(insets)
        return super.onApplyWindowInsets(insets)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        rootWindowInsets?.let { updateBottomInset(it) }
    }

    private fun updateBottomInset(insets: WindowInsets) {
        val newInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            insets.getInsets(WindowInsets.Type.navigationBars()).bottom
        } else {
            @Suppress("DEPRECATION")
            insets.systemWindowInsetBottom
        }
        if (newInset != bottomInset) {
            bottomInset = newInset
            requestLayout()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (stripHeight + rowHeight * layer.rows.size +
            bottomPadding + bottomInset).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        computeKeyBounds()
    }

    private fun computeKeyBounds() {
        slots.clear()
        if (width == 0) return

        val usableWidth = width - 2 * sidePadding
        val maxWeight = layer.rows.maxOf { row -> row.sumOf { it.widthWeight.toDouble() } }.toFloat()
        val unit = usableWidth / maxWeight

        layer.rows.forEachIndexed { rowIndex, row ->
            val rowWeight = row.sumOf { it.widthWeight.toDouble() }.toFloat()
            var x = sidePadding + (usableWidth - rowWeight * unit) / 2f
            val top = stripHeight + rowIndex * rowHeight
            row.forEach { key ->
                val w = key.widthWeight * unit
                slots += KeySlot(
                    key,
                    RectF(
                        x + keyGap / 2, top + keyGap / 2,
                        x + w - keyGap / 2, top + rowHeight - keyGap / 2
                    )
                )
                x += w
            }
        }
    }

    // ---- drawing ----

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(bgColor)
        drawSuggestionStrip(canvas)

        for (slot in slots) {
            drawKey(canvas, slot)
        }

        alternatesSlot?.let { drawAlternatesBalloon(canvas, it) }
            ?: pressedSlot?.takeIf { it.key.code > 0 }?.let { drawPreviewBalloon(canvas, it) }
    }

    private fun drawSuggestionStrip(canvas: Canvas) {
        if (suggestions.isEmpty()) return
        val cellWidth = width / SUGGESTION_COUNT.toFloat()

        textPaint.textSize = dp(16f)
        for (i in 0 until min(suggestions.size, SUGGESTION_COUNT)) {
            val centerX = cellWidth * i + cellWidth / 2
            if (i == pressedSuggestionIndex) {
                keyPaint.color = pressedColor
                canvas.drawRoundRect(
                    cellWidth * i + dp(4f), dp(4f),
                    cellWidth * (i + 1) - dp(4f), stripHeight - dp(4f),
                    keyCorner, keyCorner, keyPaint
                )
            }
            textPaint.color = textColor
            val centerY = stripHeight / 2 -
                (textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) / 2
            canvas.drawText(suggestions[i], centerX, centerY, textPaint)

            if (i > 0) {
                keyPaint.color = specialKeyColor
                canvas.drawRect(
                    cellWidth * i - dp(0.5f), dp(10f),
                    cellWidth * i + dp(0.5f), stripHeight - dp(10f),
                    keyPaint
                )
            }
        }
    }

    private fun drawKey(canvas: Canvas, slot: KeySlot) {
        val key = slot.key
        val pressed = slot === pressedSlot && alternatesSlot == null && !cursorMode

        keyPaint.color = when {
            pressed -> pressedColor
            key.code == KeyCodes.ENTER -> accentColor
            key.code == KeyCodes.SHIFT && shiftState != ShiftState.OFF -> accentColor
            key.code < 0 && key.code != KeyCodes.SPACE -> specialKeyColor
            else -> keyColor
        }
        canvas.drawRoundRect(slot.bounds, keyCorner, keyCorner, keyPaint)

        val label = labelFor(key)
        textPaint.color = if (key.code == KeyCodes.SPACE) dimTextColor else textColor
        textPaint.textSize = when {
            key.code == KeyCodes.SPACE -> dp(12f)
            key.code < 0 || label.length > 1 -> dp(15f)
            else -> dp(21f)
        }
        val centerY = slot.bounds.centerY() -
            (textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) / 2
        canvas.drawText(label, slot.bounds.centerX(), centerY, textPaint)

        key.hint?.let {
            hintPaint.textSize = dp(10f)
            canvas.drawText(
                it,
                slot.bounds.right - dp(5f),
                slot.bounds.top + dp(13f),
                hintPaint
            )
        }
    }

    private fun drawPreviewBalloon(canvas: Canvas, slot: KeySlot) {
        val balloonW = max(slot.bounds.width() * 1.15f, dp(44f))
        val balloonH = rowHeight
        val rect = balloonRect(slot, balloonW, balloonH)

        keyPaint.color = pressedColor
        canvas.drawRoundRect(rect, keyCorner, keyCorner, keyPaint)
        textPaint.color = textColor
        textPaint.textSize = dp(26f)
        val centerY = rect.centerY() -
            (textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) / 2
        canvas.drawText(labelFor(slot.key), rect.centerX(), centerY, textPaint)
    }

    private fun drawAlternatesBalloon(canvas: Canvas, slot: KeySlot) {
        val alternates = alternatesFor(slot.key)
        if (alternates.isEmpty()) return
        val cell = dp(38f)
        val rect = balloonRect(slot, cell * alternates.size + dp(8f), rowHeight)

        keyPaint.color = specialKeyColor
        canvas.drawRoundRect(rect, keyCorner, keyCorner, keyPaint)

        alternates.forEachIndexed { i, alt ->
            val cx = rect.left + dp(4f) + cell * i + cell / 2
            if (i == alternateIndex) {
                keyPaint.color = accentColor
                canvas.drawRoundRect(
                    rect.left + dp(4f) + cell * i + dp(2f), rect.top + dp(4f),
                    rect.left + dp(4f) + cell * (i + 1) - dp(2f), rect.bottom - dp(4f),
                    keyCorner, keyCorner, keyPaint
                )
            }
            textPaint.color = textColor
            textPaint.textSize = dp(20f)
            val centerY = rect.centerY() -
                (textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) / 2
            canvas.drawText(alt, cx, centerY, textPaint)
        }
    }

    /** A balloon hovering above [slot], clamped to stay inside the view. */
    private fun balloonRect(slot: KeySlot, w: Float, h: Float): RectF {
        var left = slot.bounds.centerX() - w / 2
        left = left.coerceIn(dp(2f), width - w - dp(2f))
        var top = slot.bounds.top - h - dp(6f)
        if (top < dp(2f)) top = dp(2f)
        return RectF(left, top, left + w, top + h)
    }

    private fun labelFor(key: Key): String = when {
        key.code == KeyCodes.ENTER -> enterLabel
        key.code == KeyCodes.SHIFT && shiftState == ShiftState.CAPS_LOCK -> "⇪"
        key.code > 0 && shiftState != ShiftState.OFF -> key.label.uppercase()
        else -> key.label
    }

    private fun alternatesFor(key: Key): List<String> =
        if (shiftState != ShiftState.OFF) key.alternates.map { it.uppercase() }
        else key.alternates

    // ---- touch handling ----

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleDown(event.x, event.y)
            MotionEvent.ACTION_MOVE -> handleMove(event.x, event.y)
            MotionEvent.ACTION_UP -> handleUp()
            MotionEvent.ACTION_CANCEL -> reset()
        }
        return true
    }

    private fun handleDown(x: Float, y: Float) {
        if (y < stripHeight) {
            pressedSuggestionIndex = suggestionIndexAt(x)
            invalidate()
            return
        }
        val slot = slotAt(x, y) ?: return
        pressedSlot = slot
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

        when {
            slot.key.code == KeyCodes.DELETE -> {
                deleteRepeating = false
                handlerMain.postDelayed(deleteRepeater, DELETE_INITIAL_DELAY_MS)
            }
            slot.key.code == KeyCodes.SPACE || slot.key.alternates.isNotEmpty() -> {
                cursorLastX = x
                handlerMain.postDelayed(longPressRunnable, LONG_PRESS_MS)
            }
        }
        invalidate()
    }

    private fun onLongPress() {
        val slot = pressedSlot ?: return
        if (slot.key.code == KeyCodes.SPACE) {
            cursorMode = true
        } else if (slot.key.alternates.isNotEmpty()) {
            alternatesSlot = slot
            alternateIndex = 0
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
        invalidate()
    }

    private fun handleMove(x: Float, y: Float) {
        when {
            cursorMode -> {
                val step = dp(14f)
                val delta = ((x - cursorLastX) / step).toInt()
                if (delta != 0) {
                    cursorLastX += delta * step
                    listener?.onCursorMove(delta)
                }
            }
            alternatesSlot != null -> {
                val slot = alternatesSlot ?: return
                val alternates = alternatesFor(slot.key)
                val cell = dp(38f)
                val rect = balloonRect(slot, cell * alternates.size + dp(8f), rowHeight)
                alternateIndex = (((x - rect.left - dp(4f)) / cell).toInt())
                    .coerceIn(0, alternates.size - 1)
                invalidate()
            }
            pressedSuggestionIndex >= 0 -> {
                if (y >= stripHeight || suggestionIndexAt(x) != pressedSuggestionIndex) {
                    pressedSuggestionIndex = -1
                    invalidate()
                }
            }
            else -> {
                val slot = slotAt(x, y)
                if (slot !== pressedSlot) {
                    cancelTimers()
                    pressedSlot = slot
                    invalidate()
                }
            }
        }
    }

    private fun handleUp() {
        val suggestion = pressedSuggestionIndex
        val altSlot = alternatesSlot
        val altIdx = alternateIndex
        val slot = pressedSlot
        val wasRepeating = deleteRepeating
        val wasCursorMode = cursorMode
        reset()

        when {
            suggestion >= 0 && suggestion < suggestions.size ->
                listener?.onSuggestionPicked(suggestions[suggestion])
            altSlot != null -> {
                val alternates = alternatesFor(altSlot.key)
                if (altIdx in alternates.indices) {
                    listener?.onAlternatePicked(alternates[altIdx])
                }
            }
            wasCursorMode -> Unit // dragging the cursor commits nothing
            slot != null && !(slot.key.code == KeyCodes.DELETE && wasRepeating) ->
                listener?.onKeyPressed(slot.key)
        }
    }

    private fun reset() {
        cancelTimers()
        pressedSlot = null
        pressedSuggestionIndex = -1
        alternatesSlot = null
        alternateIndex = -1
        cursorMode = false
        invalidate()
    }

    private fun cancelTimers() {
        handlerMain.removeCallbacks(deleteRepeater)
        handlerMain.removeCallbacks(longPressRunnable)
        deleteRepeating = false
    }

    private fun suggestionIndexAt(x: Float): Int {
        if (suggestions.isEmpty()) return -1
        val index = (x / (width / SUGGESTION_COUNT.toFloat())).toInt()
        return if (index in 0 until min(suggestions.size, SUGGESTION_COUNT)) index else -1
    }

    private fun slotAt(x: Float, y: Float): KeySlot? {
        slots.firstOrNull { it.bounds.contains(x, y) }?.let { return it }
        val fudge = keyGap
        return slots.firstOrNull {
            x >= it.bounds.left - fudge && x <= it.bounds.right + fudge &&
                y >= it.bounds.top - fudge && y <= it.bounds.bottom + fudge
        }
    }

    override fun onDetachedFromWindow() {
        cancelTimers()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val DELETE_INITIAL_DELAY_MS = 350L
        const val DELETE_REPEAT_MS = 50L
        const val LONG_PRESS_MS = 350L
        const val SUGGESTION_COUNT = 3
    }
}
