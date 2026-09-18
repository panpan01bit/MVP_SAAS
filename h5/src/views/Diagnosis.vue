<template>
  <div class="chat-container">
    <van-nav-bar title="ToolFix Support" fixed>
      <template #left>
        <div style="font-size: 12px; color: #969799;">
          Round {{ session.roundCount || 0 }}/{{ session.maxRounds || 5 }}
        </div>
      </template>
    </van-nav-bar>
    
    <div class="chat-messages" ref="messagesContainer" style="margin-top: 46px;">
      <van-empty v-if="!validated" description="Validating session..." />
      
      <div v-else>
        <div style="text-align: center; padding: 16px 0; color: #969799; font-size: 14px;">
          <div style="font-weight: 600; color: #323233; margin-bottom: 4px;">{{ session.productName }}</div>
          <div>Tell us what's happening with your tool</div>
        </div>
        
        <div v-for="(message, index) in messages" :key="index" 
             :class="['message-item', message.role === 'user' ? 'message-user' : 'message-assistant', message.isHazard ? 'message-hazard' : '']">
          <div class="message-bubble">
            <div style="white-space: pre-wrap;">{{ message.content }}</div>
            <div v-if="message.images && message.images.length > 0" style="margin-top: 8px;">
              <img v-for="(img, idx) in message.images" :key="idx" :src="img" 
                   style="max-width: 100%; border-radius: 8px; margin-bottom: 4px;" />
            </div>
            <div v-if="message.guideUrl" style="margin-top: 12px;">
              <van-button type="success" size="small" @click="openGuide(message.guideUrl)" block>
                View Self-Check Guide
              </van-button>
            </div>
            <div class="message-meta">{{ message.time }}</div>
          </div>
        </div>
        
        <div v-if="loading" class="message-item message-assistant">
          <div class="message-bubble">
            <span class="loading-dots">Analyzing</span>
          </div>
        </div>
        
        <div v-if="transferred" style="text-align: center; padding: 24px; color: #969799;">
          <van-icon name="service" size="48" color="#ee0a24" />
          <div style="margin-top: 12px; font-size: 16px; font-weight: 600; color: #323233;">
            Transferred to Human Support
          </div>
          <div style="margin-top: 8px; font-size: 14px;">
            Our team will contact you within 24 hours
          </div>
        </div>
      </div>
    </div>
    
    <div class="chat-input-area" v-if="validated && !transferred">
      <van-uploader v-model="uploadFiles" :max-count="3" :after-read="afterRead" multiple>
        <van-button icon="photograph" size="small" />
      </van-uploader>
      
      <van-field
        v-model="userInput"
        type="textarea"
        placeholder="Describe the issue..."
        :autosize="{ minHeight: 36, maxHeight: 100 }"
        style="flex: 1;"
      />
      
      <van-button type="primary" size="small" @click="sendMessage" :loading="loading" :disabled="!userInput.trim()">
        <van-icon name="send" />
      </van-button>
    </div>
    
    <van-action-sheet v-model:show="showFeedback" title="How was the support?">
      <div style="padding: 24px;">
        <van-button type="success" size="large" block @click="submitFeedback(true)" icon="like">
          Helpful
        </van-button>
        <van-button type="warning" size="large" block @click="submitFeedback(false)" icon="like-o" style="margin-top: 12px;">
          Not Helpful
        </van-button>
      </div>
    </van-action-sheet>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { showToast, showDialog } from 'vant'
import api from '../api'

const route = useRoute()
const sessionUuid = route.params.sessionUuid
const token = route.query.token

const validated = ref(false)
const session = ref({})
const messages = ref([])
const userInput = ref('')
const uploadFiles = ref([])
const loading = ref(false)
const transferred = ref(false)
const showFeedback = ref(false)
const messagesContainer = ref(null)

const validateSession = async () => {
  if (!token) {
    showToast('Invalid link')
    return
  }
  
  try {
    const response = await api.diagnosis.validate(sessionUuid, token)
    if (response.success && response.data.valid) {
      session.value = response.data
      validated.value = true
      loadMessages()
    } else {
      showToast('Session invalid or expired')
    }
  } catch (error) {
    showToast('Failed to validate session')
  }
}

const loadMessages = async () => {
  try {
    const response = await api.diagnosis.getSession(sessionUuid)
    if (response.success && response.data.messages) {
      messages.value = response.data.messages.map(msg => ({
        role: msg.role.toLowerCase(),
        content: msg.content,
        time: new Date(msg.createdAt).toLocaleTimeString(),
        images: msg.imageUrls ? msg.imageUrls.split(',') : [],
        isHazard: msg.isHazardWarning
      }))
      
      scrollToBottom()
      
      if (response.data.session.transferredToHuman) {
        transferred.value = true
      }
    }
  } catch (error) {
    console.error('Failed to load messages:', error)
  }
}

