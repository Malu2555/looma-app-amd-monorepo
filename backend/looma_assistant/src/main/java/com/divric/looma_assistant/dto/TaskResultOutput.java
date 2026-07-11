package com.divric.looma_assistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * TaskResultOutput — lightweight immutable record for serializing task results as JSON.
 * <p>
 * Returned by the actor system after processing, consumed by the Vue frontend
 * or headless CLI.
 *
 * @param task_id the unique identifier for the task
 * @param answer  the processed result text
 */
public record TaskResultOutput(
        @JsonProperty("task_id") String task_id,
        @JsonProperty("answer") String answer
) {
}