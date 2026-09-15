import axios from 'axios'

export interface ApiResponse<T> {
  success: boolean
  data: T
  code?: string
  message?: string
}

export const api = axios.create({ baseURL: '/api', timeout: 15_000 })

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('iot-ops-token')
  if (token) config.headers['X-Token'] = token
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('iot-ops-token')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  },
)

