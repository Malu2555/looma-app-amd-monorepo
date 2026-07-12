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
 * FactualActor — The speedster.
 * <p>
 * Handles lookup tasks, structured data returns, or definitive
 * historical/system questions. Points to a lightweight, blazing fast
 * and ultra cheap model to minimize API spend (e.g. llama-3.2-3b-instruct).
 * <p>
 * Performs real LLM inference via {@link ModelHolder#call(String)}.
 */
public class FactualActor extends AbstractActor {

    private static final Logger log = LoggerFactory.getLogger(FactualActor.class);

    /** Token limit for factual responses — short and concise. */
    private static final int MAX_TOKENS = 512;

    private final ActorRef telemetryActor;

    public FactualActor(ActorRef telemetryActor) {
        this.telemetryActor = telemetryActor;
    }

    public static Props props(ActorRef telemetryActor) {
        return Props.create(FactualActor.class, () -> new FactualActor(telemetryActor));
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(TaskInput.class, this::onTask)
                .build();
    }

    private void onTask(TaskInput task) {
        long startNanos = System.nanoTime();
        log.info("FactualActor received task: id={}, prompt={}", task.task_id(),
                TextSanitizer.truncateToTokens(task.prompt(), 20));

        // 1. Sanitize the incoming prompt (minimal — the model is cheap anyway)
        String cleanedPrompt = TextSanitizer.sanitizePrompt(task.prompt());

        // 2. Resolve the best model for this task from config (aligned with routing)
        String modelId = ModelResolver.resolveModel(TaskRouter.WorkerType.FACTUAL);
        log.debug("FactualActor resolved model: {}", modelId);

        // 3. Build a concise, factual prompt
        String fullPrompt = buildFactualPrompt(cleanedPrompt);

        // 4. Perform real LLM inference via ModelHolder with token limit
        String rawResponse = ModelHolder.call(fullPrompt, MAX_TOKENS, modelId);

        // 4. Sanitize the response
        String cleanResponse = TextSanitizer.sanitizeResponse(rawResponse);

        // 5. Build output DTO
        TaskResultOutput output = new TaskResultOutput(
                task.task_id(),
                cleanResponse
        );

        // 6. Send telemetry
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        int estimatedTokens = cleanResponse.length() / 4;
        telemetryActor.tell(new TelemetryBridgeActor.TelemetryEvent(
                "FactualActor",
                elapsedMs,
                estimatedTokens
        ), getSelf());

        // 7. Reply to supervisor
        getSender().tell(output, getSelf());

        log.info("FactualActor completed task in {}ms, output length={}", elapsedMs, cleanResponse.length());
    }

    /** Builds a concise system prompt optimized for factual lookups. */
    private String buildFactualPrompt(String userPrompt) {
        return "Answer concisely with precise factual information. "
                + "If uncertain, state that the information is not available.\n"
                + "Query: " + userPrompt;
    }
}