package com.keyboardai.app.ai

import com.keyboardai.app.ai.memory.MemoryStore
import com.keyboardai.app.ai.memory.UserProfile

/**
 * Assembles the system prompt the model sees on every request: a fixed persona
 * plus the user's profile and the global/daily memory tiers. Kept compact on
 * purpose — short context means faster first-token on-device.
 */
object PromptBuilder {

    private const val PERSONA =
        "You are Keyboard AI, a concise on-device writing assistant living in the " +
            "user's keyboard. You write text the user will send in other apps, so " +
            "reply with only the requested text — no preamble, no quotes, no markdown " +
            "unless asked. Match the user's language and the requested tone. When a " +
            "screenshot is provided, ground your answer in what it shows."

    private const val FORM_FILL_PERSONA =
        "You are Keyboard AI's form autofill. You help the user complete a form " +
            "field using the facts they have saved about themselves. You are given " +
            "the field's question/label (and sometimes a screenshot of the form). " +
            "Reply with ONLY the exact value to type into that one field — no label, " +
            "no quotes, no explanation, no trailing punctuation. If the answer is a " +
            "known fact about the user (name, email, phone, address, etc.), output it " +
            "verbatim. If it is an open question (e.g. feedback, a reason, a comment), " +
            "write a short, plausible answer in the user's voice and tone. If you " +
            "genuinely cannot tell what to put, reply with exactly: (unknown)"

    /** General writing assistant prompt (the ✨ prompt bar). */
    fun systemPrompt(profile: UserProfile, memory: MemoryStore): String = buildString {
        append(PERSONA)
        appendUserContext(profile, memory)
    }

    /** Specialized prompt for one-tap form autofill (the 📝 action). */
    fun formFillPrompt(profile: UserProfile, memory: MemoryStore): String = buildString {
        append(FORM_FILL_PERSONA)
        appendUserContext(profile, memory)
    }

    private fun StringBuilder.appendUserContext(profile: UserProfile, memory: MemoryStore) {
        if (!profile.isEmpty) {
            append("\n\nAbout the user:")
            if (profile.name.isNotBlank()) append("\n- Name: ${profile.name}")
            if (profile.email.isNotBlank()) append("\n- Email: ${profile.email}")
            if (profile.phone.isNotBlank()) append("\n- Phone: ${profile.phone}")
            if (profile.address.isNotBlank()) append("\n- Address: ${profile.address}")
            if (profile.organization.isNotBlank()) append("\n- Organization: ${profile.organization}")
            if (profile.occupation.isNotBlank()) append("\n- Occupation: ${profile.occupation}")
            if (profile.tone.isNotBlank()) append("\n- Preferred tone: ${profile.tone}")
            if (profile.languages.isNotBlank()) append("\n- Languages: ${profile.languages}")
            if (profile.about.isNotBlank()) append("\n- Notes: ${profile.about}")
        }

        val global = memory.globalFacts()
        if (global.isNotEmpty()) {
            append("\n\nLong-term memory:")
            global.forEach { append("\n- $it") }
        }

        val daily = memory.todayFacts()
        if (daily.isNotEmpty()) {
            append("\n\nEarlier today:")
            daily.forEach { append("\n- $it") }
        }
    }
}
