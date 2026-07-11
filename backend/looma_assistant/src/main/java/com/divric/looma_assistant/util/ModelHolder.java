package com.divric.looma_assistant.util;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

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
     * token-efficient responses from the model.
     *
     * @param prompt    the sanitized prompt text (typically includes system instructions)
     * @param maxTokens the maximum number of tokens to allow in the response
     * @return the model's response text
     */
    public static String call(String prompt, int maxTokens) {
        ChatModel model = getChatModel();
        return model.call(new Prompt(prompt)).getResult().getOutput().getText();
    }
}
