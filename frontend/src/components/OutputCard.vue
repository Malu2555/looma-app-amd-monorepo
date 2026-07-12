<script setup>
/**
 * OutputCard.vue
 *
 * A single response/result card — the UI block for any output.
 * Color-coded by actor, with loading skeleton, error state, and copy action.
 *
 * Props
 * -----
 * - message  : string  – the text content
 * - actor    : string  – worker route identifier (default, TaskSupervisorActor, …)
 * - isUser   : boolean – if true aligns right (user side)
 * - timestamp: string  – ISO-8601 timestamp (optional)
 * - loading  : boolean – when true shows a typing-animation placeholder
 * - error    : boolean – when true shows error styling
 */

import { computed } from 'vue'

const props = defineProps({
  message: { type: String, default: '' },
  actor: { type: String, default: 'default' },
  isUser: { type: Boolean, default: false },
  timestamp: { type: String, default: null },
  loading: { type: Boolean, default: false },
  error: { type: Boolean, default: false },
})

const emit = defineEmits(['copy'])

const ACTOR_COLORS = {
  default: 'var(--msg-default)',
  TaskSupervisorActor: 'var(--msg-supervisor)',
  CodeWorkerActor: 'var(--msg-coder)',
  StructuredTextActor: 'var(--msg-structured)',
  ReasoningActor: 'var(--msg-reasoning)',
  FactualActor: 'var(--msg-factual)',
  LabelSentimentActor: 'var(--msg-label)',
}

const bubbleColor = computed(() => ACTOR_COLORS[props.actor] ?? ACTOR_COLORS.default)

const actorLabel = computed(() => {
  if (props.actor === 'default') return 'SUPERVISOR'
  return props.actor.replace('Actor', '').replace(/([A-Z])/g, ' $1').trim().toUpperCase()
})

const formattedTime = computed(() => {
  if (!props.timestamp) return ''
  const d = new Date(props.timestamp)
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
})

function actorToBadgeClass(actor) {
  const map = {
    default: 'blue',
    TaskSupervisorActor: 'blue',
    CodeWorkerActor: 'green',
    StructuredTextActor: 'orange',
    ReasoningActor: 'purple',
    FactualActor: 'cyan',
    LabelSentimentActor: 'purple',
  }
  return map[actor] ?? 'blue'
}
</script>

<template>
  <div
    class="output-card"
    :class="{
      'output-card--user': isUser,
      'output-card--ai': !isUser,
      'output-card--error': error,
    }"
  >
    <!-- Actor badge (AI side only) -->
    <div v-if="!isUser" class="output-card__badge">
      <span class="badge" :class="`badge-${actorToBadgeClass(actor)}`">
        {{ actorLabel }}
      </span>
    </div>

    <!-- Message bubble -->
    <div
      class="output-card__bubble"
      :class="{
        'output-card__bubble--loading': loading,
        'output-card__bubble--error': error,
      }"
      :style="{ borderColor: bubbleColor }"
    >
      <!-- Loading skeleton -->
      <div v-if="loading" class="output-card__typing">
        <span class="typing-dot" />
        <span class="typing-dot" />
        <span class="typing-dot" />
      </div>

      <!-- Error icon -->
      <div v-else-if="error" class="output-card__error-icon">⚠️</div>

      <!-- Actual message content -->
      <p v-else class="output-card__text">{{ message }}</p>

      <!-- Timestamp + actions row -->
      <div v-if="!loading" class="output-card__meta">
        <span v-if="formattedTime" class="output-card__time">{{ formattedTime }}</span>
        <button
          class="output-card__copy btn btn-ghost btn-sm"
          title="Copy message"
          @click="emit('copy', message)"
        >
          📋
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.output-card {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
  max-width: 80%;
  animation: fadeIn 0.25s ease;
}
.output-card--ai {
  align-self: flex-start;
}
.output-card--user {
  align-self: flex-end;
}
.output-card--error {
  opacity: 0.9;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

.output-card__badge {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  margin-bottom: 2px;
}

.output-card__bubble {
  position: relative;
  padding: var(--spacing-md) var(--spacing-lg);
  border-radius: var(--radius-lg);
  border: 1px solid var(--msg-default);
  background: var(--bg-tertiary);
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast);
}
.output-card--ai .output-card__bubble {
  border-top-left-radius: var(--spacing-xs);
}
.output-card--user .output-card__bubble {
  border-top-right-radius: var(--spacing-xs);
  border-color: var(--accent-blue);
  background: rgba(88, 166, 255, 0.08);
}
.output-card__bubble--error {
  border-color: var(--accent-red) !important;
  background: rgba(248, 81, 73, 0.08);
  box-shadow: var(--glow-red);
}

.output-card__text {
  font-size: var(--font-size-md);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.output-card__error-icon {
  font-size: var(--font-size-lg);
  line-height: 1;
}

/* ---------- Typing animation ---------- */
.output-card__typing {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 0;
}
.typing-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--text-muted);
  animation: typingBounce 1.4s ease-in-out infinite;
}
.typing-dot:nth-child(2) { animation-delay: 0.2s; }
.typing-dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes typingBounce {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.4; }
  30% { transform: translateY(-6px); opacity: 1; }
}

/* ---------- Meta row ---------- */
.output-card__meta {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--spacing-sm);
  margin-top: var(--spacing-sm);
}
.output-card__time {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  font-family: var(--font-mono);
}
.output-card__copy {
  opacity: 0;
  transition: opacity var(--transition-fast);
}
.output-card__bubble:hover .output-card__copy {
  opacity: 1;
}
</style>