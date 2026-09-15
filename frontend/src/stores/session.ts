import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api, type ApiResponse } from '../api'

export interface CurrentUser {
  userId: number
  username: string
  displayName: string
  permissions: string[]
  allProjects: boolean
  projectIds: number[]
}

export const useSessionStore = defineStore('session', () => {
  const user = ref<CurrentUser>()

  async function load() {
    if (!localStorage.getItem('iot-ops-token')) return
    user.value = (await api.get<ApiResponse<CurrentUser>>('/auth/me')).data.data
  }

  function can(permission: string) {
    return user.value?.permissions.includes(permission) ?? false
  }

  function clear() {
    user.value = undefined
    localStorage.removeItem('iot-ops-token')
  }

  return { user, load, can, clear }
})
