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
        message: 'Backend API not hosted on GitHub Pages',
        data: null
      })
    }
    return Promise.reject(error)
  }
)

export default {
  diagnosis: {
    validate: (sessionUuid, token) => api.get(`/diagnosis/${sessionUuid}/validate`, { params: { token } }),
    chat: (sessionUuid, token, message, images) => {
      const formData = new FormData()
      formData.append('token', token)
      formData.append('message', message)
      if (images && images.length > 0) {
        images.forEach(image => formData.append('images', image))
      }
      return api.post(`/diagnosis/${sessionUuid}/chat`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      })
    },
    feedback: (sessionUuid, token, thumbsUp) => api.post(`/diagnosis/${sessionUuid}/feedback`, { thumbsUp }, { params: { token } }),
    resolve: (sessionUuid, token, resolved) => api.post(`/diagnosis/${sessionUuid}/resolve`, { resolved }, { params: { token } }),
    createDemoSession: () => api.post('/diagnosis/demo-session'),
    getSession: (sessionUuid) => api.get(`/sessions/uuid/${sessionUuid}`)
  }
}
