<script setup>
/**
 * App.vue
 *
 * Root component — wires together the entire Looma Assistant frontend.
 *
 * Layout:
 *   ┌──────────────────────────────────────────────┐
 *   │  PerformanceStats  (top bar / collapsible)   │
 *   ├──────────────────────┬───────────────────────┤
 *   │                      │                       │
 *   │  ChatBotViewCard     │  BatchRunnerViewCard  │
 *   │  (single prompt)     │  (bulk JSON)          │
 *   │                      │                       │
 *   └──────────────────────┴───────────────────────┘
 *
 * Uses Composition API (script setup) throughout.
 */

import { ref, computed } from 'vue'
import PerformanceStats from './components/PerformanceStats.vue'
import ChatBotViewCard from './components/ChatBotViewCard.vue'
import BatchRunnerViewCard from './components/BatchRunnerViewCard.vue'

/** Toggle the performance stats panel visibility. */
const showPerformance = ref(true)

/** API URL for display in the sidebar. */
const apiUrl = computed(() => import.meta.env.VITE_API_BASE_URL || 'localhost:8080/api')
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

      <!-- Chat + Batch cards side by side -->
      <div class="app-content">
        <div class="app-cards">
          <div class="app-cards__item">
            <ChatBotViewCard />
          </div>
          <div class="app-cards__item">
            <BatchRunnerViewCard />
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
/* ---------- Sidebar brand ---------- */
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

/* ---------- Sidebar nav ---------- */
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

/* ---------- Performance stats panel ---------- */
.app-performance {
  flex-shrink: 0;
  border-bottom: 1px solid var(--border-color);
  max-height: 30vh;
  overflow-y: auto;
}

/* ---------- Two-column card layout ---------- */
.app-cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--spacing-lg);
  flex: 1;
  min-height: 0;
}
.app-cards__item {
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.app-cards__item:last-child {
  margin-bottom: 0;
}

@media (max-width: 1024px) {
  .app-cards {
    grid-template-columns: 1fr;
  }
}
</style>