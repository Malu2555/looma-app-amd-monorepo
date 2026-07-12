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
 * ReasoningActor — The thinker.
 * <p>
 * Reserved for deep analytical problems, architectural bottlenecks,
 * or multi-step logic. Routes queries to the high-efficiency reasoning
 * model (e.g. deepseek-r1) to handle complex thought paths.
 * <p>
 * Performs real LLM inference via {@link ModelHolder#call(String)}.
 */
public class ReasoningActor extends AbstractActor {

    private static final Logger log = LoggerFactory.getLogger(ReasoningActor.class);

    /** Token limit for reasoning responses — allows multi-step math. */
    private static final int MAX_TOKENS = 2048;

    private final ActorRef telemetryActor;

    public ReasoningActor(ActorRef telemetryActor) {
        this.telemetryActor = telemetryActor;
    }

    public static Props props(ActorRef telemetryActor) {
        return Props.create(ReasoningActor.class, () -> new ReasoningActor(telemetryActor));
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(TaskInput.class, this::onTask)
                .build();
    }

    private void onTask(TaskInput task) {
        long startNanos = System.nanoTime();
        log.info("ReasoningActor received task: id={}, prompt={}", task.task_id(),
                TextSanitizer.truncateToTokens(task.prompt(), 20));

        // 1. Sanitize the incoming prompt
        String cleanedPrompt = TextSanitizer.sanitizePrompt(task.prompt());

        // 2. Resolve the best model for this task from config (aligned with routing)
        String modelId = ModelResolver.resolveModel(TaskRouter.WorkerType.REASONING);
        log.debug("ReasoningActor resolved model: {}", modelId);

        // 3. Build a math/reasoning-focused system prompt
        String fullPrompt = buildReasoningPrompt(cleanedPrompt);

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
                "ReasoningActor",
                elapsedMs,
                estimatedTokens
        ), getSelf());

        // 7. Reply to supervisor
        getSender().tell(output, getSelf());

        log.info("ReasoningActor completed task in {}ms, output length={}", elapsedMs, cleanResponse.length());
    }

    /**
     * Builds a reasoning-oriented system prompt.
     * Detects whether the request is mathematical or logical/deductive
     * based on prompt keywords and tailors instructions accordingly.
     */
    private String buildReasoningPrompt(String userPrompt) {
        String lower = userPrompt.toLowerCase();

        // Logical / deductive reasoning keywords (constraint-based puzzles)
        boolean isLogical = lower.contains("puzzle") || lower.contains("constraint")
                || lower.contains("deduce") || lower.contains("deductive")
                || lower.contains("logic") || lower.contains("satisfy")
                || lower.contains("condition") || lower.contains("if then")
                || lower.contains("all must") || lower.contains("rule")
                || lower.contains("syllogism") || lower.contains("inference")
                || lower.contains("truth") || lower.contains("lie")
                || lower.contains("knight") || lower.contains("knave")
                || lower.contains("zebra") || lower.contains("einstein")
                || lower.contains("schedule") || lower.contains("arrange")
                || lower.contains("permutation") || lower.contains("combination");

        if (isLogical) {
            return "You are a logical/deductive reasoning AI. Solve constraint-based puzzles "
                    + "where all given conditions must be satisfied. Work through each constraint "
                    + "systematically and state the final solution clearly. "
                    + "Skip math formatting and avoid unnecessary exposition to save tokens. "
                    + "Show only essential reasoning steps and the final answer.\n"
                    + "Puzzle: " + userPrompt;
        }

        // Default: mathematical reasoning
        return "You are a mathematical reasoning AI. Solve multi-step arithmetic, percentages, "
                + "word problems, and projections. Respond with the final numerical answer in most cases. "
                + "Skip math formatting (no \\frac{}, \\sum, etc.) and avoid heavy calculation exposition "
                + "to save tokens. Show only essential steps and the final result.\n"
                + "Problem: " + userPrompt;
    }
}