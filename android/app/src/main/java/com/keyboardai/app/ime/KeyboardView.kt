package com.keyboardai.app.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.keyboardai.app.R

enum class ShiftState { OFF, SHIFTED, CAPS_LOCK }

/**
 * Canvas-drawn keyboard. Owns only the visuals and touch handling; all input
 * logic (shift cycling, layer switching, text commits) lives in the service,
 * which mutates [layer], [shiftState] and [enterLabel].
 */
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        fun onKeyPressed(key: Key)
        /** Called repeatedly while delete is held down. */
        fun onDeleteRepeated()
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

    private data class KeySlot(val key: Key, val bounds: RectF)

    private val slots = mutableListOf<KeySlot>()
    private var pressedSlot: KeySlot? = null

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val rowHeight = dp(56f)
    private val keyGap = dp(4f)
    private val sidePadding = dp(3f)
    private val topPadding = dp(6f)
    private val bottomPadding = dp(6f)
    private val keyCorner = dp(8f)

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

    private val repeatHandler = Handler(Looper.getMainLooper())
    private var deleteRepeating = false
    private val deleteRepeater = object : Runnable {
        override fun run() {
            deleteRepeating = true
            listener?.onDeleteRepeated()
            repeatHandler.postDelayed(this, DELETE_REPEAT_MS)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (rowHeight * layer.rows.size + topPadding + bottomPadding).toInt()
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
            val top = topPadding + rowIndex * rowHeight
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

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(bgColor)

        for (slot in slots) {
            val key = slot.key
            val pressed = slot === pressedSlot

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
                key.code == KeyCodes.SPACE -> dp(13f)
                key.code < 0 || label.length > 1 -> dp(16f)
                else -> dp(22f)
            }
            val centerY = slot.bounds.centerY() -
                (textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) / 2
            canvas.drawText(label, slot.bounds.centerX(), centerY, textPaint)
        }
    }

    private fun labelFor(key: Key): String = when {
        key.code == KeyCodes.ENTER -> enterLabel
        key.code == KeyCodes.SHIFT && shiftState == ShiftState.CAPS_LOCK -> "⇪"
        key.code > 0 && shiftState != ShiftState.OFF ->
            key.label.uppercase()
        else -> key.label
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val slot = slotAt(event.x, event.y) ?: return true
                pressedSlot = slot
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                if (slot.key.code == KeyCodes.DELETE) {
                    deleteRepeating = false
                    repeatHandler.postDelayed(deleteRepeater, DELETE_INITIAL_DELAY_MS)
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val slot = slotAt(event.x, event.y)
                if (slot !== pressedSlot) {
                    cancelDeleteRepeat()
                    pressedSlot = slot
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                val slot = pressedSlot
                val wasRepeating = deleteRepeating
                cancelDeleteRepeat()
                pressedSlot = null
                invalidate()
                // A held-down delete already emitted events through the repeater.
                if (slot != null && !(slot.key.code == KeyCodes.DELETE && wasRepeating)) {
                    listener?.onKeyPressed(slot.key)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelDeleteRepeat()
                pressedSlot = null
                invalidate()
            }
        }
        return true
    }

    private fun cancelDeleteRepeat() {
        repeatHandler.removeCallbacks(deleteRepeater)
        deleteRepeating = false
    }

    private fun slotAt(x: Float, y: Float): KeySlot? {
        // Exact hit first, then forgive small misses by expanding each key by
        // half the inter-key gap so taps between keys still register.
        slots.firstOrNull { it.bounds.contains(x, y) }?.let { return it }
        val fudge = keyGap
        return slots.firstOrNull {
            x >= it.bounds.left - fudge && x <= it.bounds.right + fudge &&
                y >= it.bounds.top - fudge && y <= it.bounds.bottom + fudge
        }
    }

    override fun onDetachedFromWindow() {
        cancelDeleteRepeat()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val DELETE_INITIAL_DELAY_MS = 350L
        const val DELETE_REPEAT_MS = 50L
    }
}
