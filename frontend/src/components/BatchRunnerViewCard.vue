<script setup>
/**
 * BatchRunnerViewCard.vue
 *
 * Bulk JSON testing workspace card (batch evaluation mode).
 *
 * Allows users to:
 *   - Paste/edit a JSON array of tasks
 *   - Run the batch against the backend
 *   - View per-task results in a table / list
 *   - Copy individual results
 *
 * Uses ChatInput in bulk-mode for the editor and sends data
 * via the `sendBatch()` API helper.
 */

import { ref } from 'vue'
import ChatInput from './ChatInput.vue'
import { sendBatch } from '../services/api.js'

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

/**
 * @typedef {Object} BatchTask
 * @property {string} task_id
 * @property {string} prompt
 */

/**
 * @typedef {Object} BatchResult
 * @property {number}  index
 * @property {string}  task_id
 * @property {string}  prompt
 * @property {string}  answer
 * @property {number}  statusCode
 * @property {number}  durationMs
 * @property {boolean} success
 */

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

const results = ref(/** @type {BatchResult[]} */ ([]))
const isRunning = ref(false)
const totalDurationMs = ref(0)
const errorMessage = ref('')

// ---------------------------------------------------------------------------
// Handlers
// ---------------------------------------------------------------------------

/**
 * Called when ChatInput emits 'submit-batch'.
 * Iterates over each task, sends it, and collects results.
 */
async function handleBatchSubmit(tasks) {
  if (isRunning.value) return
  isRunning.value = true
  errorMessage.value = ''
  results.value = []
  totalDurationMs.value = 0

  const startTime = performance.now()

  try {
    const { data } = await sendBatch(tasks)

    // Backend may return an array of results or a single wrapper
    const items = Array.isArray(data) ? data : data.results ?? []

    if (items.length === 0) {
      // Fallback: treat the whole response as a single result
      results.value = tasks.map((task, i) => ({
        index: i,
        task_id: task.task_id,
        prompt: task.prompt,
        answer: typeof data === 'string' ? data : JSON.stringify(data),
        statusCode: 200,
        durationMs: 0,
        success: true,
      }))
    } else {
      results.value = items.map((item, i) => ({
        index: i,
        task_id: item.task_id ?? tasks[i]?.task_id ?? `task-${i}`,
        prompt: item.prompt ?? tasks[i]?.prompt ?? `[task ${i}]`,
        answer: item.answer ?? item.response ?? item.message ?? item.result ?? JSON.stringify(item),
        statusCode: item.statusCode ?? item.status ?? 200,
        durationMs: item.durationMs ?? item.duration ?? 0,
        success: !!(item.success ?? item.statusCode >= 200 ?? item.status >= 200 ?? true),
      }))
    }
  } catch (err) {
    errorMessage.value = err.message
  } finally {
    totalDurationMs.value = Math.round(performance.now() - startTime)
    isRunning.value = false
  }
}

/** Copy a single result's response text to clipboard. */
function copyResponse(text) {
  navigator.clipboard.writeText(text).catch(() => {})
}

/** Copy the full results as JSON to clipboard. */
function copyAllResults() {
  const json = JSON.stringify(results.value, null, 2)
  navigator.clipboard.writeText(json).catch(() => {})
}

/** Clear all results. */
function clearResults() {
  results.value = []
  errorMessage.value = ''
  totalDurationMs.value = 0
}

/**
 * Returns a badge class based on the result success status.
 */
function statusBadgeClass(result) {
  if (result.success) return 'badge-green'
  return 'badge-red'
}
</script>

