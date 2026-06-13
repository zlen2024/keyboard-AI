package com.keyboardai.app.ai

import android.graphics.BitmapFactory
import ai.liquid.leap.LeapClient
import ai.liquid.leap.ModelLoadingOptions
import ai.liquid.leap.ModelRunner
import ai.liquid.leap.message.ChatMessage
import ai.liquid.leap.message.ChatMessageContent
import ai.liquid.leap.message.ImageUtils
import ai.liquid.leap.message.MessageResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The single file that talks to the Liquid LEAP SDK. LEAP is llama.cpp-backed,
 * so we load the public Hugging Face GGUF weights directly (model + optional
 * mmproj for vision) via [LeapClient.loadModel] — no LEAP account or bundle
 * registry required.
 */
class LeapAiEngine : AiEngine {

    private var runner: ModelRunner? = null
    private val loadLock = Mutex()

    override val isLoaded: Boolean get() = runner != null

    override suspend fun load(
        modelPath: String,
        mmprojPath: String?,
        cpuThreads: Int,
        contextSize: Int,
    ) = loadLock.withLock {
        runner?.unload()
        runner = null
        val options = ModelLoadingOptions().apply {
            this.cpuThreads = cpuThreads
            this.contextSize = contextSize
        }
        runner = if (mmprojPath != null) {
            LeapClient.loadModel(modelPath, mmprojPath, options)
        } else {
            LeapClient.loadModel(modelPath, options)
        }
    }

    override suspend fun unload() = loadLock.withLock {
        runner?.unload()
        runner = null
    }

    override fun generate(
        systemPrompt: String,
        history: List<ChatTurn>,
        userText: String,
        imagePngPath: String?,
    ): Flow<String> = flow {
        val activeRunner = runner ?: error("Model not loaded")

        val seed = buildList {
            add(ChatMessage(ChatMessage.Role.SYSTEM, systemPrompt))
            history.forEach { turn ->
                val role = if (turn.fromUser) ChatMessage.Role.USER else ChatMessage.Role.ASSISTANT
                add(ChatMessage(role, turn.text))
            }
        }
        val conversation = activeRunner.createConversationFromHistory(seed)

        val responses = if (imagePngPath != null) {
            val bitmap = BitmapFactory.decodeFile(imagePngPath)
                ?: error("Could not read screenshot")
            val image = ImageUtils.fromBitmap(bitmap, IMAGE_MAX_LONG_SIDE)
            val message = ChatMessage(
                ChatMessage.Role.USER,
                listOf(image, ChatMessageContent.Text(userText)),
            )
            conversation.generateResponse(message)
        } else {
            conversation.generateResponse(userText)
        }

        responses.collect { response ->
            if (response is MessageResponse.Chunk) {
                emit(response.text)
            }
        }
    }

    private companion object {
        const val IMAGE_MAX_LONG_SIDE = 512
    }
}
