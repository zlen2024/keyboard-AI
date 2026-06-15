package com.keyboardai.app.ui

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.keyboardai.app.R
import com.keyboardai.app.ai.memory.InfoEntry
import com.keyboardai.app.ai.memory.InfoStore

/**
 * The "harness": a free-form key/value page where the user records anything they
 * want the AI to know (name, email, phone, student ID, preferences…). No fixed
 * schema — the assistant draws on these facts to autofill forms and write as the
 * user. Built programmatically to keep the resource set small.
 */
class AiSetupActivity : AppCompatActivity() {

    private lateinit var infoStore: InfoStore
    private lateinit var rowsContainer: LinearLayout
    private val rows = mutableListOf<Row>()

    private class Row(val container: LinearLayout, val key: EditText, val value: EditText)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.ai_setup_title)
        infoStore = InfoStore(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            setBackgroundColor(ContextCompat.getColor(this@AiSetupActivity, R.color.brand_surface))
        }

        root.addView(header(getString(R.string.ai_profile_header)))
        root.addView(caption(getString(R.string.ai_profile_caption)))

        rowsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(rowsContainer)

        val existing = infoStore.load()
        if (existing.isEmpty()) {
            SUGGESTED_KEYS.forEach { addRow(it, "") }
        } else {
            existing.forEach { addRow(it.key, it.value) }
        }

        root.addView(Button(this).apply {
            text = getString(R.string.ai_info_add)
            setOnClickListener { addRow("", "") }
        })

        root.addView(Button(this).apply {
            text = getString(R.string.ai_save_profile)
            setOnClickListener { save() }
        })

        setContentView(ScrollView(this).apply {
            addView(root, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
    }

    private fun addRow(key: String, value: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        val keyField = cell(getString(R.string.ai_info_key_hint), key, 0.4f)
        val valueField = cell(getString(R.string.ai_info_value_hint), value, 0.6f)
        val remove = TextView(this).apply {
            text = "✕"
            textSize = 18f
            setTextColor(ContextCompat.getColor(this@AiSetupActivity, R.color.kb_text_dim))
            setPadding(dp(10), dp(8), dp(4), dp(8))
            isClickable = true
        }
        val model = Row(row, keyField, valueField)
        remove.setOnClickListener {
            rowsContainer.removeView(row)
            rows.remove(model)
        }
        row.addView(keyField)
        row.addView(valueField)
        row.addView(remove)
        rowsContainer.addView(row)
        rows.add(model)
    }

    private fun save() {
        val entries = rows.map { InfoEntry(it.key.text.toString().trim(), it.value.text.toString().trim()) }
            .filter { it.key.isNotEmpty() || it.value.isNotEmpty() }
        infoStore.save(entries)
        Toast.makeText(this, R.string.ai_profile_saved, Toast.LENGTH_SHORT).show()
    }

    // ---- tiny view helpers ----

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun header(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(ContextCompat.getColor(this@AiSetupActivity, R.color.kb_text))
        textSize = 18f
        setPadding(0, dp(8), 0, dp(4))
    }

    private fun caption(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(ContextCompat.getColor(this@AiSetupActivity, R.color.kb_text_dim))
        textSize = 13f
        setPadding(0, 0, 0, dp(12))
    }

    private fun cell(hint: String, value: String, weight: Float) = EditText(this).apply {
        this.hint = hint
        setText(value)
        setTextColor(ContextCompat.getColor(this@AiSetupActivity, R.color.kb_text))
        setHintTextColor(ContextCompat.getColor(this@AiSetupActivity, R.color.kb_text_dim))
        textSize = 15f
        maxLines = 2
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)
    }

    private companion object {
        /** Pre-seed an empty profile with a few common form fields to fill in. */
        val SUGGESTED_KEYS = listOf("Full name", "Email", "Phone", "Address")
    }
}
