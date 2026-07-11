package com.divric.looma_assistant.util;

import com.divric.looma_assistant.util.TaskRouter.WorkerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * ModelResolver — dynamically selects a Fireworks AI model identifier based on
 * the task type and the configured allowed-models list.
 * <p>
 * Models are read from configuration ({@code spring.ai.openai.allowed-models})
 * and mapped to capability categories using simple keyword matching on the
 * model names themselves. This avoids hardcoding model IDs in the worker actors.
 * <p>
 * If no model name in the allowed list matches the expected capability,
 * the first allowed model is used as a fallback.
 */
public final class ModelResolver {

    private static final Logger log = LoggerFactory.getLogger(ModelResolver.class);

    private static List<String> allowedModelsList = List.of();

    private ModelResolver() {
    }

    /**
     * Initializes the resolver with the comma-separated allowed-models string
     * from configuration. Called once from {@code ActorBridgeService}.
     *
     * @param allowedModels comma-separated model identifiers (e.g. "models/gemma-2b-it,models/gemma-7b-it")
     */
    public static void init(String allowedModels) {
        if (allowedModels != null && !allowedModels.isBlank()) {
            allowedModelsList = List.of(allowedModels.split("\\s*,\\s*"));
            log.info("ModelResolver initialized with {} models: {}", allowedModelsList.size(), allowedModelsList);
        } else {
            allowedModelsList = List.of();
            log.warn("ModelResolver initialized with empty allowed-models list!");
        }
    }

    /**
     * Returns the list of all allowed model identifiers.
     */
    public static List<String> getAllowedModels() {
        return allowedModelsList;
    }

    /**
     * Resolves a model identifier for the given task type using simple
     * keyword matching against the allowed models list.
     * <p>
     * Logic:
     * <ul>
     *   <li>If taskType contains "code", "debug", "architect", "parse", or "generate"
     *       → prefers a model whose name contains "code" or "coder"</li>
     *   <li>If taskType contains "reason", "analyze", "think", "logic", or "multi"
     *       → prefers a model whose name contains "reason", "r1", or the largest model</li>
     *   <li>If taskType contains "fact", "lookup", "history", "define", "system", or "data"
     *       → prefers a model whose name contains "3b" or the smallest / fastest model</li>
     *   <li>Otherwise (prose, summary, chat, default)
     *       → prefers a model whose name contains "8b" or the middle-range model</li>
     * </ul>
     *
     * @param taskType the type of the task (can be null)
     * @return the resolved model identifier, or the first allowed model if no match found
     */
    public static String resolveModel(String taskType) {
        if (allowedModelsList.isEmpty()) {
            log.warn("resolveModel called but allowed-models is empty");
            return "";
        }

        if (taskType == null) {
            return pickFallback();
        }

        String type = taskType.trim().toLowerCase();

        // ── Code / debug / architecture ───────────────────────────────
        if (type.contains("code") || type.contains("debug") || type.contains("architect")
                || type.contains("parse") || type.contains("generate")) {
            String model = findByKeyword("code", "coder", "33b", "34b", "70b");
            if (model != null) return model;
        }

        // ── Reasoning / deep analysis ─────────────────────────────────
        if (type.contains("reason") || type.contains("analyze") || type.contains("analyze")
                || type.contains("think") || type.contains("logic") || type.contains("multi")) {
            String model = findByKeyword("reason", "r1", "qwen", "72b", "70b");
            if (model != null) return model;
        }

        // ── Factual / lightweight lookups ─────────────────────────────
        if (type.contains("fact") || type.contains("lookup") || type.contains("history")
                || type.contains("define") || type.contains("system") || type.contains("data")) {
            String model = findByKeyword("3b", "tiny", "small", "light", "1b", "2b");
            if (model != null) return model;
        }

        // ── Fallback for anything else (including prose, summary, chat) ─
        return pickFallback();
    }

    /**
     * Resolves a model identifier for an already-classified {@link WorkerType}.
     * <p>
     * This aligns model selection with the routing decision made by
     * {@link TaskRouter}, so the chosen model matches the worker that will
     * handle the task (e.g. CODE → large code model, FACTUAL → small/cheap
     * model). This avoids re-deriving the type from the task_id string and
     * keeps model cost/token usage consistent with the task's actual nature.
     *
     * @param workerType the classified worker type (must not be null)
     * @return the resolved model identifier, or the first allowed model if no match found
     */
    public static String resolveModel(WorkerType workerType) {
        if (allowedModelsList.isEmpty()) {
            log.warn("resolveModel called but allowed-models is empty");
            return "";
        }
        if (workerType == null) {
            return pickFallback();
        }

        return switch (workerType) {
            case CODE -> {
                String m = findByKeyword("code", "coder", "33b", "34b", "70b", "72b");
                yield (m != null) ? m : pickFallback();
            }
            case REASONING -> {
                String m = findByKeyword("reason", "r1", "qwen", "72b", "70b");
                yield (m != null) ? m : pickFallback();
            }
            case FACTUAL -> {
                String m = findByKeyword("3b", "tiny", "small", "light", "1b", "2b");
                yield (m != null) ? m : pickFallback();
            }
            case TEXT -> pickFallback();
            case LABEL -> {
                String m = findByKeyword("3b", "tiny", "small", "light", "1b", "2b", "label");
                yield (m != null) ? m : pickFallback();
            }
        };
    }

    /**
     * Searches the allowed models list for any model whose name contains
     * at least one of the given keywords (case-insensitive match on the
     * model ID string).
     *
     * @return the first matching model, or null if none match
     */
    private static String findByKeyword(String... keywords) {
        for (String model : allowedModelsList) {
            String lower = model.toLowerCase();
            for (String kw : keywords) {
                if (lower.contains(kw.toLowerCase())) {
                    log.debug("findByKeyword matched model='{}' for keyword='{}'", model, kw);
                    return model;
                }
            }
        }
        return null;
    }

    /**
     * Picks a fallback model — prefers the first entry in the allowed list,
     * which is typically the "default" model in the configuration.
     */
    private static String pickFallback() {
        String fallback = allowedModelsList.get(0);
        log.debug("pickFallback returning first allowed model: {}", fallback);
        return fallback;
    }
}