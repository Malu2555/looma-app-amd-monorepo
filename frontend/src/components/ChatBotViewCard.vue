<script setup>
/**
 * ChatBotViewCard.vue
 *
 * Chat message list card — uses OutputView + OutputCard internally.
 * The parent (App.vue) calls `sendPrompt(prompt)` via template ref.
 */

import { ref } from 'vue'
import OutputView from './OutputView.vue'
import { sendPrompt } from '../services/api.js'

const outputViewRef = ref(null)

/** Default actor label for AI responses. */
const aiActor = ref('TaskSupervisorActor')

/**
 * Called by the parent (App.vue) when the shared ChatInput submits a prompt.
 */
async function sendPromptHandler(prompt) {
  const view = outputViewRef.value
  if (!view) return

  // Push user message
  view.addOutput({
    role: 'user',
    message: prompt,
    timestamp: new Date().toISOString(),
  })

  // Push a loading placeholder
  view.setLoading(true)

  try {
    const { data } = await sendPrompt(prompt)
    const answer = data.answer ?? data.response ?? data.message ?? JSON.stringify(data)

    // Replace the loading message with the actual response
    view.replaceLast({
      role: 'ai',
      message: answer,
      actor: aiActor.value,
      timestamp: new Date().toISOString(),
      loading: false,
    })
  } catch (err) {
    view.replaceLast({
      role: 'ai',
      message: `Error: ${err.message}`,
      actor: 'default',
      timestamp: new Date().toISOString(),
      loading: false,
      error: true,
    })
  } finally {
    view.setLoading(false)
  }
}

defineExpose({ sendPrompt: sendPromptHandler })
</script>

<template>
  <OutputView
    ref="outputViewRef"
    title="Chat Debug — Single Prompt"
    badge-text="PROMPT"
    badge-class="blue"
    :actor-label="aiActor"
    empty-text="Send a prompt to start debugging."
  />
</template>