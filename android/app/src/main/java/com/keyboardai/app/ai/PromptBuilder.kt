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

    fun systemPrompt(profile: UserProfile, memory: MemoryStore): String = buildString {
        append(PERSONA)

        if (!profile.isEmpty) {
            append("\n\nAbout the user:")
            if (profile.name.isNotBlank()) append("\n- Name: ${profile.name}")
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
