<script setup>
/**
 * ChatMessage.vue
 *
 * Renders a single message bubble color-coded by the active worker actor route.
 * The `actor` prop determines which accent colour is applied to the bubble border
 * and the little actor badge.
 *
 * Props
 * -----
 * - message  : string  – the text content of the message
 * - actor    : string  – worker route identifier (default, worker-a..worker-e)
 * - isUser   : boolean – if true the bubble is aligned to the right (user side)
 * - timestamp: string  – ISO-8601 timestamp (optional)
 * - loading  : boolean – when true shows a typing-animation placeholder
 */

import { computed } from 'vue'

const props = defineProps({
  message: { type: String, default: '' },
  actor: { type: String, default: 'default' },
  isUser: { type: Boolean, default: false },
  timestamp: { type: String, default: null },
  loading: { type: Boolean, default: false },
})

/**
 * Map of actor names → CSS colour variables.
 * Keys match the backend actor class names returned in API responses.
 */
const ACTOR_COLORS = {
  default: 'var(--msg-default)',
  TaskSupervisorActor: 'var(--msg-supervisor)',
  CodeWorkerActor: 'var(--msg-coder)',
  StructuredTextActor: 'var(--msg-structured)',
  ReasoningActor: 'var(--msg-reasoning)',
  FactualActor: 'var(--msg-factual)',
  LabelSentimentActor: 'var(--msg-label)',
}

/** Resolved accent colour for the current actor. */
const bubbleColor = computed(() => ACTOR_COLORS[props.actor] ?? ACTOR_COLORS.default)

/** Human-readable actor label for the badge. */
const actorLabel = computed(() => {
  if (props.actor === 'default') return 'SUPERVISOR'
  return props.actor.replace('Actor', '').replace(/([A-Z])/g, ' $1').trim().toUpperCase()
})

/** Formatted timestamp for display. */
const formattedTime = computed(() => {
  if (!props.timestamp) return ''
  const d = new Date(props.timestamp)
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
})

/**
 * Maps an actor name to a CSS badge class suffix.
 */
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

/** Emitted when the user clicks the "copy message" action. */
const emit = defineEmits(['copy'])
</script>

<template>
  <div
    class="chat-message"
    :class="{ 'chat-message--user': isUser, 'chat-message--ai': !isUser }"
  >
    <!-- Actor badge (AI side only) -->
    <div v-if="!isUser" class="chat-message__badge">
      <span
        class="badge"
        :class="`badge-${actorToBadgeClass(actor)}`"
      >
        {{ actorLabel }}
      </span>
    </div>

    <!-- Message bubble -->
    <div
      class="chat-message__bubble"
      :class="{ 'chat-message__bubble--loading': loading }"
      :style="{ borderColor: bubbleColor }"
    >
      <!-- Loading skeleton -->
      <div v-if="loading" class="chat-message__typing">
        <span class="typing-dot" />
        <span class="typing-dot" />
        <span class="typing-dot" />
      </div>

      <!-- Actual message content -->
      <p v-else class="chat-message__text">{{ message }}</p>

      <!-- Timestamp + actions row -->
      <div v-if="!loading" class="chat-message__meta">
        <span v-if="formattedTime" class="chat-message__time">{{ formattedTime }}</span>
        <button
          class="chat-message__copy btn btn-ghost btn-sm"
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
/* ---------- Message row ---------- */
.chat-message {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
  max-width: 80%;
  animation: fadeIn 0.25s ease;
}
.chat-message--ai {
  align-self: flex-start;
}
.chat-message--user {
  align-self: flex-end;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ---------- Actor badge ---------- */
.chat-message__badge {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  margin-bottom: 2px;
}

/* ---------- Bubble ---------- */
.chat-message__bubble {
  position: relative;
  padding: var(--spacing-md) var(--spacing-lg);
  border-radius: var(--radius-lg);
  border: 1px solid var(--msg-default);
  background: var(--bg-tertiary);
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast);
}
.chat-message--ai .chat-message__bubble {
  border-top-left-radius: var(--spacing-xs);
}
.chat-message--user .chat-message__bubble {
  border-top-right-radius: var(--spacing-xs);
  border-color: var(--accent-blue);
  background: rgba(88, 166, 255, 0.08);
}

.chat-message__text {
  font-size: var(--font-size-md);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

/* ---------- Typing animation ---------- */
.chat-message__typing {
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
.typing-dot:nth-child(2) {
  animation-delay: 0.2s;
}
.typing-dot:nth-child(3) {
  animation-delay: 0.4s;
}
@keyframes typingBounce {
  0%, 60%, 100% {
    transform: translateY(0);
    opacity: 0.4;
  }
  30% {
    transform: translateY(-6px);
    opacity: 1;
  }
}

/* ---------- Meta row ---------- */
.chat-message__meta {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--spacing-sm);
  margin-top: var(--spacing-sm);
}
.chat-message__time {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  font-family: var(--font-mono);
}
.chat-message__copy {
  opacity: 0;
  transition: opacity var(--transition-fast);
}
.chat-message__bubble:hover .chat-message__copy {
  opacity: 1;
}
</style>