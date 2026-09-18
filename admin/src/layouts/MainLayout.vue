<template>
  <el-container class="layout-container">
    <el-aside width="240px" class="sidebar glass">
      <div class="sidebar-header">
        <div class="brand-icon">
          <el-icon size="28"><Tools /></el-icon>
        </div>
        <h2 class="brand-title">ToolFix</h2>
        <p class="brand-subtitle">AI售后诊断系统</p>
      </div>
      
      <el-menu
        :default-active="activeMenu"
        router
        class="sidebar-menu"
      >
        <el-menu-item index="/">
          <el-icon><DataAnalysis /></el-icon>
          <span>仪表盘</span>
        </el-menu-item>
        <el-menu-item index="/shops">
          <el-icon><Shop /></el-icon>
          <span>店铺管理</span>
        </el-menu-item>
        <el-menu-item index="/products">
          <el-icon><Box /></el-icon>
          <span>产品管理</span>
        </el-menu-item>
        <el-menu-item index="/manuals">
          <el-icon><Document /></el-icon>
          <span>说明书管理</span>
        </el-menu-item>
        <el-menu-item index="/sessions">
          <el-icon><ChatDotRound /></el-icon>
          <span>诊断会话</span>
        </el-menu-item>
        <el-menu-item index="/knowledge">
          <el-icon><Reading /></el-icon>
          <span>知识库</span>
        </el-menu-item>
        <el-menu-item index="/ai-usage">
          <el-icon><Coin /></el-icon>
          <span>AI用量统计</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    
    <el-container>
      <el-header class="top-header glass">
        <div class="header-title">
          {{ pageTitle }}
        </div>
        <div style="display:flex; align-items:center; gap:16px; margin-left:auto;">
          <span class="admin-user">{{ adminEmail }}</span>
          <el-button text type="info" @click="handleLogout">退出登录</el-button>
          <el-badge :value="transferredCount" :hidden="transferredCount === 0" type="danger">
            <el-button 
              type="danger" 
              :icon="Bell" 
              @click="showTransferred" 
              circle 
              class="notification-btn"
            />
          </el-badge>
        </div>
      </el-header>
      
      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Bell, Tools } from '@element-plus/icons-vue'
import api from '../api'

const route = useRoute()
const router = useRouter()

const transferredCount = ref(0)
const adminEmail = ref(localStorage.getItem('toolfix_admin') || 'admin')

const handleLogout = async () => {
  try { await api.auth.logout() } catch (e) { /* 忽略 */ }
  localStorage.removeItem('toolfix_admin')
  router.push('/login')
}

const activeMenu = computed(() => route.path)

const pageTitle = computed(() => {
  const titleMap = {
    '/': '仪表盘',
    '/shops': '店铺管理',
    '/products': '产品管理',
    '/manuals': '说明书管理',
    '/sessions': '诊断会话',
    '/knowledge': '知识库',
    '/ai-usage': 'AI用量统计'
  }
  return titleMap[route.path] || '详情'
})

const loadTransferredCount = async () => {
  try {
    const response = await api.sessions.getStats(1)
    if (response.success) {
      transferredCount.value = response.data.transferredCount || 0
    }
  } catch (error) {
    console.error('Failed to load transferred count:', error)
  }
}

const showTransferred = () => {
  router.push('/sessions?status=TRANSFERRED')
}

onMounted(() => {
  loadTransferredCount()
  setInterval(loadTransferredCount, 30000)
})
</script>

<style scoped>
.layout-container {
  height: 100vh;
  background: var(--bg-base);
}

/* Sidebar with glass effect */
.sidebar {
  border-right: 1px solid var(--glass-border);
  position: relative;
}

.sidebar-header {
  padding: var(--space-8) var(--space-6);
  text-align: center;
  background: linear-gradient(135deg, var(--brand-primary) 0%, var(--brand-primary-hover) 100%);
  position: relative;
  overflow: hidden;
}

.sidebar-header::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -50%;
  width: 200%;
  height: 200%;
  background: radial-gradient(circle, rgba(255,255,255,0.1) 0%, transparent 70%);
  animation: shimmer 3s ease-in-out infinite;
}

@keyframes shimmer {
  0%, 100% { transform: translate(0, 0); }
  50% { transform: translate(-10%, -10%); }
}

.brand-icon {
  width: 56px;
  height: 56px;
  margin: 0 auto var(--space-3);
  background: rgba(255, 255, 255, 0.2);
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-inverse);
  backdrop-filter: blur(var(--blur-sm));
  border: 1px solid rgba(255, 255, 255, 0.3);
  box-shadow: var(--shadow-md);
}

.brand-title {
  color: var(--text-inverse);
  margin: 0;
  font-size: var(--text-2xl);
  font-weight: var(--font-bold);
  letter-spacing: -0.02em;
}

.brand-subtitle {
  color: rgba(255, 255, 255, 0.9);
  margin: var(--space-1) 0 0;
  font-size: var(--text-sm);
  font-weight: var(--font-medium);
}

.sidebar-menu {
  background: transparent;
  border-right: none;
  padding: var(--space-4) 0;
}

/* Top header with glass effect */
.top-header {
  border-bottom: 1px solid var(--glass-border);
  padding: 0 var(--space-6);
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
}

.header-title {
  font-size: var(--text-xl);
  font-weight: var(--font-semibold);
  color: var(--text-primary);
  letter-spacing: -0.01em;
}

.notification-btn {
  transition: all var(--transition-base);
}

.notification-btn:hover {
  transform: translateY(-2px) scale(1.05);
  box-shadow: var(--shadow-lg);
}

/* Main content */
.main-content {
  padding: var(--space-6);
  overflow-y: auto;
  background: var(--bg-base);
}

/* Smooth scrolling */
.main-content::-webkit-scrollbar {
  width: 8px;
}

.main-content::-webkit-scrollbar-track {
  background: transparent;
}

.main-content::-webkit-scrollbar-thumb {
  background: var(--neutral-300);
  border-radius: var(--radius-full);
}

.main-content::-webkit-scrollbar-thumb:hover {
  background: var(--neutral-400);
}
</style>
