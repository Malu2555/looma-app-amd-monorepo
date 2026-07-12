<script setup>
/**
 * App.vue
 *
 * Root component — wires together the entire Looma Assistant frontend.
 *
 * Terminal-style layout:
 *   ┌──────────────────────────────────────────────┐
 *   │  PerformanceStats  (top bar / collapsible)   │
 *   ├──────────────────────┬───────────────────────┤
 *   │                      │                       │
 *   │  ChatBotViewCard     │  BatchRunnerViewCard  │
 *   │  (scrollable chat)   │  (scrollable results) │
 *   │                      │                       │
 *   ├──────────────────────────────────────────────┤
 *   │  Mode Toggle (Single / Batch) + ChatInput    │
 *   └──────────────────────────────────────────────┘
 *
 * Input is always pinned to the bottom like a terminal prompt.
 * Cards above scroll freely as content is added.
 *
 * Uses Composition API (script setup) throughout.
 */

import { ref, computed } from 'vue'
import PerformanceStats from './components/PerformanceStats.vue'
import ChatBotViewCard from './components/ChatBotViewCard.vue'
import BatchRunnerViewCard from './components/BatchRunnerViewCard.vue'
import ChatInput from './components/ChatInput.vue'

/** Toggle the performance stats panel visibility. */
const showPerformance = ref(true)

/** API URL for display in the sidebar. */
const apiUrl = computed(() => import.meta.env.VITE_API_BASE_URL || 'localhost:8080/api')

/** Current input mode: 'single' or 'batch' */
const inputMode = ref('single')

/** Template refs to child cards for calling exposed methods. */
const chatCardRef = ref(null)
const batchCardRef = ref(null)

/**
 * Called when the shared ChatInput emits 'submit' (single prompt mode).
 * Routes the prompt to ChatBotViewCard's exposed sendPrompt method.
 */
function handleSingleSubmit(prompt) {
  chatCardRef.value?.sendPrompt(prompt)
}

/**
 * Called when the shared ChatInput emits 'submit-batch' (batch mode).
 * Routes the tasks to BatchRunnerViewCard's exposed runBatch method.
 */
function handleBatchSubmit(tasks) {
  batchCardRef.value?.runBatch(tasks)
}
</script>

<template>
  <div class="app-layout">
    <!-- ===== Sidebar / Branding ===== -->
    <aside class="app-sidebar">
      <div class="app-sidebar__brand">
        <span class="app-sidebar__logo">✦</span>
        <div class="app-sidebar__title">
          <h1>Looma</h1>
          <span class="text-muted terminal-text">assistant v0.1</span>
        </div>
      </div>

      <nav class="app-sidebar__nav">
        <button
          class="btn btn-ghost btn-sm app-sidebar__nav-btn"
          :class="{ 'app-sidebar__nav-btn--active': showPerformance }"
          @click="showPerformance = !showPerformance"
        >
          <span class="pulse-dot active" />
          {{ showPerformance ? 'Hide Stats' : 'Show Stats' }}
        </button>
      </nav>

      <div class="app-sidebar__footer">
        <span class="badge badge-blue">API: {{ apiUrl }}</span>
      </div>
    </aside>

    <!-- ===== Main content ===== -->
    <main class="app-main">
      <!-- Performance Stats (collapsible) -->
      <Transition name="slide-up">
        <div v-if="showPerformance" class="app-performance">
          <PerformanceStats />
        </div>
      </Transition>

      <!-- Chat + Batch cards side by side (scrollable area above input) -->
      <div class="app-content">
        <div class="app-cards">
          <div class="app-cards__item">
            <ChatBotViewCard ref="chatCardRef" />
          </div>
          <div class="app-cards__item">
            <BatchRunnerViewCard ref="batchCardRef" />
          </div>
        </div>
      </div>

      <!-- Terminal-style input pinned to bottom -->
      <div class="app-input-area">
        <!-- Mode toggle -->
        <div class="app-input-area__mode-toggle">
          <button
            class="btn btn-sm"
            :class="inputMode === 'single' ? 'btn-primary' : 'btn-ghost'"
            @click="inputMode = 'single'"
          >
            💬 Single Prompt
          </button>
          <button
            class="btn btn-sm"
            :class="inputMode === 'batch' ? 'btn-primary' : 'btn-ghost'"
            @click="inputMode = 'batch'"
          >
            📋 Batch JSON
          </button>
        </div>

        <!-- Shared ChatInput -->
        <ChatInput
          :bulk-mode="inputMode === 'batch'"
          @submit="handleSingleSubmit"
          @submit-batch="handleBatchSubmit"
        />
      </div>
    </main>
  </div>
</template>

<style scoped>
/* ---------- Sidebar brand (flex) ---------- */
.app-sidebar__brand {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-lg);
  border-bottom: 1px solid var(--border-color);
}
.app-sidebar__logo {
  font-size: 28px;
  line-height: 1;
  color: var(--accent-blue);
}
.app-sidebar__title h1 {
  font-size: var(--font-size-lg);
  font-weight: 700;
  line-height: 1.2;
}
.app-sidebar__title span {
  font-size: var(--font-size-xs);
}

/* ---------- Sidebar nav (flex) ---------- */
.app-sidebar__nav {
  flex: 1;
  padding: var(--spacing-md);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}
.app-sidebar__nav-btn {
  justify-content: flex-start;
  width: 100%;
  padding: var(--spacing-sm) var(--spacing-md);
  gap: var(--spacing-sm);
  font-size: var(--font-size-sm);
}
.app-sidebar__nav-btn--active {
  background: var(--bg-tertiary);
  border-color: var(--border-color);
  color: var(--accent-blue);
}

/* ---------- Sidebar footer ---------- */
.app-sidebar__footer {
  padding: var(--spacing-md) var(--spacing-lg);
  border-top: 1px solid var(--border-color);
}

/* ---------- Performance stats panel (flex) ---------- */
.app-performance {
  flex-shrink: 0;
  border-bottom: 1px solid var(--border-color);
  max-height: 30vh;
  overflow-y: auto;
}

/* ---------- Terminal input area pinned to bottom (flex) ---------- */
.app-input-area {
  flex-shrink: 0;
  border-top: 1px solid var(--border-color);
  background: var(--bg-secondary);
}
.app-input-area__mode-toggle {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-lg);
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-tertiary);
}
</style>