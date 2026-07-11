package com.divric.looma_assistant.services;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.pattern.Patterns;
import com.divric.looma_assistant.actor.TaskSupervisorActor;
import com.divric.looma_assistant.dto.TaskInput;
import com.divric.looma_assistant.dto.TaskResultOutput;
import com.divric.looma_assistant.util.ModelHolder;
import com.divric.looma_assistant.util.ModelResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * ActorBridgeService — web-layer bridge to the actor system.
 * <p>
 * Exposes a clean Spring-managed service that controllers (REST endpoints)
 * can call to submit tasks and receive results without interacting directly
 * with Akka actors.
 * <p>
 * Uses the ask pattern ({@link Patterns#ask}) for non-blocking,
 * timeout-protected communication with the actor system.
 * <p>
 * Also bridges the auto-configured Spring AI {@link ChatModel} and the
 * allowed-models configuration to the actor system so that all specialized
 * workers can perform real LLM inference without hardcoding model IDs.
 */
@Service
public class ActorBridgeService {

    private static final Logger log = LoggerFactory.getLogger(ActorBridgeService.class);

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final ChatModel chatModel;
    private final String allowedModels;
    private ActorSystem actorSystem;
    private ActorRef supervisor;

    public ActorBridgeService(ChatModel chatModel, @Value("${spring.ai.openai.allowed-models}") String allowedModels) {
        this.chatModel = chatModel;
        this.allowedModels = allowedModels;
    }

    @PostConstruct
    public void init() {
        // Bridge the auto-configured ChatModel so Akka actors can use it
        ModelHolder.setChatModel(chatModel);
        log.info("ChatModel bridged to ModelHolder for actor system");

        // Initialize the dynamic model resolver with the allowed-models list
        ModelResolver.init(allowedModels);
        log.info("ModelResolver initialized with allowed-models: {}", allowedModels);

        actorSystem = ActorSystem.create("looma-assistant-system");
        supervisor = actorSystem.actorOf(
                TaskSupervisorActor.props(),
                "task-supervisor"
        );
        log.info("ActorBridgeService initialized — actor system '{}' ready", actorSystem.name());
    }

    /**
     * Submit a list of tasks to the actor system for processing.
     * <p>
     * Uses {@link Patterns#ask} with a 30-second timeout so the caller
     * is never blocked indefinitely.
     *
     * @param tasks list of TaskInput records
     * @return CompletionStage that completes with the aggregated List<TaskResultOutput>
     * @throws IllegalStateException if the actor system has not been initialized
     */
    @SuppressWarnings("unchecked")
    public CompletionStage<List<TaskResultOutput>> submitTasks(List<TaskInput> tasks) {
        CompletionStage<List<TaskResultOutput>> stage = Patterns.ask(
                supervisor,
                new TaskSupervisorActor.SubmitTasks(tasks),
                DEFAULT_TIMEOUT
        ).thenApply(response -> (List<TaskResultOutput>) response);

        log.info("Submitted {} tasks to actor system via ask pattern", tasks.size());
        return stage;
    }

    /**
     * Submit a single task (wraps in a list for convenience).
     *
     * @param task single TaskInput record
     * @return CompletionStage that completes with a single-element List<TaskResultOutput>
     */
    public CompletionStage<List<TaskResultOutput>> submitSingleTask(TaskInput task) {
        return submitTasks(List.of(task));
    }

    @PreDestroy
    public void shutdown() {
        if (actorSystem != null) {
            actorSystem.terminate();
            log.info("ActorBridgeService — actor system terminated");
        }
    }
}