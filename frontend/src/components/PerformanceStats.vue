<script setup>
/**
 * PerformanceStats.vue
 *
 * UI block that displays real-time system performance metrics.
 *
 * On mount it registers itself as the telemetry handler in the API service
 * so that every API response automatically pushes a telemetry snapshot here.
 *
 * It also periodically polls the backend for a full performance snapshot.
 *
 * The component extracts raw numeric snapshots and transforms them into
 * visual components (gauges, sparklines, stat cards) to show system efficiency.
 */

import { ref, computed, onMounted, onUnmounted } from 'vue'
import { setTelemetryHandler, fetchPerformanceStats } from '../services/api.js'

// ---------------------------------------------------------------------------
// Telemetry accumulator (from response interceptor)
// ---------------------------------------------------------------------------

/**
 * @type {import('vue').Ref<Array<import('../services/api.js').TelemetryPayload>>}
 */
const telemetryLog = ref([])
const MAX_LOG_ENTRIES = 100

/** Callback registered with the API service. */
function onTelemetry(payload) {
  telemetryLog.value = [payload, ...telemetryLog.value].slice(0, MAX_LOG_ENTRIES)
}

// ---------------------------------------------------------------------------
// Derived metrics from telemetry log
// ---------------------------------------------------------------------------

const totalRequests = computed(() => telemetryLog.value.length)
const successfulRequests = computed(() => telemetryLog.value.filter((t) => t.success).length)
const failedRequests = computed(() => totalRequests.value - successfulRequests.value)

const avgDurationMs = computed(() => {
  if (totalRequests.value === 0) return 0
  const sum = telemetryLog.value.reduce((acc, t) => acc + t.durationMs, 0)
  return Math.round(sum / totalRequests.value)
})

const maxDurationMs = computed(() => {
  if (totalRequests.value === 0) return 0
  return Math.max(...telemetryLog.value.map((t) => t.durationMs))
})

const minDurationMs = computed(() => {
  if (totalRequests.value === 0) return 0
  return Math.min(...telemetryLog.value.map((t) => t.durationMs))
})

const successRate = computed(() => {
  if (totalRequests.value === 0) return 100
  return Math.round((successfulRequests.value / totalRequests.value) * 100)
})

// ---------------------------------------------------------------------------
// Backend system snapshot (polled)
// ---------------------------------------------------------------------------

const systemStats = ref({
  total_requests: 0,
  successful_requests: 0,
  failed_requests: 0,
  total_tokens: 0,
  total_latency_ms: 0,
  avg_latency_ms: 0,
  success_rate: 0,
  uptime_ms: 0,
  workers: {},
})

const isPolling = ref(false)
let pollInterval = null

async function pollSystemStats() {
  try {
    const { data } = await fetchPerformanceStats()
    systemStats.value = {
      total_requests: data.total_requests ?? 0,
      successful_requests: data.successful_requests ?? 0,
      failed_requests: data.failed_requests ?? 0,
      total_tokens: data.total_tokens ?? 0,
      total_latency_ms: data.total_latency_ms ?? 0,
      avg_latency_ms: data.avg_latency_ms ?? 0,
      success_rate: data.success_rate ?? 0,
      uptime_ms: data.uptime_ms ?? 0,
      workers: data.workers ?? {},
    }
  } catch {
    // Silently fail — backend may not be running
  }
}

// ---------------------------------------------------------------------------
// Visual helpers
// ---------------------------------------------------------------------------

/**
 * Returns a colour class for a percentage gauge value.
 */
function gaugeColor(value) {
  if (value >= 80) return 'gauge--danger'
  if (value >= 50) return 'gauge--warning'
  return 'gauge--safe'
}

/**
 * Formats a duration in ms to a human-readable string.
 */
