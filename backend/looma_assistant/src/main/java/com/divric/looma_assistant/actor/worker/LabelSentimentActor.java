package com.divric.looma_assistant.actor.worker;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import com.divric.looma_assistant.dto.TaskInput;
import com.divric.looma_assistant.dto.TaskResultOutput;
import com.divric.looma_assistant.actor.telemetry.TelemetryBridgeActor;
import com.divric.looma_assistant.util.ModelHolder;
import com.divric.looma_assistant.util.ModelResolver;
import com.divric.looma_assistant.util.TaskRouter;
import com.divric.looma_assistant.util.TextSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LabelSentimentActor — The labeler.
 * <p>
 * Handles two classification tasks efficiently in a single actor:
 * <ul>
 *   <li><b>Sentiment classification</b> — labels text as positive, negative, or neutral
 *       with a brief one-line justification. No chain-of-thought or reasoning steps.</li>
 *   <li><b>Named Entity Recognition (NER)</b> — extracts and labels entities such as
 *       Person, Organization, Location, Date, Event, Product, etc.</li>
 * </ul>
 * <p>
 * Uses a lightweight, fast model to minimize token spend. Responses are concise
 * labels with minimal verbosity.
 */
public class LabelSentimentActor extends AbstractActor {

    private static final Logger log = LoggerFactory.getLogger(LabelSentimentActor.class);

    /** Token limit for label/sentiment responses — short and concise. */
    private static final int MAX_TOKENS = 256;

    private final ActorRef telemetryActor;

    public LabelSentimentActor(ActorRef telemetryActor) {
        this.telemetryActor = telemetryActor;
    }

    public static Props props(ActorRef telemetryActor) {
        return Props.create(LabelSentimentActor.class, () -> new LabelSentimentActor(telemetryActor));
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(TaskInput.class, this::onTask)
                .build();
    }

    private void onTask(TaskInput task) {
        long startNanos = System.nanoTime();
        log.info("LabelSentimentActor received task: id={}, prompt={}", task.task_id(),
                TextSanitizer.truncateToTokens(task.prompt(), 20));

        // 1. Sanitize the incoming prompt
        String cleanedPrompt = TextSanitizer.sanitizePrompt(task.prompt());

        // 2. Resolve the best model for this task from config
        String modelId = ModelResolver.resolveModel(TaskRouter.WorkerType.LABEL);
        log.debug("LabelSentimentActor resolved model: {}", modelId);

        // 3. Build a label/sentiment-focused system prompt
        String fullPrompt = buildLabelPrompt(cleanedPrompt);

        // 4. Perform real LLM inference via ModelHolder with token limit
        String rawResponse = ModelHolder.call(fullPrompt, MAX_TOKENS, modelId);

        // 5. Sanitize the response
        String cleanResponse = TextSanitizer.sanitizeResponse(rawResponse);

        // 6. Build output DTO
        TaskResultOutput output = new TaskResultOutput(
                task.task_id(),
                cleanResponse
        );

        // 7. Send telemetry
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        int estimatedTokens = cleanResponse.length() / 4;
        telemetryActor.tell(new TelemetryBridgeActor.TelemetryEvent(
                "LabelSentimentActor",
                elapsedMs,
                estimatedTokens
        ), getSelf());

        // 8. Reply to supervisor
        getSender().tell(output, getSelf());

        log.info("LabelSentimentActor completed task in {}ms, output length={}", elapsedMs, cleanResponse.length());
    }

    /**
     * Builds a system prompt optimized for classification and entity extraction.
     * Detects whether the request is sentiment or NER based on prompt keywords
     * and tailors the instructions accordingly.
     */
    private String buildLabelPrompt(String userPrompt) {
        String lower = userPrompt.toLowerCase();

        // Sentiment classification keywords
        boolean isSentiment = lower.contains("sentiment") || lower.contains("positive")
                || lower.contains("negative") || lower.contains("neutral")
                || lower.contains("feeling") || lower.contains("emotion")
                || lower.contains("tone") || lower.contains("opinion")
                || lower.contains("attitude") || lower.contains("polarity");

        // NER / entity extraction keywords
        boolean isNer = lower.contains("entity") || lower.contains("ner")
                || lower.contains("extract") || lower.contains("person")
                || lower.contains("organization") || lower.contains("location")
                || lower.contains("date") || lower.contains("named")
                || lower.contains("label") || lower.contains("classify");

        if (isSentiment && !isNer) {
            return "You are a sentiment classifier. Respond with EXACTLY one of: "
                    + "POSITIVE, NEGATIVE, or NEUTRAL followed by a single brief justification sentence. "
                    + "No thinking, no reasoning steps, no extra text.\n"
                    + "Text: " + userPrompt;
        }

        if (isNer) {
            return "You are a named entity recognizer. Extract all named entities from the text "
                    + "and label each with its type (Person, Organization, Location, Date, Event, Product, etc.). "
                    + "Format as a simple list: \"EntityName (Type)\". "
                    + "No thinking, no reasoning, no extra text.\n"
                    + "Text: " + userPrompt;
        }

        // Default: treat as general classification/labeling
        return "You are a text classifier. Analyze the input and provide a concise label or classification. "
                + "Respond with just the label and a brief justification. No thinking or reasoning steps.\n"
                + "Input: " + userPrompt;
    }
}