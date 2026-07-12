<script setup>
/**
 * BatchRunnerViewCard.vue
 *
 * Bulk JSON testing workspace card — uses OutputView + OutputCard internally.
 * The parent (App.vue) calls `runBatch(tasks)` via template ref.
 */

import { ref } from 'vue'
import OutputView from './OutputView.vue'
import { sendBatch } from '../services/api.js'

const outputViewRef = ref(null)
const isRunning = ref(false)
const totalDurationMs = ref(0)

/**
 * Called by the parent (App.vue) when the shared ChatInput submits a batch.
 */
async function runBatchHandler(tasks) {
  if (isRunning.value) return
  isRunning.value = true
  totalDurationMs.value = 0

  const view = outputViewRef.value
  if (!view) return

  view.clearAll()
  const startTime = performance.now()

  try {
    const { data } = await sendBatch(tasks)
    const items = Array.isArray(data) ? data : data.results ?? []

    if (items.length === 0) {
      // Fallback: treat the whole response as a single result
      items.push({
        task_id: tasks[0]?.task_id ?? 'batch',
        prompt: tasks.map((t) => t.prompt).join('; '),
        answer: typeof data === 'string' ? data : JSON.stringify(data),
        statusCode: 200,
        success: true,
      })
    }

    for (let i = 0; i < items.length; i++) {
      const item = items[i]
      const task = tasks[i] ?? {}
      const prompt = item.prompt ?? task.prompt ?? `[task ${i}]`
      const answer = item.answer ?? item.response ?? item.message ?? item.result ?? JSON.stringify(item)
      const taskId = item.task_id ?? task.task_id ?? `task-${i}`
      const statusCode = item.statusCode ?? item.status ?? 200
      const success = !!(item.success ?? (statusCode >= 200 && statusCode < 300) ?? true)

      view.addOutput({
        role: 'ai',
        message: `[${taskId}] ${prompt}\n\n${answer}`,
        actor: taskId,
        timestamp: new Date().toISOString(),
        loading: false,
        error: !success,
      })
    }
  } catch (err) {
    view.addOutput({
      role: 'ai',
      message: `Batch Error: ${err.message}`,
      actor: 'default',
      timestamp: new Date().toISOString(),
      loading: false,
      error: true,
    })
  } finally {
    totalDurationMs.value = Math.round(performance.now() - startTime)
    isRunning.value = false
  }
}

/** Clear all results. */
function clearResults() {
  outputViewRef.value?.clearAll()
  totalDurationMs.value = 0
}

defineExpose({ runBatch: runBatchHandler })
</script>

<template>
  <div class="batch-runner">
    <!-- Running banner -->
    <div v-if="isRunning" class="batch-runner__info">
      <span class="spinner spinner-sm" /> Running batch ({{ totalDurationMs }}ms)…
    </div>

    <!-- Output list via OutputView -->
    <OutputView
      ref="outputViewRef"
      title="Batch Runner — Bulk JSON"
      badge-text="BATCH"
      badge-class="orange"
      :actor-label="'batch'"
      empty-text="Run a batch to see results."
    >
      <template #header-actions>
        <button
          v-if="totalDurationMs > 0"
          class="btn btn-ghost btn-sm"
          title="Clear results"
          @click="clearResults"
        >
          🗑️
        </button>
      </template>
    </OutputView>
  </div>
</template>

<style scoped>
.batch-runner {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 400px;
}

/* ---------- Info banner ---------- */
.batch-runner__info {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-lg);
  background: rgba(88, 166, 255, 0.08);
  border-bottom: 1px solid var(--accent-blue);
  font-size: var(--font-size-sm);
  color: var(--accent-blue);
  font-family: var(--font-mono);
}
</style>