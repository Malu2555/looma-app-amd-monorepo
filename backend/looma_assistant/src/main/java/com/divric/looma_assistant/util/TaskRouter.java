package com.divric.looma_assistant.util;

import com.divric.looma_assistant.dto.TaskInput;

/**
 * TaskRouter — hybrid task-type classifier for routing to specialized workers.
 * <p>
 * Decides which worker should handle a task using two signals, in priority order:
 * <ol>
 *   <li><b>Explicit task_id keyword</b> — preserves the original behavior where
 *       the task identifier itself encodes the type (e.g. "code-task-1").</li>
 *   <li><b>Dynamic prompt analysis</b> — inspects the actual prompt text for
 *       code/reasoning/factual signals. This is what enables dynamic harness
 *       prompts whose task_id is an opaque identifier (e.g. "task-1", "req-42").</li>
 * </ol>
 * <p>
 * The classifier is pure and side-effect-free so it can be unit-tested without
 * starting an Akka actor system.
 */
public final class TaskRouter {

    /** The five specialized worker categories. */
    public enum WorkerType {
        CODE,
        REASONING,
        FACTUAL,
        TEXT,
        LABEL
    }

    // ── Prompt signal patterns (lowercased substring matches) ──────────

    private static final String[] CODE_SIGNALS = {
            "```", "def ", "function", "class ", "import ",
            "public static", "private static", "select ", "from ",
            "<html", "</", "void ", "return ", "const ", "let ", "var "
    };

    private static final String[] REASONING_SIGNALS = {
            "prove", "why ", "analyze", "step by step", "reason",
            "logic", "compare", "explain", "evaluate", "argue"
    };

    private static final String[] FACTUAL_SIGNALS = {
            "what is", "define", "when did", "who was", "how many",
            "list ", "name the", "which ", "where is", "how much"
    };

    private static final String[] LABEL_SIGNALS = {
            "sentiment", "positive", "negative", "neutral", "entity",
            "ner", "extract", "person", "organization", "location",
            "classify", "label", "tone", "emotion", "named entity"
    };

    private TaskRouter() {
    }

    /**
     * Resolve the worker type for a task.
     *
     * @param task the incoming task (must not be null)
     * @return the best matching {@link WorkerType} (never null; defaults to TEXT)
     */
    public static WorkerType resolveWorkerType(TaskInput task) {
        if (task == null) {
            return WorkerType.TEXT;
        }

        // 1. Explicit task_id keyword (backward-compatible behavior)
        WorkerType fromId = matchById(task.task_id());
        if (fromId != null) {
            return fromId;
        }

        // 2. Dynamic prompt analysis
        String prompt = task.prompt();
        if (prompt == null || prompt.isBlank()) {
            return WorkerType.TEXT;
        }

        String lower = prompt.toLowerCase();

        int codeScore = score(lower, CODE_SIGNALS);
        int reasonScore = score(lower, REASONING_SIGNALS);
        int factScore = score(lower, FACTUAL_SIGNALS);
        int labelScore = score(lower, LABEL_SIGNALS);

        // Pick the highest score; ties resolve to TEXT (default prose path)
        int max = Math.max(codeScore, Math.max(reasonScore, Math.max(factScore, labelScore)));
        if (max == 0) {
            return WorkerType.TEXT;
        }
        if (codeScore == max) {
            return WorkerType.CODE;
        }
        if (reasonScore == max) {
            return WorkerType.REASONING;
        }
        if (factScore == max) {
            return WorkerType.FACTUAL;
        }
        if (labelScore == max) {
            return WorkerType.LABEL;
        }
        return WorkerType.TEXT;
    }

    /**
     * Matches the task_id against the original hardcoded keyword rules.
     * Returns null if the id carries no type signal (so prompt analysis can run).
     */
    private static WorkerType matchById(String taskId) {
        if (taskId == null) {
            return null;
        }
        String type = taskId.trim().toLowerCase();

        if (type.contains("code") || type.contains("debug") || type.contains("architect")
                || type.contains("parse") || type.contains("generate")) {
            return WorkerType.CODE;
        }
        if (type.contains("reason") || type.contains("analyze") || type.contains("think")
                || type.contains("logic") || type.contains("multi")) {
            return WorkerType.REASONING;
        }
        if (type.contains("fact") || type.contains("lookup") || type.contains("history")
                || type.contains("define") || type.contains("system") || type.contains("data")) {
            return WorkerType.FACTUAL;
        }
        if (type.contains("label") || type.contains("sentiment") || type.contains("ner")
                || type.contains("entity") || type.contains("classify") || type.contains("tone")) {
            return WorkerType.LABEL;
        }
        return null;
    }

    /** Counts how many signals from the given list appear in the text. */
    private static int score(String text, String[] signals) {
        int count = 0;
        for (String s : signals) {
            if (text.contains(s)) {
                count++;
            }
        }
        return count;
    }
}