package com.keyboardai.app.ui

import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.keyboardai.app.R
import com.keyboardai.app.ai.ModelCatalog
import com.keyboardai.app.ai.ModelManager
import com.keyboardai.app.ai.ModelSpec
import com.keyboardai.app.ai.memory.ProfileStore
import com.keyboardai.app.ai.memory.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The "harness": where the user registers who they are and picks/downloads the
 * on-device model. Built programmatically to keep the resource set small.
 */
class AiSetupActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var profileStore: ProfileStore

    private lateinit var nameField: EditText
    private lateinit var emailField: EditText
    private lateinit var phoneField: EditText
    private lateinit var addressField: EditText
    private lateinit var organizationField: EditText
    private lateinit var occupationField: EditText
    private lateinit var toneField: EditText
    private lateinit var languagesField: EditText
    private lateinit var aboutField: EditText
    private lateinit var modelGroup: RadioGroup
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.ai_setup_title)
        profileStore = ProfileStore(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            setBackgroundColor(ContextCompat.getColor(this@AiSetupActivity, R.color.brand_surface))
        }

        root.addView(header(getString(R.string.ai_profile_header)))
        root.addView(caption(getString(R.string.ai_profile_caption)))
        val profile = profileStore.load()
        nameField = field(getString(R.string.ai_field_name), profile.name)
        emailField = field(getString(R.string.ai_field_email), profile.email, type = FieldType.EMAIL)
        phoneField = field(getString(R.string.ai_field_phone), profile.phone, type = FieldType.PHONE)
        addressField = field(getString(R.string.ai_field_address), profile.address, multiline = true)
        organizationField = field(getString(R.string.ai_field_organization), profile.organization)
        occupationField = field(getString(R.string.ai_field_occupation), profile.occupation)
        toneField = field(getString(R.string.ai_field_tone), profile.tone)
        languagesField = field(getString(R.string.ai_field_languages), profile.languages)
        aboutField = field(getString(R.string.ai_field_about), profile.about, multiline = true)
        listOf(
            nameField, emailField, phoneField, addressField, organizationField,
            occupationField, toneField, languagesField, aboutField,
        ).forEach { root.addView(it) }

        root.addView(Button(this).apply {
            text = getString(R.string.ai_save_profile)
            setOnClickListener { saveProfile() }
        })

        root.addView(header(getString(R.string.ai_model_header)))
        root.addView(caption(getString(R.string.ai_model_caption)))
        modelGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val selected = ModelManager.selectedSpec(this)
        ModelCatalog.all.forEachIndexed { index, spec ->
            modelGroup.addView(RadioButton(this).apply {
                id = index + 1
                text = "${spec.displayName}\n${spec.description}"
                setTextColor(ContextCompat.getColor(this@AiSetupActivity, R.color.kb_text))
                isChecked = spec.id == selected.id
            })
        }
        root.addView(modelGroup)

        statusText = caption("")
        root.addView(statusText)

        root.addView(Button(this).apply {
            text = getString(R.string.ai_download_model)
            setOnClickListener { downloadSelected() }
        })

        setContentView(ScrollView(this).apply {
            addView(root, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })

        observeState()
    }

    private fun selectedSpecFromUi(): ModelSpec {
        val index = (modelGroup.checkedRadioButtonId - 1).coerceIn(0, ModelCatalog.all.lastIndex)
        return ModelCatalog.all[index]
    }

    private fun saveProfile() {
        profileStore.save(
            UserProfile(
                name = nameField.text.toString().trim(),
                email = emailField.text.toString().trim(),
                phone = phoneField.text.toString().trim(),
                address = addressField.text.toString().trim(),
                organization = organizationField.text.toString().trim(),
                occupation = occupationField.text.toString().trim(),
                tone = toneField.text.toString().trim(),
                languages = languagesField.text.toString().trim(),
                about = aboutField.text.toString().trim(),
            )
        )
        Toast.makeText(this, R.string.ai_profile_saved, Toast.LENGTH_SHORT).show()
    }

    private fun downloadSelected() {
        val spec = selectedSpecFromUi()
        scope.launch {
            runCatching { ModelManager.switchTo(this@AiSetupActivity, spec) }
                .onFailure { statusText.text = getString(R.string.ai_model_error, it.message ?: "failed") }
        }
    }

    private fun observeState() {
        scope.launch {
            ModelManager.state.collect { st ->
                statusText.text = when (st) {
                    is ModelManager.State.Downloading ->
                        getString(R.string.ai_downloading, (st.fraction * 100).toInt())
                    is ModelManager.State.Unpacking ->
                        getString(R.string.ai_unpacking, (st.fraction * 100).toInt())
                    ModelManager.State.Loading -> getString(R.string.ai_loading)
                    ModelManager.State.Ready -> getString(R.string.ai_ready)
                    is ModelManager.State.Error -> getString(R.string.ai_model_error, st.message)
                    ModelManager.State.Idle -> {
                        val spec = ModelManager.selectedSpec(this@AiSetupActivity)
                        if (ModelManager.isAvailableOffline(this@AiSetupActivity, spec)) {
                            getString(R.string.ai_offline_ready)
                        } else ""
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    // ---- tiny view helpers ----

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun header(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(ContextCompat.getColor(this@AiSetupActivity, R.color.kb_text))
        textSize = 18f
        setPadding(0, dp(20), 0, dp(4))
    }

    private fun caption(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(ContextCompat.getColor(this@AiSetupActivity, R.color.kb_text_dim))
        textSize = 13f
        setPadding(0, 0, 0, dp(8))
    }

    private enum class FieldType { TEXT, EMAIL, PHONE }

    private fun field(
        hint: String,
        value: String,
        multiline: Boolean = false,
        type: FieldType = FieldType.TEXT,
    ) = EditText(this).apply {
        this.hint = hint
        setText(value)
        setTextColor(ContextCompat.getColor(this@AiSetupActivity, R.color.kb_text))
        setHintTextColor(ContextCompat.getColor(this@AiSetupActivity, R.color.kb_text_dim))
        inputType = when {
            type == FieldType.EMAIL ->
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            type == FieldType.PHONE -> InputType.TYPE_CLASS_PHONE
            multiline ->
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        if (multiline) minLines = 2
    }
}
