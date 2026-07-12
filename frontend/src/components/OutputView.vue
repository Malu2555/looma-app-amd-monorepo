<script setup>
/**
 * OutputView.vue
 *
 * Technical container component that manages a scrollable list of outputs.
 * Uses OutputCard internally for each item.
 *
 * Props
 * -----
 * - title       : string  – card header title
 * - badgeText   : string  – badge label in the header (e.g. "PROMPT", "BATCH")
 * - badgeClass  : string  – badge CSS class suffix (e.g. "blue", "orange")
 * - actorLabel  : string  – default actor name for AI responses
 * - emptyText   : string  – placeholder text when there are no outputs
 * - loading     : boolean – whether a loading state is active
 *
 * Exposes
 * -------
 * - addOutput(entry)     – push a new output entry
 * - setLoading(v)        – set loading state
 * - clearAll()           – clear all outputs
 * - scrollToBottom()     – auto-scroll to the latest entry
 *
 * Events
 * ------
 * - @copy(text) – emitted when user copies an output
 */

import { ref, nextTick } from 'vue'
import OutputCard from './OutputCard.vue'

const props = defineProps({
  title: { type: String, default: 'Output' },
  badgeText: { type: String, default: 'OUTPUT' },
  badgeClass: { type: String, default: 'blue' },
  actorLabel: { type: String, default: 'TaskSupervisorActor' },
  emptyText: { type: String, default: 'No outputs yet.' },
  loading: { type: Boolean, default: false },
})

const emit = defineEmits(['copy'])

/** @type {import('vue').Ref<Array<{role:string, message:string, actor?:string, timestamp?:string|null, loading?:boolean, error?:boolean}>>} */
const entries = ref([])

/** Reference to the scrollable container. */
const listRef = ref(null)

/** Add a single output entry. */
function addOutput(entry) {
  entries.value.push(entry)
  scrollToBottom()
}

/** Replace the last entry (e.g. swap loading placeholder with real response). */
function replaceLast(entry) {
  if (entries.value.length > 0) {
    entries.value[entries.value.length - 1] = entry
  }
  scrollToBottom()
}

/** Set or clear the loading indicator. */
function setLoading(v) {
  if (v) {
    entries.value.push({
      role: 'ai',
      message: '',
      actor: props.actorLabel,
      timestamp: null,
      loading: true,
    })
  } else {
    const idx = entries.value.findIndex((e) => e.loading)
    if (idx !== -1) entries.value.splice(idx, 1)
  }
  scrollToBottom()
}

/** Clear all entries. */
function clearAll() {
  entries.value = []
}

/** Auto-scroll to the bottom. */
function scrollToBottom() {
  nextTick(() => {
    if (listRef.value) {
      listRef.value.scrollTop = listRef.value.scrollHeight
    }
  })
}

/** Copy handler. */
function handleCopy(text) {
  navigator.clipboard.writeText(text).catch(() => {})
  emit('copy', text)
}

defineExpose({ addOutput, replaceLast, setLoading, clearAll, scrollToBottom })
</script>

<template>
  <div class="output-view card">
    <!-- Header -->
    <div class="card-header">
      <h4>
        <span
          class="pulse-dot"
          :class="loading ? 'loading' : entries.length > 0 ? 'active' : 'error'"
        />
        {{ title }}
      </h4>
      <div class="output-view__header-actions">
        <span class="badge" :class="`badge-${badgeClass}`">{{ badgeText }}</span>
        <slot name="header-actions" />
        <button
          v-if="entries.length > 0"
          class="btn btn-ghost btn-sm"
          title="Clear all"
          @click="clearAll"
        >
          🗑️
        </button>
      </div>
    </div>

    <!-- Output list -->
    <div ref="listRef" class="output-view__list">
      <template v-if="entries.length === 0">
        <div class="output-view__empty">
          <span class="text-muted">{{ emptyText }}</span>
        </div>
      </template>

      <template v-for="(entry, idx) in entries" :key="idx">
        <OutputCard
          :message="entry.message"
          :actor="entry.actor ?? 'default'"
          :is-user="entry.role === 'user'"
          :timestamp="entry.timestamp"
          :loading="entry.loading ?? false"
          :error="entry.error ?? false"
          @copy="handleCopy"
        />
      </template>
    </div>
  </div>
</template>

<style scoped>
.output-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 400px;
}
.output-view__header-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

/* ---------- Scrollable list ---------- */
.output-view__list {
  flex: 1;
  overflow-y: auto;
  padding: var(--spacing-lg);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
  background: var(--bg-primary);
}
.output-view__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 120px;
  font-size: var(--font-size-sm);
}
</style>