<template>
  <div class="batch-runner card">
    <!-- Header -->
    <div class="card-header">
      <h4>
        <span class="pulse-dot" :class="isRunning ? 'loading' : results.length > 0 ? 'active' : 'error'" />
        Batch Runner — Bulk JSON
      </h4>
      <div class="card-header__actions">
        <span class="badge badge-orange">BATCH</span>
        <button
          v-if="results.length > 0"
          class="btn btn-ghost btn-sm"
          title="Copy all results"
          @click="copyAllResults"
        >
          📋
        </button>
        <button
          v-if="results.length > 0"
          class="btn btn-ghost btn-sm"
          title="Clear results"
          @click="clearResults"
        >
          🗑️
        </button>
      </div>
    </div>

    <!-- ChatInput in bulk mode -->
    <div class="batch-runner__editor">
      <ChatInput :disabled="isRunning" :bulk-mode="true" @submit-batch="handleBatchSubmit" />
    </div>

    <!-- Error banner -->
    <div v-if="errorMessage" class="batch-runner__error">
      ⚠️ {{ errorMessage }}
    </div>

    <!-- Results section -->
    <div v-if="results.length > 0" class="batch-runner__results">
      <div class="batch-runner__results-header">
        <span class="text-muted terminal-text">
          Results ({{ results.length }} tasks · {{ totalDurationMs }}ms total)
        </span>
      </div>

      <div class="batch-runner__table">
        <!-- Table header -->
        <div class="batch-runner__table-row batch-runner__table-row--header">
          <span class="batch-runner__cell batch-runner__cell--idx">#</span>
          <span class="batch-runner__cell batch-runner__cell--actor">Actor</span>
          <span class="batch-runner__cell batch-runner__cell--prompt">Prompt</span>
          <span class="batch-runner__cell batch-runner__cell--response">Response</span>
          <span class="batch-runner__cell batch-runner__cell--status">Status</span>
          <span class="batch-runner__cell batch-runner__cell--action" />
        </div>

        <!-- Table rows -->
        <div
          v-for="(result, idx) in results"
          :key="idx"
          class="batch-runner__table-row"
          :class="{ 'batch-runner__table-row--even': idx % 2 === 0 }"
        >
          <span class="batch-runner__cell batch-runner__cell--idx terminal-text">
            {{ result.index }}
          </span>

          <span class="batch-runner__cell batch-runner__cell--actor">
            <span class="badge badge-blue">
              {{ result.task_id }}
            </span>
          </span>

          <span class="batch-runner__cell batch-runner__cell--prompt" :title="result.prompt">
            {{ result.prompt.length > 60 ? result.prompt.slice(0, 60) + '…' : result.prompt }}
          </span>

          <span class="batch-runner__cell batch-runner__cell--response" :title="result.answer">
            <code class="terminal-text">
              {{ result.answer.length > 80 ? result.answer.slice(0, 80) + '…' : result.answer }}
            </code>
          </span>

          <span class="batch-runner__cell batch-runner__cell--status">
            <span class="badge" :class="statusBadgeClass(result)">
              {{ result.statusCode }} {{ result.success ? 'OK' : 'FAIL' }}
            </span>
          </span>

          <span class="batch-runner__cell batch-runner__cell--action">
            <button
              class="btn btn-ghost btn-sm"
              title="Copy response"
              @click="copyResponse(result.answer)"
            >
              📋
            </button>
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.batch-runner {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 400px;
}
.card-header__actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

/* ---------- Editor area ---------- */
.batch-runner__editor {
  flex-shrink: 0;
}

/* ---------- Error banner ---------- */
.batch-runner__error {
  padding: var(--spacing-md) var(--spacing-lg);
  background: rgba(248, 81, 73, 0.1);
  border-bottom: 1px solid var(--accent-red);
  font-size: var(--font-size-sm);
  color: var(--accent-red);
  font-family: var(--font-mono);
}

/* ---------- Results section ---------- */
.batch-runner__results {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}
.batch-runner__results-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--spacing-sm) var(--spacing-lg);
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-tertiary);
  font-size: var(--font-size-xs);
}

/* ---------- Results table ---------- */
.batch-runner__table {
  display: flex;
  flex-direction: column;
  font-size: var(--font-size-sm);
}
.batch-runner__table-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-lg);
  border-bottom: 1px solid var(--border-color);
  transition: background var(--transition-fast);
}
.batch-runner__table-row:hover {
  background: var(--bg-hover);
}
.batch-runner__table-row--header {
  background: var(--bg-tertiary);
  font-weight: 600;
  text-transform: uppercase;
  font-size: var(--font-size-xs);
  letter-spacing: 0.5px;
  color: var(--text-secondary);
  position: sticky;
  top: 0;
  z-index: 1;
}
.batch-runner__table-row--even {
  background: rgba(255, 255, 255, 0.02);
}

/* ---------- Cells ---------- */
.batch-runner__cell {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.batch-runner__cell--idx {
  width: 36px;
  flex-shrink: 0;
  color: var(--text-muted);
  text-align: center;
}
.batch-runner__cell--actor {
  width: 80px;
  flex-shrink: 0;
}
.batch-runner__cell--prompt {
  flex: 1.5;
  min-width: 100px;
}
.batch-runner__cell--response {
  flex: 2;
  min-width: 120px;
}
.batch-runner__cell--response code {
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
}
.batch-runner__cell--status {
  width: 80px;
  flex-shrink: 0;
  text-align: center;
}
.batch-runner__cell--action {
  width: 36px;
  flex-shrink: 0;
  text-align: center;
}
</style>