const afterRead = (file) => {
  console.log('Image uploaded:', file)
}

const sendMessage = async () => {
  if (!userInput.value.trim()) return
  
  const messageText = userInput.value.trim()
  const imageFiles = uploadFiles.value.map(f => f.file)
  
  messages.value.push({
    role: 'user',
    content: messageText,
    time: new Date().toLocaleTimeString(),
    images: []
  })
  
  userInput.value = ''
  uploadFiles.value = []
  loading.value = true
  
  await scrollToBottom()
  
  try {
    const response = await api.diagnosis.chat(sessionUuid, token, messageText, imageFiles)
    
    if (response.success && response.data) {
      const aiMessage = {
        role: 'assistant',
        content: response.data.message,
        time: new Date().toLocaleTimeString(),
        isHazard: response.data.hazardDetected,
        guideUrl: response.data.guideUrl
      }
      
      messages.value.push(aiMessage)
      session.value.roundCount = response.data.roundNumber
      
      if (response.data.needsTransfer) {
        transferred.value = true
      } else if (response.data.guideUrl) {
        showFeedback.value = true
      }
      
      await scrollToBottom()
    }
  } catch (error) {
    showToast('Failed to send message')
  } finally {
    loading.value = false
  }
}

const submitFeedback = async (thumbsUp) => {
  showFeedback.value = false
  
  try {
    await api.diagnosis.feedback(sessionUuid, token, thumbsUp)
    
    if (thumbsUp) {
      showDialog({
        title: 'Thank you!',
        message: 'We\'re glad we could help. Feel free to contact us if you need further assistance.'
      })
    } else {
      showDialog({
        title: 'We\'re sorry',
        message: 'Our team will reach out to provide additional support.'
      })
      transferred.value = true
    }
  } catch (error) {
    showToast('Failed to submit feedback')
  }
}

const openGuide = (url) => {
  window.location.href = url
}

const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

onMounted(() => {
  validateSession()
})
</script>

<style scoped>
.chat-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg-chat);
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-4);
  padding-bottom: 80px;
}

.message-item {
  display: flex;
  margin-bottom: var(--space-4);
  animation: slideIn 0.2s ease-out;
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.message-user {
  justify-content: flex-end;
}

.message-assistant {
  justify-content: flex-start;
}

.message-bubble {
  max-width: 80%;
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-lg);
  line-height: var(--line-relaxed);
  word-wrap: break-word;
  box-shadow: var(--shadow-sm);
  transition: all var(--transition-base);
}

.message-user .message-bubble {
  background: linear-gradient(135deg, var(--brand-primary) 0%, var(--brand-primary-hover) 100%);
  color: var(--text-inverse);
  border-bottom-right-radius: var(--space-2);
}

.message-assistant .message-bubble {
  background: var(--bg-elevated);
  color: var(--text-primary);
  border: 1px solid var(--neutral-200);
  border-bottom-left-radius: var(--space-2);
}

.message-hazard .message-bubble {
  background: linear-gradient(135deg, #fef2f2 0%, #fee2e2 100%);
  border: 2px solid var(--color-hazard);
  box-shadow: 0 4px 12px rgba(220, 38, 38, 0.2);
  animation: pulse 1s ease-in-out;
}

@keyframes pulse {
  0%, 100% { box-shadow: 0 4px 12px rgba(220, 38, 38, 0.2); }
  50% { box-shadow: 0 4px 16px rgba(220, 38, 38, 0.4); }
}

.message-meta {
  font-size: var(--text-xs);
  color: var(--text-tertiary);
  margin-top: var(--space-2);
  opacity: 0.7;
}

.message-user .message-meta {
  color: rgba(255, 255, 255, 0.8);
}

/* Chat Input Area with Glass Effect */
.chat-input-area {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: var(--space-4);
  display: flex;
  align-items: flex-end;
  gap: var(--space-2);
  background: var(--glass-bg);
  border-top: 1px solid var(--glass-border);
  box-shadow: 0 -2px 12px rgba(0, 0, 0, 0.08);
  padding-bottom: calc(var(--space-4) + env(safe-area-inset-bottom));
}

@supports (backdrop-filter: blur(var(--blur-md))) {
  .chat-input-area {
    backdrop-filter: blur(var(--blur-md)) saturate(180%);
  }
}

/* Loading Animation */
.loading-dots::after {
  content: '...';
  animation: dots 1.5s steps(3, end) infinite;
}

@keyframes dots {
  0%, 20% { content: '.'; }
  40% { content: '..'; }
  60%, 100% { content: '...'; }
}
</style>
