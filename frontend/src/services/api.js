import axios from 'axios'
import {
  mockSendPrompt,
  mockSendBatch,
  mockFetchPerformanceStats,
  MOCK_TELEMETRY_LOG,
} from './mockData.js'

// ---------------------------------------------------------------------------
// Demo mode flag — set VITE_DEMO_MODE=true in .env to use mock data
// ---------------------------------------------------------------------------

const IS_DEMO_MODE = import.meta.env.VITE_DEMO_MODE === 'true'

// ---------------------------------------------------------------------------
// API Client — Axios instance with request/response interceptors
// ---------------------------------------------------------------------------

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 120_000, // 2 minutes
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  },
})

// ---------------------------------------------------------------------------
// Telemetry callback — injected by PerformanceStats.vue at mount
// ---------------------------------------------------------------------------

/**
 * External handler for telemetry data.
 * PerformanceStats.vue sets this via `setTelemetryHandler(fn)` so that the
 * response interceptor can push execution-time snapshots directly into the
 * performance metrics store.
 *
 * @type {(data: TelemetryPayload) => void}
 */
let telemetryHandler = null

/**
 * Register a callback that receives telemetry payloads after every API call.
 * Called automatically by PerformanceStats.vue on mount.
 *
 * @param {(payload: TelemetryPayload) => void} handler
 */
export function setTelemetryHandler(handler) {
  telemetryHandler = handler

  // In demo mode, flush pre-built telemetry entries so PerformanceStats
  // shows data immediately on first render.
  if (IS_DEMO_MODE && handler) {
    MOCK_TELEMETRY_LOG.forEach((entry) => handler(entry))
  }
}

/**
 * @typedef {Object} TelemetryPayload
 * @property {string}   endpoint     - The URL path that was called
 * @property {string}   method       - HTTP method (GET, POST, …)
 * @property {number}   statusCode   - HTTP response status
 * @property {number}   durationMs   - Wall-clock execution time in ms
 * @property {string}   timestamp    - ISO-8601 timestamp of the request start
 * @property {boolean}  success      - Whether the status was 2xx
 */

// ---------------------------------------------------------------------------
// Request interceptor — stamps start time for telemetry
// ---------------------------------------------------------------------------

apiClient.interceptors.request.use(
  (config) => {
    config.metadata = { startTime: performance.now() }
    return config
  },
  (error) => Promise.reject(error),
)

// ---------------------------------------------------------------------------
// Response interceptor — captures telemetry & fires to PerformanceStats
// ---------------------------------------------------------------------------

apiClient.interceptors.response.use(
  (response) => {
    const { config } = response
    const startTime = config.metadata?.startTime ?? performance.now()
    const durationMs = Math.round(performance.now() - startTime)

    const payload = {
      endpoint: config.url || 'unknown',
      method: (config.method || 'GET').toUpperCase(),
      statusCode: response.status,
      durationMs,
      timestamp: new Date().toISOString(),
      success: response.status >= 200 && response.status < 300,
    }

    if (typeof telemetryHandler === 'function') {
      telemetryHandler(payload)
    }

    return response
  },
  (error) => {
    if (error.config) {
      const { config } = error
      const startTime = config.metadata?.startTime ?? performance.now()
      const durationMs = Math.round(performance.now() - startTime)

      const payload = {
        endpoint: config.url || 'unknown',
        method: (config.method || 'GET').toUpperCase(),
        statusCode: error.response?.status ?? 0,
        durationMs,
        timestamp: new Date().toISOString(),
        success: false,
      }

      if (typeof telemetryHandler === 'function') {
        telemetryHandler(payload)
      }
    }

    return Promise.reject(error)
  },
)

// ---------------------------------------------------------------------------
// Convenience helpers — single prompt & batch JSON
// ---------------------------------------------------------------------------

/**
 * Send a single prompt string to the chatbot endpoint.
 * The backend routes the message to the appropriate actor via its internal
 * TaskSupervisorActor — no actor field is sent from the frontend.
 *
 * @param {string} prompt  - The user's message
 * @param {string} [taskId] - Optional task type hint (defaults to "text")
 * @returns {Promise<import('axios').AxiosResponse>}
 */
export function sendPrompt(prompt, taskId = 'text') {
  if (IS_DEMO_MODE) {
    return mockSendPrompt(prompt, taskId)
  }
  return apiClient.post('/agent/chat', { message: prompt, task_id: taskId })
}

/**
 * Send a bulk JSON array of tasks for batch evaluation.
 *
 * @param {Array<{ prompt: string, actor?: string }>} tasks
 * @returns {Promise<import('axios').AxiosResponse>}
 */
export function sendBatch(tasks) {
  if (IS_DEMO_MODE) {
    return mockSendBatch(tasks)
  }
  return apiClient.post('/agent/batch', tasks)
}

/**
 * Fetch performance / health snapshot from the backend.
 *
 * @returns {Promise<import('axios').AxiosResponse>}
 */
export function fetchPerformanceStats() {
  if (IS_DEMO_MODE) {
    return mockFetchPerformanceStats()
  }
  return apiClient.get('/metrics')
}

export default apiClient