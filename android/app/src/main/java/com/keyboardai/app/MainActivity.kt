package com.keyboardai.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.keyboardai.app.ai.ModelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var statusText: TextView
    private lateinit var modelStatusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = requireNotNull(findViewById(R.id.status_text))
        modelStatusText = requireNotNull(findViewById(R.id.model_status_text))

        requireNotNull(findViewById<Button>(R.id.enable_button)).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        requireNotNull(findViewById<Button>(R.id.switch_button)).setOnClickListener {
            imm().showInputMethodPicker()
        }

        requireNotNull(findViewById<Button>(R.id.ai_setup_button)).setOnClickListener {
            startActivity(Intent(this, com.keyboardai.app.ui.AiSetupActivity::class.java))
        }

        // Tap the model line to retry if a download failed.
        modelStatusText.setOnClickListener { startModelDownload() }

        observeModelState()
        startModelDownload()
    }

    /** Auto-download the (single) model on first launch so it's ready when needed. */
    private fun startModelDownload() {
        ModelManager.startPreparing(applicationContext)
    }

    private fun observeModelState() {
        scope.launch {
            ModelManager.state.collect { st ->
                modelStatusText.text = when (st) {
                    is ModelManager.State.Downloading ->
                        getString(R.string.ai_downloading, (st.fraction * 100).toInt())
                    is ModelManager.State.Unpacking ->
                        getString(R.string.ai_unpacking, (st.fraction * 100).toInt())
                    ModelManager.State.Loading -> getString(R.string.ai_loading)
                    ModelManager.State.Ready -> getString(R.string.ai_ready)
                    is ModelManager.State.Error ->
                        getString(R.string.ai_model_error_retry, st.message)
                    ModelManager.State.Idle ->
                        if (ModelManager.isReadyOnDisk(this@MainActivity)) {
                            getString(R.string.ai_ready)
                        } else {
                            getString(R.string.ai_model_preparing)
                        }
                }
            }
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

    override fun onDestroy() {
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    private fun imm() = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
}