function formatDuration(ms) {
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(2)}s`
}

// ---------------------------------------------------------------------------
// Lifecycle
// ---------------------------------------------------------------------------

onMounted(() => {
  // Register as the telemetry sink for the API interceptors
  setTelemetryHandler(onTelemetry)

  // Start polling backend stats every 5 seconds
  pollSystemStats()
  pollInterval = setInterval(pollSystemStats, 5000)
  isPolling.value = true
})

onUnmounted(() => {
  // Unregister telemetry handler
  setTelemetryHandler(null)

  // Stop polling
  if (pollInterval) {
    clearInterval(pollInterval)
    pollInterval = null
  }
  isPolling.value = false
})
</script>

<template>
  <div class="perf-stats card">
    <!-- Header -->
    <div class="card-header">
      <h4>
        <span class="pulse-dot" :class="isPolling ? 'active' : 'error'" />
        System Performance
      </h4>
      <span class="badge badge-blue">LIVE</span>
    </div>

    <div class="card-body perf-stats__body">
      <!-- Compact row: gauges + stat cards side by side -->
      <div class="perf-stats__row">
        <!-- Success rate gauge -->
        <div class="perf-stats__gauge-compact">
          <span class="perf-stats__gauge-label">Success</span>
          <div class="gauge__track">
            <div
              class="gauge__fill"
              :class="gaugeColor(systemStats.success_rate * 100)"
              :style="{ width: Math.round(systemStats.success_rate * 100) + '%' }"
            />
          </div>
          <span class="perf-stats__gauge-value">{{ Math.round(systemStats.success_rate * 100) }}%</span>
        </div>

        <!-- Avg Latency gauge -->
        <div class="perf-stats__gauge-compact">
          <span class="perf-stats__gauge-label">Latency</span>
          <div class="gauge__track">
            <div
              class="gauge__fill gauge--safe"
              :style="{ width: Math.min(systemStats.avg_latency_ms / 20, 100) + '%' }"
            />
          </div>
          <span class="perf-stats__gauge-value">{{ formatDuration(systemStats.avg_latency_ms) }}</span>
        </div>

        <!-- Total Tokens -->
        <div class="perf-stats__stat-compact">
          <span class="perf-stats__stat-value">{{ systemStats.total_tokens }}</span>
          <span class="perf-stats__stat-label">Tokens</span>
        </div>

        <!-- OK / Failed -->
        <div class="perf-stats__stat-compact">
          <span class="perf-stats__stat-value">{{ systemStats.successful_requests }}/{{ systemStats.failed_requests }}</span>
          <span class="perf-stats__stat-label">OK/Fail</span>
        </div>

        <!-- Uptime -->
        <div class="perf-stats__stat-compact">
          <span class="perf-stats__stat-value perf-stats__stat-value--sm">{{ formatDuration(systemStats.uptime_ms) }}</span>
          <span class="perf-stats__stat-label">Uptime</span>
        </div>

        <!-- Requests -->
        <div class="perf-stats__stat-compact">
          <span class="perf-stats__stat-value">{{ systemStats.total_requests }}</span>
          <span class="perf-stats__stat-label">Requests</span>
        </div>
      </div>

      <!-- Telemetry chips row -->
      <div class="perf-stats__telemetry-row">
        <span class="perf-stats__telemetry-label">Telemetry</span>
        <div class="perf-stats__chips">
          <span class="perf-stats__chip">{{ totalRequests }} total</span>
          <span class="perf-stats__chip perf-stats__chip--success">{{ successfulRequests }} ok</span>
          <span class="perf-stats__chip perf-stats__chip--fail">{{ failedRequests }} fail</span>
          <span class="perf-stats__chip">avg {{ formatDuration(avgDurationMs) }}</span>
          <span class="perf-stats__chip">min {{ formatDuration(minDurationMs) }}</span>
          <span class="perf-stats__chip">max {{ formatDuration(maxDurationMs) }}</span>
          <span class="badge" :class="successRate >= 90 ? 'badge-green' : 'badge-orange'">
            {{ successRate }}%
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.perf-stats {
  display: flex;
  flex-direction: column;
}

/* ---------- Compact body ---------- */
.perf-stats__body {
  padding: var(--spacing-sm) var(--spacing-md) !important;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

/* ---------- Compact row: flex items side by side ---------- */
.perf-stats__row {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  flex-wrap: wrap;
}

/* ---------- Compact gauge ---------- */
.perf-stats__gauge-compact {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  min-width: 140px;
}
.perf-stats__gauge-label {
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
  white-space: nowrap;
  min-width: 44px;
}
.perf-stats__gauge-value {
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  font-weight: 600;
  color: var(--text-primary);
  min-width: 40px;
  text-align: right;
}
.gauge__track {
  flex: 1;
  height: 6px;
  min-width: 60px;
  background: var(--bg-tertiary);
  border-radius: 3px;
  overflow: hidden;
}
.gauge__fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.5s ease, background 0.3s ease;
}
.gauge--safe {
  background: var(--accent-green);
}
.gauge--warning {
  background: var(--accent-orange);
}
.gauge--danger {
  background: var(--accent-red);
}

/* ---------- Compact stat ---------- */
.perf-stats__stat-compact {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 60px;
  padding: var(--spacing-xs) var(--spacing-sm);
  background: var(--bg-tertiary);
  border-radius: var(--radius-md);
  border: 1px solid var(--border-color);
}
.perf-stats__stat-value {
  font-family: var(--font-mono);
  font-size: var(--font-size-md);
  font-weight: 700;
  color: var(--accent-blue);
  line-height: 1.2;
}
.perf-stats__stat-value--sm {
  font-size: var(--font-size-sm);
}
.perf-stats__stat-label {
  font-size: 10px;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

/* ---------- Telemetry row ---------- */
.perf-stats__telemetry-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
  padding-top: var(--spacing-xs);
  border-top: 1px solid var(--border-color);
}
.perf-stats__telemetry-label {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  font-family: var(--font-mono);
  white-space: nowrap;
}
.perf-stats__chips {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  flex-wrap: wrap;
}
.perf-stats__chip {
  font-family: var(--font-mono);
  font-size: 10px;
  padding: 2px 6px;
  background: var(--bg-tertiary);
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-color);
  color: var(--text-secondary);
  white-space: nowrap;
}
.perf-stats__chip--success {
  color: var(--accent-green);
  border-color: rgba(63, 185, 80, 0.3);
}
.perf-stats__chip--fail {
  color: var(--accent-red);
  border-color: rgba(248, 81, 73, 0.3);
}
</style>
