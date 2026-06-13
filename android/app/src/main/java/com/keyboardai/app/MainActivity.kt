package com.keyboardai.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = requireNotNull(findViewById(R.id.status_text))

        requireNotNull(findViewById<Button>(R.id.enable_button)).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        requireNotNull(findViewById<Button>(R.id.switch_button)).setOnClickListener {
            imm().showInputMethodPicker()
        }

        requireNotNull(findViewById<Button>(R.id.ai_setup_button)).setOnClickListener {
            startActivity(Intent(this, com.keyboardai.app.ui.AiSetupActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The input method picker is a system dialog, so returning from it
        // does not trigger onResume; refresh here too.
        if (hasFocus) updateStatus()
    }

    private fun updateStatus() {
        val enabled = imm().enabledInputMethodList.any { it.packageName == packageName }
        val selectedId = Settings.Secure.getString(
            contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD
        ) ?: ""
        val active = selectedId.startsWith("$packageName/")

        statusText.text = getString(
            when {
                active -> R.string.status_active
                enabled -> R.string.status_enabled
                else -> R.string.status_not_enabled
            }
        )
        statusText.setTextColor(
            ContextCompat.getColor(
                this,
                if (active) R.color.brand_primary else R.color.kb_text_dim
            )
        )
    }

    private fun imm() = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
}
