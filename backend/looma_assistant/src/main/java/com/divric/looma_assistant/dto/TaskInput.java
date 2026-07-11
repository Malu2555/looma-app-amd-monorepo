package com.divric.looma_assistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * TaskInput — lightweight immutable record for deserializing incoming task JSON payloads.
 * <p>
 * Used by both the Vue frontend and headless CLI sources to submit tasks
 * to the actor system.
 *
 * @param task_id the unique identifier for the task
 * @param prompt  the prompt or data to process
 */
public record TaskInput(
        @JsonProperty("task_id") String task_id,
        @JsonProperty("prompt") String prompt
) {
}