<script setup>
/**
 * ChatInput.vue
 *
 * Dual-mode input component:
 *   1. Single-prompt mode — a textarea for a single prompt string.
 *   2. Bulk-JSON mode — a larger monospace editor that accepts a JSON array
 *      of TaskInput objects matching the backend DTO.
 *
 * Emits
 * -----
 * - submit(text: string)                         – single prompt
 * - submit-batch(tasks: TaskInput[])             – bulk JSON array
 *
 * Props
 * -----
 * - disabled  : boolean – disables both input and submit button
 * - bulkMode  : boolean – when true switches to JSON-array editor mode
 */

import { ref, watch, nextTick } from 'vue'
import { validateTaskInput } from '../services/schemas.js'

const props = defineProps({
  disabled: { type: Boolean, default: false },
  bulkMode: { type: Boolean, default: false },
})

const emit = defineEmits(['submit', 'submit-batch'])

// ---------- Single-prompt state ----------
const promptText = ref('')

// ---------- Bulk-JSON state ----------
const jsonText = ref(
  JSON.stringify(
    [
      { task_id: 'text', prompt: 'Explain quantum computing' },
      { task_id: 'code', prompt: 'Write a Python fibonacci function' },
    ],
    null,
    2,
  ),
)
const jsonError = ref('')

/** Textarea reference for auto-focus. */
const textareaRef = ref(null)

// Auto-focus when bulk mode changes
watch(
  () => props.bulkMode,
  async () => {
    await nextTick()
    textareaRef.value?.focus()
  },
)

/** Submit handler — dispatches the correct event based on mode. */
function handleSubmit() {
  if (props.disabled) return

  if (props.bulkMode) {
    submitBulk()
  } else {
    submitSingle()
  }
}

function submitSingle() {
  const trimmed = promptText.value.trim()
  if (!trimmed) return
  emit('submit', trimmed)
  promptText.value = ''
}

function submitBulk() {
  jsonError.value = ''
  try {
    const parsed = JSON.parse(jsonText.value)

    if (!Array.isArray(parsed)) {
      jsonError.value = 'Payload must be a JSON array.'
      return
    }
    if (parsed.length === 0) {
      jsonError.value = 'Array must contain at least one task.'
      return
    }

    for (let i = 0; i < parsed.length; i++) {
      const err = validateTaskInput(parsed[i], i)
      if (err) {
        jsonError.value = err
        return
      }
    }

    emit('submit-batch', parsed)
  } catch (err) {
    jsonError.value = `Invalid JSON: ${err.message}`
  }
}

/** Keyboard shortcut: Ctrl/Cmd + Enter to submit. */
function onKeydown(e) {
  if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
    e.preventDefault()
    handleSubmit()
  }
}

/** Exposed so parent can clear the input programmatically. */
function clear() {
  promptText.value = ''
  jsonText.value = ''
  jsonError.value = ''
}

defineExpose({ clear })
</script>

<template>
  <div class="chat-input card">
    <!-- Mode indicator -->
    <div class="chat-input__header">
      <span class="chat-input__mode-label">
        <span class="pulse-dot" :class="disabled ? 'error' : 'active'" />
        <code class="terminal-text">{{ bulkMode ? 'BULK JSON' : 'SINGLE PROMPT' }}</code>
      </span>
      <span v-if="bulkMode && jsonError" class="chat-input__error-badge">⚠️ {{ jsonError }}</span>
    </div>

    <!-- Input area -->
    <div class="chat-input__body">
      <!-- Single-prompt textarea -->
      <textarea
        v-if="!bulkMode"
        ref="textareaRef"
        v-model="promptText"
        class="chat-input__textarea chat-input__textarea--single"
        placeholder="Type your message… (Ctrl+Enter to send)"
        :disabled="disabled"
        rows="2"
        @keydown="onKeydown"
      />

      <!-- Bulk-JSON editor -->
      <textarea
        v-else
        ref="textareaRef"
        v-model="jsonText"
        class="chat-input__textarea chat-input__textarea--json"
        :class="{ 'chat-input__textarea--error': jsonError }"
        placeholder='[{ "task_id": "text", "prompt": "…" }, …]'
        :disabled="disabled"
        rows="5"
        spellcheck="false"
        @keydown="onKeydown"
      />
    </div>

    <!-- Footer: hint + submit button -->
    <div class="chat-input__footer">
      <span class="chat-input__hint">
        {{ bulkMode ? 'JSON array of tasks' : 'Ctrl+Enter to send' }}
      </span>
      <button
        class="btn btn-primary"
        :disabled="disabled || (bulkMode && !!jsonError)"
        @click="handleSubmit"
      >
        {{ bulkMode ? '▶ Run Batch' : '▶ Send' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.chat-input {
  display: flex;
  flex-direction: column;
}
.chat-input__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  padding: var(--spacing-xs) var(--spacing-md);
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-tertiary);
}
.chat-input__mode-label {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  font-size: var(--font-size-xs);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--text-secondary);
}
.chat-input__error-badge {
  font-size: var(--font-size-xs);
  color: var(--accent-red);
  font-family: var(--font-mono);
}
.chat-input__body {
  padding: var(--spacing-sm) var(--spacing-md);
}
.chat-input__textarea {
  width: 100%;
  font-family: var(--font-mono);
  font-size: var(--font-size-sm);
  color: var(--text-primary);
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: var(--spacing-md);
  outline: none;
  resize: vertical;
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast);
}
.chat-input__textarea:focus {
  border-color: var(--accent-blue);
  box-shadow: var(--glow-blue);
}
.chat-input__textarea--single {
  min-height: 40px;
  padding: var(--spacing-sm) var(--spacing-md);
}
.chat-input__textarea--json {
  min-height: 100px;
  line-height: 1.6;
  tab-size: 2;
  padding: var(--spacing-sm) var(--spacing-md);
}
.chat-input__textarea--error {
  border-color: var(--accent-red) !important;
  box-shadow: var(--glow-red) !important;
}
.chat-input__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--spacing-xs) var(--spacing-md);
  border-top: 1px solid var(--border-color);
}
.chat-input__hint {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
}
</style>