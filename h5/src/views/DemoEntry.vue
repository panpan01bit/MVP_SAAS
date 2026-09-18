<template>
  <div class="demo-entry">
    <div class="hero">
      <div class="logo">🔧</div>
      <h1>ToolFix AI Support</h1>
      <p class="tagline">Smart after-sales diagnosis for your power tools</p>
    </div>

    <div class="card">
      <div class="features">
        <div class="feature">
          <span class="icon">📖</span>
          <div>
            <b>Manual-trained AI</b>
            <p>Answers based on your product's real manual</p>
          </div>
        </div>
        <div class="feature">
          <span class="icon">⚡</span>
          <div>
            <b>Fix it in minutes</b>
            <p>Step-by-step guides for common false faults</p>
          </div>
        </div>
        <div class="feature">
          <span class="icon">🛡️</span>
          <div>
            <b>Safety first</b>
            <p>Dangerous issues go straight to human agents</p>
          </div>
        </div>
      </div>

      <button class="start-btn" :disabled="loading" @click="startDemo">
        <span v-if="!loading">▶ &nbsp;Start Free Demo</span>
        <span v-else>Creating demo session...</span>
      </button>
      <p class="hint">No sign-up needed · Instant AI diagnosis experience</p>
      <p v-if="error" class="error">{{ error }}</p>
    </div>

    <p class="footer">Powered by ToolFix AI · Demo Mode</p>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api'

const router = useRouter()
const loading = ref(false)
const error = ref('')

const startDemo = async () => {
  loading.value = true
  error.value = ''
  try {
    const res = await api.diagnosis.createDemoSession()
    if (res.success && res.data?.sessionUuid) {
      router.push({
        path: `/diagnosis/${res.data.sessionUuid}`,
        query: { token: res.data.token }
      })
    } else {
      error.value = res.message || 'Demo is not available right now.'
    }
  } catch (e) {
    error.value = 'Cannot reach the server. Please try again later.'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.demo-entry {
  min-height: 100vh;
  background: linear-gradient(160deg, #0f172a 0%, #1e3a5f 55%, #0c4a6e 100%);
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px 24px 32px;
  color: #fff;
}
.hero { text-align: center; margin-bottom: 32px; }
.logo { font-size: 64px; }
.hero h1 { font-size: 32px; margin: 12px 0 6px; letter-spacing: 0.5px; }
.tagline { color: #94a3b8; margin: 0; font-size: 15px; }

.card {
  width: 100%;
  max-width: 420px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 20px;
  padding: 28px 24px;
  backdrop-filter: blur(10px);
}
.features { display: flex; flex-direction: column; gap: 18px; margin-bottom: 28px; }
.feature { display: flex; gap: 14px; align-items: flex-start; }
.feature .icon { font-size: 26px; }
.feature b { display: block; font-size: 15px; margin-bottom: 2px; }
.feature p { margin: 0; font-size: 13px; color: #a8b6c8; }

.start-btn {
  width: 100%;
  padding: 16px;
  font-size: 17px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, #2563eb, #0891b2);
  border: none;
  border-radius: 14px;
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
}
.start-btn:active { transform: scale(0.98); }
.start-btn:disabled { opacity: 0.6; cursor: wait; }
.hint { text-align: center; color: #7d8ea3; font-size: 12px; margin: 14px 0 0; }
.error { text-align: center; color: #f87171; font-size: 13px; }
.footer { margin-top: auto; padding-top: 32px; color: #5b6b7f; font-size: 12px; }
</style>
