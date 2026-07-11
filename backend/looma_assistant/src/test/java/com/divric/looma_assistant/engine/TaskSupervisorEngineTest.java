package com.divric.looma_assistant.engine;

import com.divric.looma_assistant.dto.TaskInput;
import com.divric.looma_assistant.dto.TaskResultOutput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the headless engine's file I/O boundary.
 * Tests JSON loading, writing, and error handling without starting a full actor system.
 */
class TaskSupervisorEngineTest {

    private TaskSupervisorEngine engine;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Create engine with a short timeout; actor system starts but won't be used for I/O tests
        engine = new TaskSupervisorEngine("test-headless", 5_000);
    }

    @AfterEach
    void tearDown() {
        // Shut down the actor system without calling System.exit()
        engine.getActorSystem().terminate();
    }

    @Test
    void loadTasks_validJson_returnsParsedTasks() throws Exception {
        // Arrange
        Path inputFile = tempDir.resolve("tasks.json");
        Files.writeString(inputFile, """
                [
                  {"task_id": "t1", "prompt": "Write a sorting algorithm"},
                  {"task_id": "t2", "prompt": "Analyze this argument"}
                ]
                """);

        // Act
        List<TaskInput> tasks = engine.loadTasks(inputFile.toString());

        // Assert
        assertEquals(2, tasks.size());
        assertEquals("t1", tasks.get(0).task_id());
        assertEquals("Write a sorting algorithm", tasks.get(0).prompt());
        assertEquals("t2", tasks.get(1).task_id());
        assertEquals("Analyze this argument", tasks.get(1).prompt());
    }

    @Test
    void loadTasks_emptyArray_returnsEmptyList() throws Exception {
        // Arrange
        Path inputFile = tempDir.resolve("empty.json");
        Files.writeString(inputFile, "[]");

        // Act
        List<TaskInput> tasks = engine.loadTasks(inputFile.toString());

        // Assert
        assertTrue(tasks.isEmpty());
    }

    @Test
    void loadTasks_fileNotFound_throwsIOException() {
        // Arrange
        String missingPath = tempDir.resolve("nonexistent.json").toString();

        // Act & Assert
        IOException exception = assertThrows(IOException.class, () -> engine.loadTasks(missingPath));
        assertTrue(exception.getMessage().contains("nonexistent.json"));
    }

    @Test
    void writeResults_roundTrip_returnsSameData() throws Exception {
        // Arrange
        Path outputFile = tempDir.resolve("results.json");
        List<TaskResultOutput> original = List.of(
                new TaskResultOutput("t1", "Result 1"),
                new TaskResultOutput("t2", "Result 2")
        );

        // Act: write
        engine.writeResults(original, outputFile.toString());

        // Assert: file exists and is valid JSON
        assertTrue(Files.exists(outputFile));
        String content = Files.readString(outputFile);
        assertTrue(content.contains("t1"));
        assertTrue(content.contains("Result 2"));

        // Act: read back as TaskResultOutput (results use a different schema than TaskInput)
        ObjectMapper mapper = new ObjectMapper();
        List<TaskResultOutput> reloaded = mapper.readValue(outputFile.toFile(),
                new com.fasterxml.jackson.core.type.TypeReference<List<TaskResultOutput>>() {});

        // Assert: same number of entries and content preserved
        assertEquals(2, reloaded.size());
        assertEquals("t1", reloaded.get(0).task_id());
        assertEquals("Result 1", reloaded.get(0).answer());
        assertEquals("t2", reloaded.get(1).task_id());
        assertEquals("Result 2", reloaded.get(1).answer());
    }

    @Test
    void writeResults_createsParentDirectories() throws Exception {
        // Arrange
        Path nestedDir = tempDir.resolve("subdir").resolve("nested");
        Path outputFile = nestedDir.resolve("results.json");
        List<TaskResultOutput> results = List.of(
                new TaskResultOutput("t1", "Hello")
        );

        // Act
        engine.writeResults(results, outputFile.toString());

        // Assert
        assertTrue(Files.exists(outputFile));
        String content = Files.readString(outputFile);
        assertTrue(content.contains("t1"));
    }

    @Test
    void loadTasks_invalidJson_throwsIOException() {
        // Arrange
        Path inputFile = tempDir.resolve("bad.json");
        // Write malformed JSON
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> {
            Files.writeString(inputFile, "{invalid json}");
        });

        // Act & Assert
        assertThrows(IOException.class, () -> engine.loadTasks(inputFile.toString()));
    }
}