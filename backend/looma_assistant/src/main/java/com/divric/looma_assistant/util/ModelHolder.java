package com.divric.looma_assistant.util;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * ModelHolder — a static bridge that holds the auto-configured
 * {@link ChatModel} reference so that Akka actors (which are
 * not Spring-managed beans) can perform real LLM inference.
 * <p>
 * The reference is set once during Spring context initialization
 * (from {@code ActorBridgeService}) and read by all specialized workers.
 */
public final class ModelHolder {

    private static ChatModel chatModel;

    private ModelHolder() {
        // Utility class — no instantiation
    }

    /**
     *initializes the holder with the auto-configured chat model.
     * Called once from {@code ActorBridgeService} after the actor system starts.
     *
     * @param model the auto-configured ChatModel bean (e.g. OpenAiChatModel)
     */
    public static void setChatModel(ChatModel model) {
        chatModel = model;
    }

    /**
     * Returns the held chat model reference.
     *
     * @throws IllegalStateException if the model has not been initialized yet
     */
    public static ChatModel getChatModel() {
        if (chatModel == null) {
            throw new IllegalStateException(
                    "ModelHolder has not been initialized. Ensure ActorBridgeService calls setChatModel() on startup."
            );
        }
        return chatModel;
    }

    /**
     * Convenience method: performs a synchronous chat call with the given
     * prompt text, returning the response.
     * <p>
     * Each worker calls this with its own system-prompt-enhanced message.
     * Uses the Spring AI default model (no explicit model override).
     *
     * @param prompt the sanitized prompt text (typically includes system instructions)
     * @return the model's response text
     */
    public static String call(String prompt) {
        ChatModel model = getChatModel();
        return model.call(new Prompt(prompt)).getResult().getOutput().getText();
    }

    /**
     * Convenience method: performs a synchronous chat call with the given
     * prompt text and a token limit for the response.
     * <p>
     * Uses {@code maxTokens} to constrain generation length, promoting
     * token-efficient responses from the model. Uses the Spring AI default model.
     *
     * @param prompt    the sanitized prompt text (typically includes system instructions)
     * @param maxTokens the maximum number of tokens to allow in the response
     * @return the model's response text
     */
    public static String call(String prompt, int maxTokens) {
        return call(prompt, maxTokens, null);
    }

    /**
     * Convenience method: performs a synchronous chat call with the given
     * prompt text, a token limit, and an explicit model identifier.
     * <p>
     * The {@code modelId} is passed through {@link ChatOptions} so the call
     * targets the model resolved by {@link ModelResolver} rather than the
     * Spring AI default (which would otherwise be {@code gpt-4o}). When
     * {@code modelId} is {@code null} or blank, the default model is used.
     *
     * @param prompt    the sanitized prompt text (typically includes system instructions)
     * @param maxTokens the maximum number of tokens to allow in the response
     * @param modelId   the resolved model identifier (e.g. a Fireworks/Google model), or null
     * @return the model's response text
     */
    public static String call(String prompt, int maxTokens, String modelId) {
        ChatModel model = getChatModel();
        // Build a Prompt with max-token constraint (and model override) via OpenAiChatOptions.
        // Using the concrete OpenAiChatOptions (not the generic ChatOptions) guarantees the
        // model override is reliably honored by OpenAiChatModel / the Google OpenAI-compatible endpoint.
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .maxTokens(maxTokens)
                .model(modelId != null && !modelId.isBlank() ? modelId : null)
                .build();
        Prompt promptObj = new Prompt(prompt, options);
        return model.call(promptObj).getResult().getOutput().getText();
    }
}
