package com.keyboardai.app.ai

import kotlinx.coroutines.flow.Flow

/** One prior conversation turn fed back to the model as context. */
data class ChatTurn(val fromUser: Boolean, val text: String)

/**
 * Abstraction over the on-device model so the keyboard and the rest of the app
 * never depend on a specific runtime. The only implementation today is
 * [LeapAiEngine]; this seam also makes a llama.cpp fallback drop-in.
 */
interface AiEngine {

    val isLoaded: Boolean

    /** Loads a model (and optional multimodal projector) from local GGUF files. */
    suspend fun load(modelPath: String, mmprojPath: String?, cpuThreads: Int, contextSize: Int)

    suspend fun unload()

    /**
     * Streams the assistant's reply token-by-token.
     *
     * @param systemPrompt persona + profile + memory, assembled by [PromptBuilder]
     * @param history prior turns of this session
     * @param userText the new user prompt
     * @param imagePngPath optional screenshot to reason over (requires a multimodal model)
     */
    fun generate(
        systemPrompt: String,
        history: List<ChatTurn>,
        userText: String,
        imagePngPath: String?,
    ): Flow<String>
}
