package com.keyboardai.app.ai

import com.keyboardai.app.ai.memory.InfoEntry
import com.keyboardai.app.ai.memory.MemoryStore

/**
 * Assembles the system prompt the model sees on every request: a fixed persona
 * plus the user's free-form key/value info and the global/daily memory tiers.
 * Every request also carries a screenshot of the user's screen, so the persona
 * tells the model to ground its answer in what it sees.
 */
object PromptBuilder {

    private const val PERSONA =
        "You are Keyboard AI, a concise on-device assistant living in the user's " +
            "keyboard. A screenshot of the user's current screen is attached to every " +
            "message — read it to understand the context (the app, the form, the field " +
            "or conversation the user is in). You write text the user will send in other " +
            "apps, so reply with only the requested text — no preamble, no quotes, no " +
            "markdown unless asked. Match the user's language and tone."

    private const val FORM_FILL_PERSONA =
        "You are Keyboard AI's form autofill. A screenshot of the user's screen is " +
            "attached. Identify the form field the user is currently editing (usually the " +
            "focused/empty input) and the question or label next to it, then reply with " +
            "ONLY the exact value to type into that one field — no label, no quotes, no " +
            "explanation, no trailing punctuation. If the answer is a known fact about the " +
            "user (from their saved info below), output it verbatim. If it is an open " +
            "question (feedback, a reason, a comment), write a short plausible answer in " +
            "the user's voice. If you genuinely cannot tell, reply with exactly: (unknown)"

    /** General assistant prompt (the ✨ prompt bar). */
    fun systemPrompt(info: List<InfoEntry>, memory: MemoryStore): String = buildString {
        append(PERSONA)
        appendUserContext(info, memory)
    }

    /** Specialized prompt for one-tap form autofill (the 📝 action). */
    fun formFillPrompt(info: List<InfoEntry>, memory: MemoryStore): String = buildString {
        append(FORM_FILL_PERSONA)
        appendUserContext(info, memory)
    }

    private fun StringBuilder.appendUserContext(info: List<InfoEntry>, memory: MemoryStore) {
        val facts = info.filter { it.key.isNotBlank() || it.value.isNotBlank() }
        if (facts.isNotEmpty()) {
            append("\n\nWhat the user told you about themselves:")
            facts.forEach { entry ->
                when {
                    entry.key.isBlank() -> append("\n- ${entry.value}")
                    entry.value.isBlank() -> append("\n- ${entry.key}")
                    else -> append("\n- ${entry.key}: ${entry.value}")
                }
            }
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
