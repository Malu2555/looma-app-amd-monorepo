<script setup>
/**
 * ChatBotViewCard.vue
 *
 * An interactive chat interface card for single-prompt debugging.
 *
 * The card wraps:
 *   - A scrollable message list (ChatMessage components)
 *   - A ChatInput in single-prompt mode
 *   - Internal state for conversation history
 *
 * It exposes the single-prompt API via `sendPrompt()` and handles
 * loading / error states. Each AI response is color-coded by the
 * worker actor route returned from the backend.
 */

import { ref } from 'vue'
import ChatMessage from './ChatMessage.vue'
import ChatInput from './ChatInput.vue'
import { sendPrompt } from '../services/api.js'

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

/**
 * @typedef {Object} ChatEntry
 * @property {'user'|'ai'} role
 * @property {string}       message
 * @property {string}       [actor]
 * @property {string}       [timestamp]
 * @property {boolean}      [loading]
 */

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

/** @type {import('vue').Ref<ChatEntry[]>} */
const messages = ref([])

const isWaiting = ref(false)

/** Reference to the message list container for auto-scroll. */
const messageListRef = ref(null)

/** Default actor label for AI responses. */
const aiActor = ref('TaskSupervisorActor')

// ---------------------------------------------------------------------------
// Handlers
// ---------------------------------------------------------------------------

/**
 * Called when ChatInput emits 'submit' (single prompt).
 * Sends the prompt to the backend, pushes a user message and then
 * the AI response into the conversation.
 */
async function handleSend(prompt) {
  if (isWaiting.value) return

  // Push user message
  messages.value.push({
    role: 'user',
    message: prompt,
    timestamp: new Date().toISOString(),
  })

  // Push a loading placeholder
  messages.value.push({
    role: 'ai',
    message: '',
    actor: aiActor.value,
    timestamp: null,
    loading: true,
  })
  isWaiting.value = true
  scrollToBottom()

  try {
    const { data } = await sendPrompt(prompt)

    // Replace the loading message with the actual response
    const lastIdx = messages.value.length - 1
    messages.value[lastIdx] = {
      role: 'ai',
      message: data.answer ?? data.response ?? data.message ?? JSON.stringify(data),
      actor: aiActor.value,
      timestamp: new Date().toISOString(),
      loading: false,
    }
  } catch (err) {
    const lastIdx = messages.value.length - 1
    messages.value[lastIdx] = {
      role: 'ai',
      message: `⚠️ Error: ${err.message}`,
      actor: 'default',
      timestamp: new Date().toISOString(),
      loading: false,
    }
  } finally {
    isWaiting.value = false
    scrollToBottom()
  }
}

/** Copy a message text to the clipboard. */
function copyMessage(text) {
  navigator.clipboard.writeText(text).catch(() => {
    // Fallback — silently ignore
  })
}

/** Clear the current conversation. */
function clearConversation() {
  messages.value = []
}

/** Auto-scroll the message list to the bottom. */
function scrollToBottom() {
  setTimeout(() => {
    if (messageListRef.value) {
      messageListRef.value.scrollTop = messageListRef.value.scrollHeight
    }
  }, 50)
}
</script>

<template>
  <div class="chatbot-card card">
    <!-- Header -->
    <div class="card-header">
      <h4>
        <span class="pulse-dot" :class="isWaiting ? 'loading' : 'active'" />
        Chat Debug — Single Prompt
      </h4>
      <div class="card-header__actions">
        <span class="badge badge-blue">PROMPT</span>
        <button class="btn btn-ghost btn-sm" title="Clear conversation" @click="clearConversation">
          🗑️
        </button>
      </div>
    </div>

    <!-- Message list -->
    <div ref="messageListRef" class="chatbot-card__messages">
      <template v-if="messages.length === 0">
        <div class="chatbot-card__empty">
          <span class="text-muted">Send a prompt to start debugging.</span>
        </div>
      </template>

      <template v-for="(entry, idx) in messages" :key="idx">
        <ChatMessage
          :message="entry.message"
          :actor="entry.actor ?? 'default'"
          :is-user="entry.role === 'user'"
          :timestamp="entry.timestamp"
          :loading="entry.loading ?? false"
          @copy="copyMessage"
        />
      </template>
    </div>

    <!-- Input footer -->
    <div class="chatbot-card__input">
      <ChatInput :disabled="isWaiting" :bulk-mode="false" @submit="handleSend" />
    </div>
  </div>
</template>

<style scoped>
.chatbot-card {
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

/* ---------- Message list (scrollable) ---------- */
.chatbot-card__messages {
  flex: 1;
  overflow-y: auto;
  padding: var(--spacing-lg);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
  background: var(--bg-primary);
}
.chatbot-card__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 120px;
  font-size: var(--font-size-sm);
}

/* ---------- Input area pinned to bottom ---------- */
.chatbot-card__input {
  flex-shrink: 0;
  border-top: 1px solid var(--border-color);
}
</style>