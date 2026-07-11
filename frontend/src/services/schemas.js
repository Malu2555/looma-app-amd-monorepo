/**
 * schemas.js
 *
 * Shared JSON schema definitions that mirror the backend DTOs.
 *
 * These constants are the single source of truth for the JSON shapes
 * the frontend sends and receives, ensuring they stay aligned with
 * the backend Java records.
 *
 * Backend DTOs:
 *   - TaskInput        → { task_id: string, prompt: string }
 *   - ChatRequest      → { task_id: string, message: string }
 *   - TaskResultOutput → { task_id: string, answer: string }
 *
 * Usage:
 *   import { TASK_INPUT_SCHEMA, CHAT_REQUEST_SCHEMA } from '../services/schemas.js'
 */

// ---------------------------------------------------------------------------
// Request schemas (what the frontend sends to the backend)
// ---------------------------------------------------------------------------

/**
 * Schema for an individual batch task — mirrors TaskInput.java.
 *
 * @typedef {Object} TaskInput
 * @property {string} task_id  - Unique identifier for the task (e.g. "text", "code", "reason")
 * @property {string} prompt   - The prompt or data to process
 */

/** @type {TaskInput} */
export const TASK_INPUT_TEMPLATE = {
  task_id: 'text',
  prompt: 'Your prompt here…',
}

/** Human-readable column order for batch CSV / table display. */
export const TASK_INPUT_FIELDS = ['task_id', 'prompt']

/**
 * Schema for single-prompt chat — mirrors ChatRequest.java.
 *
 * @typedef {Object} ChatRequest
 * @property {string} task_id  - Actor route hint (e.g. "text", "code", "reason")
 * @property {string} message  - The user's chat message
 */
export const CHAT_REQUEST_TEMPLATE = {
  task_id: 'text',
  message: 'Your message here…',
}

// ---------------------------------------------------------------------------
// Response schemas (what the backend returns)
// ---------------------------------------------------------------------------

/**
 * Schema for a single task result — mirrors TaskResultOutput.java.
 *
 * @typedef {Object} TaskResultOutput
 * @property {string} task_id  - Echoes back the task_id from the request
 * @property {string} answer   - The processed result text
 */
export const TASK_RESULT_OUTPUT_FIELDS = ['task_id', 'answer']

// ---------------------------------------------------------------------------
// Validation helpers
// ---------------------------------------------------------------------------

/**
 * Validates that a parsed object conforms to TaskInput shape.
 *
 * @param {*} item     - The parsed JSON item to validate
 * @param {number} idx - Index in the array (for error messaging)
 * @returns {string|null} Error message, or null if valid
 */
export function validateTaskInput(item, idx) {
  if (!item || typeof item !== 'object') {
    return `Item ${idx}: must be an object`
  }
  if (typeof item.task_id !== 'string' || item.task_id.trim().length === 0) {
    return `Item ${idx}: missing or invalid "task_id" (must be a non-empty string)`
  }
  if (typeof item.prompt !== 'string' || item.prompt.trim().length === 0) {
    return `Item ${idx}: missing or invalid "prompt" (must be a non-empty string)`
  }
  return null
}

/**
 * Validates a single ChatRequest payload.
 *
 * @param {*} body - The parsed JSON body
 * @returns {string|null} Error message, or null if valid
 */
export function validateChatRequest(body) {
  if (!body || typeof body !== 'object') {
    return 'Body must be a JSON object'
  }
  if (typeof body.message !== 'string' || body.message.trim().length === 0) {
    return 'Missing or invalid "message" (must be a non-empty string)'
  }
  return null
}