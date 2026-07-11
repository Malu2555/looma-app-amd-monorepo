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
 * StructuredTextActor — The writer.
 * <p>
 * Dedicated to standard prose, document summaries, or casual chatbot
 * interactions. Balances speed and conversational capacity cleanly.
 * Dynamically resolves the best model via {@link ModelResolver#resolveModel(String)}
 * based on the task type and configured allowed-models — no hardcoded model IDs.
 * <p>
 * Performs real LLM inference via {@link ModelHolder#call(String)}.
 */
public class StructuredTextActor extends AbstractActor {

    private static final Logger log = LoggerFactory.getLogger(StructuredTextActor.class);

    /** Token limit for prose responses — balanced for readability. */
    private static final int MAX_TOKENS = 1024;

    private final ActorRef telemetryActor;

    public StructuredTextActor(ActorRef telemetryActor) {
        this.telemetryActor = telemetryActor;
    }

    public static Props props(ActorRef telemetryActor) {
        return Props.create(StructuredTextActor.class, () -> new StructuredTextActor(telemetryActor));
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(TaskInput.class, this::onTask)
                .build();
    }

    private void onTask(TaskInput task) {
        long startNanos = System.nanoTime();
        log.info("StructuredTextActor received task: id={}, prompt={}", task.task_id(),
                TextSanitizer.truncateToTokens(task.prompt(), 20));

        // 1. Sanitize the incoming prompt
        String cleanedPrompt = TextSanitizer.sanitizePrompt(task.prompt());

        // 2. Resolve the best model for this task from config (aligned with routing)
        String modelId = ModelResolver.resolveModel(TaskRouter.WorkerType.TEXT);
        log.debug("StructuredTextActor resolved model: {}", modelId);

        // 3. Build a prose-optimized system prompt
        String fullPrompt = buildProsePrompt(cleanedPrompt);

        // 4. Perform real LLM inference via ModelHolder with token limit
        String rawResponse = ModelHolder.call(fullPrompt, MAX_TOKENS);

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
                "StructuredTextActor",
                elapsedMs,
                estimatedTokens
        ), getSelf());

        // 8. Reply to supervisor
        getSender().tell(output, getSelf());

        log.info("StructuredTextActor completed task in {}ms, output length={}", elapsedMs, cleanResponse.length());
    }

    /** Builds a system prompt optimized for natural prose and summarization. */
    private String buildProsePrompt(String userPrompt) {
        return "You are a helpful writing assistant. Respond with clear, well-structured prose. "
                + "Use appropriate formatting for readability.\n"
                + "User request: " + userPrompt;
    }
}