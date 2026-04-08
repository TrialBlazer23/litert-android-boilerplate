/**
 * LmConversationConfig.kt — Configuration and message types for LmConversation.
 *
 * Provides a module-local abstraction over ConversationConfig / SamplerConfig so
 * callers do not need to import com.google.ai.edge.litertlm.* directly.
 * The translation to LiteRT-LM types is performed inside LmEngineWrapper.createConversation().
 *
 * Part of litert-android-boilerplate by Necessity Labs
 * Created: 2025-05-02
 */
package com.necessitylabs.litert.lm.conversation

/**
 * Role of a participant in a conversation.
 *
 * Maps directly to [com.google.ai.edge.litertlm.Message.user] and
 * [com.google.ai.edge.litertlm.Message.model] factory functions.
 */
enum class LmRole {
    /** Human turn — converted to Message.user() in the LiteRT-LM API. */
    USER,

    /** Model turn — converted to Message.model() in the LiteRT-LM API. */
    MODEL,
}

/**
 * A single message in a conversation history.
 *
 * @property role The speaker of this message.
 * @property text Plain-text content. For multimodal content, use
 *               [LmConversation.sendMessageStream] with a [com.google.ai.edge.litertlm.Contents]
 *               object directly.
 */
data class LmMessage(
    val role: LmRole,
    val text: String,
)

/**
 * All parameters that configure a new [LmConversation] session.
 *
 * Defaults match the recommended values from the LiteRT-LM documentation and
 * produce balanced, creative output without excessive repetition.
 *
 * @property systemInstruction Optional system prompt injected before the first user turn.
 *                             When null, the model's built-in default system prompt is used.
 * @property topK              Number of highest-probability tokens to sample from at each step.
 *                             Lower values make output more focused; higher values more diverse.
 *                             Range: 1–100. Default: 40.
 * @property topP              Cumulative probability threshold for nucleus sampling.
 *                             Tokens whose cumulative probability exceeds this value are excluded.
 *                             Range: 0.0–1.0. Default: 0.95.
 * @property temperature       Softmax temperature applied to logits before sampling.
 *                             Values below 1.0 reduce randomness; above 1.0 increase it.
 *                             Range: 0.0–2.0. Default: 0.8.
 * @property initialMessages   Optional list of prior messages injected as conversation history.
 *                             Useful for restoring a previous session or providing few-shot
 *                             examples.
 */
data class LmConversationConfig(
    val systemInstruction: String? = null,
    val topK: Int = 40,
    val topP: Double = 0.95,
    val temperature: Double = 0.8,
    val initialMessages: List<LmMessage> = emptyList(),
) {
    /**
     * Validates sampler parameter ranges before the config is passed to the LiteRT-LM API.
     *
     * @throws IllegalArgumentException if any parameter is outside its valid range.
     */
    fun validate() {
        require(topK in 1..100) { "topK must be in 1..100, was $topK." }
        require(topP in 0.0..1.0) { "topP must be in 0.0..1.0, was $topP." }
        require(temperature in 0.0..2.0) { "temperature must be in 0.0..2.0, was $temperature." }
    }
}
