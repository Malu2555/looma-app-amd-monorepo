package com.divric.looma_assistant.controllers;

import com.divric.looma_assistant.dto.TaskInput;
import com.divric.looma_assistant.dto.TaskResultOutput;
import com.divric.looma_assistant.services.ActorBridgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * AgentRestController — main REST endpoint for the Vue.js chatbot frontend.
 * <p>
 * Handles both batch task submission and single conversational ("chat")
 * requests in one controller. All requests are routed through the actor
 * system via {@link ActorBridgeService} using the non-blocking ask pattern.
 */
@RestController
@RequestMapping("/api/agent")
public class AgentRestController {

    private static final Logger log = LoggerFactory.getLogger(AgentRestController.class);

    private final ActorBridgeService actorBridgeService;

    public AgentRestController(ActorBridgeService actorBridgeService) {
        this.actorBridgeService = actorBridgeService;
    }

    /**
     * POST /api/agent/batch
     * <p>
     * Submit a batch of tasks (JSON array of TaskInput) for processing.
     * Returns the aggregated results once all workers complete.
     */
    @PostMapping("/batch")
    public CompletionStage<ResponseEntity<List<TaskResultOutput>>> submitBatch(
            @RequestBody List<TaskInput> tasks) {

        log.info("Received batch request with {} tasks", tasks.size());
        return actorBridgeService.submitTasks(tasks)
                .thenApply(ResponseEntity::ok);
    }

    /**
     * POST /api/agent/chat
     * <p>
     * Single conversational request. The frontend sends a chat message and
     * the controller wraps it as a "text" task, routes it through the actor
     * system, and returns the first result. This is the chatbot entry point.
     */
    @PostMapping("/chat")
    public CompletionStage<ResponseEntity<TaskResultOutput>> chat(
            @RequestBody ChatRequest request) {

        log.info("Received chat request: '{}'", truncate(request.message(), 80));
        TaskInput task = new TaskInput(
                request.task_id() != null ? request.task_id() : "text",
                request.message()
        );

        return actorBridgeService.submitSingleTask(task)
                .thenApply(results -> {
                    TaskResultOutput first = results.isEmpty() ? null : results.get(0);
                    return ResponseEntity.ok(first);
                });
    }

    /**
     * GET /api/agent/health
     * <p>
     * Simple liveness probe for the frontend to check connectivity.
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("{\"status\":\"UP\"}");
    }

    // ── Request record ────────────────────────────────────────────────

    /**
     * Inbound chat payload from the Vue frontend.
     *
     * @param task_id the unique identifier for the task (optional, defaults to "text")
     * @param message the user's prompt / chat text
     */
    public record ChatRequest(
            String task_id,
            String message
    ) {}

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}