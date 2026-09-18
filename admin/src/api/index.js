import axios from 'axios'

// Use VITE_API_BASE_URL env var, fallback to /api for local dev
const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'

const api = axios.create({
  baseURL,
  timeout: 30000
})

api.interceptors.response.use(
  response => response.data,
  error => {
    console.error('API Error:', error)
    // Graceful handling for GitHub Pages demo (no backend)
    if (error.code === 'ERR_NETWORK' || error.message.includes('Network Error')) {
      console.warn('Backend not available (expected on GitHub Pages demo)')
      return Promise.resolve({
        success: false,
        backendDown: true,
        message: 'Backend API not hosted on GitHub Pages',
        data: null
      })
    }
    // 401: 回登录页（登录接口自身除外）
    if (error.response?.status === 401 && !error.config?.url?.includes('/auth/login')) {
      localStorage.removeItem('toolfix_admin')
      if (!window.location.pathname.endsWith('/login')) {
        window.location.href = (import.meta.env.BASE_URL || '/') + 'login'
      }
    }
    return Promise.reject(error)
  }
)

export default {
  auth: {
    login: (email, password) => api.post('/auth/login', { email, password }),
    logout: () => api.post('/auth/logout'),
    me: () => api.get('/auth/me')
  },
  
  shops: {
    getAll: () => api.get('/shops'),
    connect: (shopDomain) => api.post('/shops/connect/init', { shopDomain }),
    callback: (data) => api.post('/shops/connect/callback', data),
    syncOrders: (id) => api.post(`/shops/${id}/sync-orders`)
  },
  
  products: {
    getAll: (shopId) => api.get('/products', { params: { shopId } }),
    get: (id) => api.get(`/products/${id}`),
    create: (data) => api.post('/products', data),
    update: (id, data) => api.put(`/products/${id}`, data)
  },
  
  manuals: {
    getAll: () => api.get('/manuals'),
    get: (id) => api.get(`/manuals/${id}`),
    getByProduct: (productId) => api.get(`/manuals/product/${productId}`),
    upload: (formData) => api.post('/manuals/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    }),
    confirm: (id, data) => api.post(`/manuals/${id}/confirm`, data),
    delete: (id) => api.delete(`/manuals/${id}`)
  },
  
  sessions: {
    getAll: (params) => api.get('/sessions', { params }),
    get: (id) => api.get(`/sessions/${id}`),
    getByUuid: (uuid) => api.get(`/sessions/uuid/${uuid}`),
    getTransferred: (shopId) => api.get('/sessions/transferred', { params: { shopId } }),
    getStats: (shopId) => api.get('/sessions/stats', { params: { shopId } }),
    sendHumanReply: (id, message) => api.post(`/sessions/${id}/human-reply`, { message }),
    createSession: (data) => api.post('/diagnosis/create-session', data)
  },
  
  knowledge: {
    getAll: () => api.get('/admin/knowledge-base'),
    get: (id) => api.get(`/admin/knowledge-base/${id}`)
  },
  
  aiUsage: {
    getAll: (page, size) => api.get('/admin/ai-usage', { params: { page, size } }),
    getStats: (days) => api.get('/admin/ai-usage/stats', { params: { days } })
  }
}
