package com.divric.looma_assistant.engine;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.pattern.Patterns;
import com.divric.looma_assistant.actor.TaskSupervisorActor;
import com.divric.looma_assistant.dto.TaskInput;
import com.divric.looma_assistant.dto.TaskResultOutput;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * TaskSupervisorEngine — headless engine for submitting tasks to the actor system
 * without a web layer (e.g. from CLI, batch jobs, scheduled tasks, or other non-HTTP entry points).
 * <p>
 * This engine manages its own Akka ActorSystem lifecycle independently of Spring's web context.
 * Uses the ask pattern ({@link Patterns#ask}) for non-blocking submission.
 * <p>
 * Supports loading task payloads from JSON files via Jackson {@link ObjectMapper}.
 */
public class TaskSupervisorEngine {

    private static final Logger log = LoggerFactory.getLogger(TaskSupervisorEngine.class);

    private final ActorSystem actorSystem;
    private final ActorRef supervisor;
    private final Duration defaultTimeout;
    private final ObjectMapper objectMapper;

    /**
     * Create a new headless engine with a configurable timeout.
     *
     * @param actorSystemName  name for the Akka ActorSystem
     * @param defaultTimeoutMs default timeout in milliseconds for task completion
     */
    public TaskSupervisorEngine(String actorSystemName, long defaultTimeoutMs) {
        this.defaultTimeout = Duration.ofMillis(defaultTimeoutMs);
        this.actorSystem = ActorSystem.create(actorSystemName);
        this.supervisor = actorSystem.actorOf(
                TaskSupervisorActor.props(),
                "task-supervisor"
        );
        this.objectMapper = new ObjectMapper();
        log.info("TaskSupervisorEngine '{}' initialized with {}ms default timeout",
                actorSystemName, defaultTimeoutMs);
    }

    /**
     * Convenience constructor with a default 30-second timeout.
     */
    public TaskSupervisorEngine(String actorSystemName) {
        this(actorSystemName, 30_000);
    }

    // ── JSON file loading ─────────────────────────────────────────────

    /**
     * Load a list of {@link TaskInput} records from a JSON file.
     * <p>
     * The file must contain a JSON array of objects matching the TaskInput record structure:
     * <pre>{@code
     * [
     *   { "task_id": "task-1", "prompt": "Write a sorting algorithm" },
     *   { "task_id": "task-2", "prompt": "Analyze this argument" }
     * ]
     * }</pre>
     *
     * @param jsonFilePath path to the JSON file (e.g. "/input/tasks.json")
     * @return parsed list of TaskInput records
     * @throws IOException if the file cannot be read or parsed
     */
    public List<TaskInput> loadTasks(String jsonFilePath) throws IOException {
        File file = new File(jsonFilePath);
        if (!file.exists()) {
            throw new IOException("Task input file not found: " + file.getAbsolutePath());
        }
        List<TaskInput> tasks = objectMapper.readValue(file, new TypeReference<List<TaskInput>>() {});
        log.info("Loaded {} tasks from {}", tasks.size(), file.getAbsolutePath());
        return tasks;
    }

    // ── JSON file output ───────────────────────────────────────────────

    /**
     * Write a list of {@link TaskResultOutput} records to a JSON file.
     *
     * @param results     the results to write
     * @param jsonFilePath path to the output JSON file (e.g. "/output/results.json")
     * @throws IOException if the file cannot be written
     */
    public void writeResults(List<TaskResultOutput> results, String jsonFilePath) throws IOException {
        File file = new File(jsonFilePath);
        // Ensure parent directories exist
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        objectMapper.writeValue(file, results);
        log.info("Wrote {} results to {}", results.size(), file.getAbsolutePath());
    }

    // ── Execution ─────────────────────────────────────────────────────

    /**
     * Load tasks from a JSON file, submit them to the actor system, and block
     * until all complete (or timeout).
     *
     * @param jsonFilePath path to the JSON file containing the task array
     * @return aggregated List<TaskResultOutput>
     * @throws IOException          if the file cannot be read or parsed
     * @throws InterruptedException if the current thread is interrupted while waiting
     * @throws ExecutionException   if the computation threw an exception
     * @throws TimeoutException     if the timeout expired before completion
     */
    public List<TaskResultOutput> executeFromJson(String jsonFilePath)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        List<TaskInput> tasks = loadTasks(jsonFilePath);
        return execute(tasks, defaultTimeout.toMillis());
    }

    /**
     * Submit a batch of tasks and block until all complete (or timeout).
     *
     * @param tasks list of TaskInput records
     * @return aggregated List<TaskResultOutput>
     * @throws InterruptedException if the current thread is interrupted while waiting
     * @throws ExecutionException   if the computation threw an exception
     * @throws TimeoutException     if the timeout expired before completion
     */
    public List<TaskResultOutput> execute(List<TaskInput> tasks)
            throws InterruptedException, ExecutionException, TimeoutException {
        return execute(tasks, defaultTimeout.toMillis());
    }

    /**
     * Submit a batch of tasks and block until all complete (or timeout).
     *
     * @param tasks     list of TaskInput records
     * @param timeoutMs maximum time to wait in milliseconds
     * @return aggregated List<TaskResultOutput>
     * @throws InterruptedException if the current thread is interrupted while waiting
     * @throws ExecutionException   if the computation threw an exception
     * @throws TimeoutException     if the timeout expired before completion
     */
    @SuppressWarnings("unchecked")
    public List<TaskResultOutput> execute(List<TaskInput> tasks, long timeoutMs)
            throws InterruptedException, ExecutionException, TimeoutException {

        CompletionStage<List<TaskResultOutput>> stage = Patterns.ask(
                supervisor,
                new TaskSupervisorActor.SubmitTasks(tasks),
                Duration.ofMillis(timeoutMs)
        ).thenApply(response -> (List<TaskResultOutput>) response);

        log.info("Engine submitted {} tasks, waiting up to {}ms for completion", tasks.size(), timeoutMs);
        List<TaskResultOutput> results = stage.toCompletableFuture().get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        log.info("Engine received {} results", results.size());
        return results;
    }

    /**
     * Submit a single task and block for the result.
     */
    public List<TaskResultOutput> executeSingle(TaskInput task)
            throws InterruptedException, ExecutionException, TimeoutException {
        return execute(List.of(task), defaultTimeout.toMillis());
    }

    // ── Main entry point with file I/O ────────────────────────────────

    /**
     * Main entry point for headless execution.
     * Reads tasks from /input/tasks.json, processes them, and writes results to /output/results.json.
     * Exits with code 0 on success, non-zero on failure.
     */
    public static void main(String[] args) {
        // Read paths from environment variables with sensible defaults for the container.
        // Override via env vars when running locally on Windows for testing, e.g.:
        //   set INPUT_FILE=C:\temp\tasks.json
        //   set OUTPUT_FILE=C:\temp\results.json
        String inputFile = System.getenv().getOrDefault("INPUT_FILE", "/input/tasks.json");
        String outputFile = System.getenv().getOrDefault("OUTPUT_FILE", "/output/results.json");

        TaskSupervisorEngine engine = new TaskSupervisorEngine("looma-headless", 60_000);

        try {
            log.info("Loading tasks from {}", inputFile);
            List<TaskInput> tasks = engine.loadTasks(inputFile);

            if (tasks.isEmpty()) {
                log.warn("No tasks found in input file");
                engine.writeResults(List.of(), outputFile);
                engine.shutdown();
                return;
            }

            log.info("Processing {} task(s)...", tasks.size());
            List<TaskResultOutput> results = engine.execute(tasks);

            log.info("Writing results to {}", outputFile);
            engine.writeResults(results, outputFile);

            log.info("Successfully processed {} tasks", results.size());
            engine.shutdown();

        } catch (Exception e) {
            log.error("Engine execution failed", e);
            System.exit(1);
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────

    /**
     * Shut down the actor system gracefully and exit the JVM with code 0.
     * <p>
     * Blocks until the actor system has fully terminated, then calls
     * {@code System.exit(0)} to ensure a clean headless shutdown.
     */
    public void shutdown() {
        log.info("Shutting down TaskSupervisorEngine...");
        actorSystem.terminate();
        try {
            actorSystem.getWhenTerminated().toCompletableFuture().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for actor system termination");
        } catch (ExecutionException e) {
            log.error("Error during actor system termination", e.getCause());
        }
        log.info("Actor system terminated. Exiting with code 0.");
        System.exit(0);
    }

    /**
     * Returns the underlying ActorSystem (for advanced use cases).
     */
    public ActorSystem getActorSystem() {
        return actorSystem;
    }
}