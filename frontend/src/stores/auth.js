import { defineStore } from 'pinia'
import { getMe, logout as apiLogout } from '../api'

export const useAuthStore = defineStore('auth', {
  state: () => ({ user: null }),
  getters: {
    nickname: (s) => s.user?.nickname || '未登录',
    isAdmin: (s) => s.user?.role === 'ADMIN',
  },
  actions: {
    async fetchMe() {
      const resp = await getMe()
      this.user = resp.data
    },
    async logout() {
      try {
        await apiLogout()
      } finally {
        localStorage.removeItem('df_token')
        this.user = null
        location.href = '/login'
      }
    },
  },
})
