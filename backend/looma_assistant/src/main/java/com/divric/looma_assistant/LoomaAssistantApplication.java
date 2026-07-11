package com.divric.looma_assistant;

import com.divric.looma_assistant.dto.TaskInput;
import com.divric.looma_assistant.dto.TaskResultOutput;
import com.divric.looma_assistant.engine.TaskSupervisorEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * LoomaAssistantApplication — main entry point.
 * <p>
 * Container mode is determined by the active Spring profile:
 * <ul>
 *   <li><b>dev</b> (default) — boots up a full Tomcat web server for REST API development</li>
 *   <li><b>prod</b> — explicitly shuts down the web container ({@code WebApplicationType.NONE})
 *       so the app launches as an ultra-lightweight console utility.
 *       {@link TaskSupervisorEngine} takes over the terminal via a {@link CommandLineRunner},
 *       processes local files, and exits instantly without spinning up a web port.</li>
 * </ul>
 */
@SpringBootApplication
public class LoomaAssistantApplication {

    private static final Logger log = LoggerFactory.getLogger(LoomaAssistantApplication.class);

    public static void main(String[] args) {
        // Evaluate the active profile before the application context loads
        String activeProfile = resolveActiveProfile(args);

        SpringApplication app = new SpringApplication(LoomaAssistantApplication.class);

        if ("prod".equalsIgnoreCase(activeProfile)) {
            // PROD mode: no web server, pure CLI utility
            app.setWebApplicationType(WebApplicationType.NONE);
            log.info("Profile 'prod' detected — running in headless CLI mode (no web container)");
        } else {
            // DEV mode (or any other profile): full web server
            app.setWebApplicationType(WebApplicationType.SERVLET);
            log.info("Profile '{}' detected — running with embedded web container", activeProfile);
        }

        app.run(args);
    }

    /**
     * Resolves the active Spring profile from the command-line arguments.
     * Looks for {@code --spring.profiles.active=...} or {@code -Dspring.profiles.active=...}.
     * Defaults to {@code dev} if not specified.
     */
    private static String resolveActiveProfile(String[] args) {
        // Check system property first (set via -D when launching the JAR)
        String systemProp = System.getProperty("spring.profiles.active");
        if (systemProp != null && !systemProp.isEmpty()) {
            return systemProp;
        }

        // Check command-line arguments for --spring.profiles.active=...
        for (String arg : args) {
            if (arg.startsWith("--spring.profiles.active=")) {
                return arg.substring("--spring.profiles.active=".length());
            }
        }

        return "dev";
    }

    // ── CommandLineRunner (PROD only) ─────────────────────────────────

    /**
     * In PROD mode, a {@link CommandLineRunner} fires after the application
     * context is ready. It creates a {@link TaskSupervisorEngine}, submits
     * any tasks provided via command-line arguments or a default demo set,
     * prints results to stdout, and shuts down.
     * <p>
     * In DEV mode this runner is a no-op (the web server handles requests instead).
     */
    @Bean
    public CommandLineRunner cliRunner(Environment env) {
        return (String... args) -> {
            String[] activeProfiles = env.getActiveProfiles();
            boolean isProd = Arrays.asList(activeProfiles).contains("prod");

            if (!isProd) {
                log.info("DEV mode — CommandLineRunner skipped (web server handles requests)");
                return;
            }

            log.info("PROD mode — CommandLineRunner starting headless engine...");

            // Create a headless engine with a 60-second timeout
            TaskSupervisorEngine engine = new TaskSupervisorEngine("looma-headless", 60_000);

            try {
                // Build tasks from CLI arguments
                List<TaskInput> tasks = buildTasksFromArgs(args);

                if (tasks.isEmpty()) {
                    log.info("No tasks provided. Use: java -jar looma-assistant.jar \"task_id|prompt\"");
                    log.info("Example: java -jar looma-assistant.jar \"task-1|write a hello world\" \"task-2|capital of France\"");
                    return;
                }

                log.info("Processing {} task(s)...", tasks.size());
                List<TaskResultOutput> results = engine.execute(tasks);

                // Print results to stdout
                System.out.println("\n========== RESULTS ==========");
                for (int i = 0; i < results.size(); i++) {
                    TaskResultOutput output = results.get(i);
                    System.out.printf("[%d] task_id=%s | answer=%s%n",
                            i + 1,
                            output.task_id(),
                            output.answer() != null
                                    ? output.answer().substring(0, Math.min(output.answer().length(), 200))
                                    : "N/A");
                }
                System.out.println("==============================\n");

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Engine was interrupted", e);
            } catch (TimeoutException e) {
                log.error("Task processing timed out", e);
            } catch (Exception e) {
                log.error("Engine execution failed", e);
            } finally {
                engine.shutdown();
                log.info("Headless engine shut down. Exiting.");
                // Force exit to ensure the process terminates cleanly
                System.exit(0);
            }
        };
    }

    /**
     * Builds a list of {@link TaskInput} from the command-line arguments.
     * Each argument is expected in the format {@code task_id|prompt}.
     * <p>
     * For example: {@code java -jar app.jar "task-1|write a sorting algorithm" "task-2|capital of Germany"}
     *
     * @param args the raw command-line arguments (excluding Spring boot args already consumed)
     * @return list of TaskInput, empty if no matching arguments
     */
    private static List<TaskInput> buildTasksFromArgs(String[] args) {
        return Arrays.stream(args)
                .filter(arg -> arg.contains("|"))
                .map(arg -> {
                    String[] parts = arg.split("\\|", 2);
                    return new TaskInput(parts[0].trim(), parts[1].trim());
                })
                .toList();
    }
}