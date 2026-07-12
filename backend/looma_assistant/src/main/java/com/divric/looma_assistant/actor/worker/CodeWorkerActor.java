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
 * CodeWorkerActor — The specialist.
 * <p>
 * Explicitly targets code generation, parsing, architectural prompts,
 * and debugging. Utilizes the heavy-duty code-instruct model variant
 * from Fireworks AI (e.g. deepseek-coder-33b-instruct).
 * <p>
 * Performs real LLM inference via {@link ModelHolder#call(String)}.
 */
public class CodeWorkerActor extends AbstractActor {

    private static final Logger log = LoggerFactory.getLogger(CodeWorkerActor.class);

    /** Token limit for code responses — allows full code blocks. */
    private static final int MAX_TOKENS = 2048;

    private final ActorRef telemetryActor;

    public CodeWorkerActor(ActorRef telemetryActor) {
        this.telemetryActor = telemetryActor;
    }

    public static Props props(ActorRef telemetryActor) {
        return Props.create(CodeWorkerActor.class, () -> new CodeWorkerActor(telemetryActor));
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(TaskInput.class, this::onTask)
                .build();
    }

    private void onTask(TaskInput task) {
        long startNanos = System.nanoTime();
        log.info("CodeWorkerActor received task: id={}, prompt={}", task.task_id(),
                TextSanitizer.truncateToTokens(task.prompt(), 20));

        // 1. Sanitize the incoming prompt
        String cleanedPrompt = TextSanitizer.sanitizePrompt(task.prompt());

        // 2. Resolve the best model for this task from config (aligned with routing)
        String modelId = ModelResolver.resolveModel(TaskRouter.WorkerType.CODE);
        log.debug("CodeWorkerActor resolved model: {}", modelId);

        // 3. Build the code-specific system prompt
        String fullPrompt = buildCodePrompt(cleanedPrompt);

        // 4. Perform real LLM inference via ModelHolder with token limit
        String rawResponse = ModelHolder.call(fullPrompt, MAX_TOKENS, modelId);

        // 4. Sanitize the LLM response
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
                "CodeWorkerActor",
                elapsedMs,
                estimatedTokens
        ), getSelf());

        // 7. Reply to supervisor
        getSender().tell(output, getSelf());

        log.info("CodeWorkerActor completed task in {}ms, output length={}", elapsedMs, cleanResponse.length());
    }

    /** Builds a code-oriented system prompt for the code-instruct model. */
    private String buildCodePrompt(String userPrompt) {
        return "You are an expert code generation AI. Respond with clean, well-documented code only.\n"
                + "User request: " + userPrompt;
    }
}