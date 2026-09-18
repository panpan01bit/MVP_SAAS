<template>
  <div class="login-page">
    <div class="login-card glass">
      <div class="login-brand">
        <div class="brand-icon">
          <el-icon size="36"><Tools /></el-icon>
        </div>
        <h1>ToolFix</h1>
        <p>AI 售后诊断系统 · 演示后台</p>
      </div>

      <el-form @submit.prevent="handleLogin" class="login-form">
        <el-form-item>
          <el-input v-model="email" placeholder="邮箱" size="large" :prefix-icon="Message">
            <template #prefix><el-icon><Message /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item>
          <el-input v-model="password" type="password" placeholder="密码" size="large" show-password
                    @keyup.enter="handleLogin">
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="handleLogin">
          登 录
        </el-button>
        <div class="demo-hint">
          演示账号：<code>demo@toolfix.com</code> / <code>demo123</code>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Tools, Message, Lock } from '@element-plus/icons-vue'
import api from '../api'

const router = useRouter()
const email = ref('demo@toolfix.com')
const password = ref('demo123')
const loading = ref(false)

const handleLogin = async () => {
  if (!email.value || !password.value) {
    ElMessage.warning('请输入邮箱和密码')
    return
  }
  loading.value = true
  try {
    const res = await api.auth.login(email.value, password.value)
    if (res.success) {
      localStorage.setItem('toolfix_admin', res.data.email)
      ElMessage.success('登录成功')
      router.push('/')
    } else {
      ElMessage.error(res.message || '登录失败')
    }
  } catch (e) {
    ElMessage.error('登录失败，请检查后端服务')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 50%, #0c4a6e 100%);
}
.login-card {
  width: 400px;
  padding: 48px 40px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 25px 60px rgba(0, 0, 0, 0.35);
}
.login-brand { text-align: center; margin-bottom: 32px; }
.brand-icon {
  width: 72px; height: 72px;
  margin: 0 auto 16px;
  border-radius: 20px;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #2563eb, #0891b2);
  color: #fff;
}
.login-brand h1 { font-size: 28px; margin: 0 0 4px; color: #0f172a; }
.login-brand p { margin: 0; color: #64748b; font-size: 14px; }
.login-btn { width: 100%; margin-top: 8px; }
.demo-hint {
  margin-top: 20px;
  text-align: center;
  font-size: 13px;
  color: #94a3b8;
}
.demo-hint code {
  background: #f1f5f9;
  padding: 2px 6px;
  border-radius: 4px;
  color: #475569;
}
</style>
