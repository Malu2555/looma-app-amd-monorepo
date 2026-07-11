package com.divric.looma_assistant.actor;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Terminated;
import com.divric.looma_assistant.dto.TaskInput;
import com.divric.looma_assistant.dto.TaskResultOutput;
import com.divric.looma_assistant.actor.telemetry.TelemetryBridgeActor;
import com.divric.looma_assistant.util.TaskRouter;
import com.divric.looma_assistant.actor.worker.CodeWorkerActor;
import com.divric.looma_assistant.actor.worker.FactualActor;
import com.divric.looma_assistant.actor.worker.LabelSentimentActor;
import com.divric.looma_assistant.actor.worker.ReasoningActor;
import com.divric.looma_assistant.actor.worker.StructuredTextActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * TaskSupervisorActor — the orchestrator / root actor.
 * <p>
 * Receives a batch of TaskInput items, routes each to the appropriate
 * specialized child worker based on task_id, tracks completions,
 * and aggregates the final List<TaskResultOutput>.
 * <p>
 * Uses the ask pattern: callers send a SubmitTasks message and the actor
 * replies to the sender with the aggregated results when all tasks complete.
 */
public class TaskSupervisorActor extends AbstractActor {

    private static final Logger log = LoggerFactory.getLogger(TaskSupervisorActor.class);

    // ── Inbound messages ──────────────────────────────────────────────

    /**
     * Submit a batch of tasks for processing.
     * The actor will reply to the sender with {@code List<TaskResultOutput>} when all tasks complete.
     */
    public static final class SubmitTasks {
        public final List<TaskInput> tasks;

        public SubmitTasks(List<TaskInput> tasks) {
            this.tasks = tasks;
        }
    }

    /**
     * Internal: result returned from a child worker.
     */
    public static final class TaskCompleted {
        public final TaskResultOutput output;

        public TaskCompleted(TaskResultOutput output) {
            this.output = output;
        }
    }

    // ── Actor state ───────────────────────────────────────────────────

    private final List<TaskResultOutput> results = new ArrayList<TaskResultOutput>();
    private int pendingCount = 0;
    private ActorRef replyTo;

    // Child actor references — one per specialized type (pooled/reusable)
    private ActorRef codeWorker;
    private ActorRef reasoningWorker;
    private ActorRef factualWorker;
    private ActorRef textWorker;
    private ActorRef labelWorker;
    private ActorRef telemetryActor;

    // ── Props factory ─────────────────────────────────────────────────

    public static Props props() {
        return Props.create(TaskSupervisorActor.class, TaskSupervisorActor::new);
    }

    // ── Lifecycle hooks ───────────────────────────────────────────────

    @Override
    public void preStart() {
        // Create the telemetry collector once
        telemetryActor = getContext().actorOf(
                TelemetryBridgeActor.props(),
                "telemetry-bridge"
        );

        // Create one instance of each specialized worker (pooled approach)
        codeWorker = getContext().actorOf(
                CodeWorkerActor.props(telemetryActor),
                "code-worker"
        );
        reasoningWorker = getContext().actorOf(
                ReasoningActor.props(telemetryActor),
                "reasoning-worker"
        );
        factualWorker = getContext().actorOf(
                FactualActor.props(telemetryActor),
                "factual-worker"
        );
        textWorker = getContext().actorOf(
                StructuredTextActor.props(telemetryActor),
                "text-worker"
        );
        labelWorker = getContext().actorOf(
                LabelSentimentActor.props(telemetryActor),
                "label-worker"
        );

        // Watch all children for crash detection
        getContext().watch(codeWorker);
        getContext().watch(reasoningWorker);
        getContext().watch(factualWorker);
        getContext().watch(textWorker);
        getContext().watch(labelWorker);

        log.info("TaskSupervisorActor initialized with 5 specialized workers + telemetry");
    }

    // ── Message handling ──────────────────────────────────────────────

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(SubmitTasks.class, this::onSubmitTasks)
                .match(TaskCompleted.class, this::onTaskCompleted)
                .match(Terminated.class, this::onTerminated)
                .build();
    }

    /**
     * Entry point: receives a batch of tasks, routes by task_id, tracks completions.
     * Replies to the sender when all tasks are complete.
     */
    private void onSubmitTasks(SubmitTasks msg) {
        this.replyTo = getSender();
        this.results.clear();
        this.pendingCount = msg.tasks.size();

        if (msg.tasks.isEmpty()) {
            replyTo.tell(new ArrayList<TaskResultOutput>(), getSelf());
            return;
        }

        log.info("Submitting {} tasks to specialized workers", msg.tasks.size());

        // Route each task to the correct specialized worker based on task type
        for (TaskInput task : msg.tasks) {
            ActorRef targetWorker = resolveWorker(task);
            // Workers receive the raw TaskInput directly (they handle TaskInput)
            targetWorker.tell(task, getSelf());
        }
    }

    /**
     * Routes a task to the correct specialized worker using the hybrid
     * TaskRouter (task_id keyword + dynamic prompt analysis).
     * Falls back to the text worker if the type is unrecognized.
     */
    private ActorRef resolveWorker(TaskInput task) {
        TaskRouter.WorkerType type = TaskRouter.resolveWorkerType(task);
        return switch (type) {
            case CODE -> codeWorker;
            case REASONING -> reasoningWorker;
            case FACTUAL -> factualWorker;
            case LABEL -> labelWorker;
            default -> textWorker; // TEXT (default: prose / summaries / chat)
        };
    }

    /**
     * Collects results from child workers.
     * When all pending tasks have completed, replies to the original sender.
     */
    private void onTaskCompleted(TaskCompleted msg) {
        results.add(msg.output);
        pendingCount--;

        if (pendingCount <= 0 && replyTo != null) {
            log.info("All {} tasks completed, aggregating results", results.size());
            replyTo.tell(new ArrayList<TaskResultOutput>(results), getSelf());
        }
    }

    /**
     * Handles worker termination (records a failure if a worker crashes mid-batch).
     */
    private void onTerminated(Terminated msg) {
        log.warn("Worker terminated unexpectedly: {}", msg.actor().path());
        if (pendingCount > 0) {
            results.add(new TaskResultOutput(
                    "unknown",
                    "Worker actor terminated unexpectedly: " + msg.actor().path()
            ));
            pendingCount--;

            if (pendingCount <= 0 && replyTo != null) {
                replyTo.tell(new ArrayList<TaskResultOutput>(results), getSelf());
            }
        }
    }
}