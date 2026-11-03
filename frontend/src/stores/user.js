import { defineStore } from 'pinia'

const TOKEN_KEY = 'tp_token'
const USER_KEY = 'tp_user'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    userInfo: JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  }),
  getters: {
    isLogin: (state) => !!state.token,
    nickname: (state) => (state.userInfo && state.userInfo.nickname) || '旅行者',
    /** 是否管理员：角色由后端下发，前端只用于显示入口，真正的鉴权在 /api/admin/** 拦截器里 */
    isAdmin: (state) => (state.userInfo && state.userInfo.role) === 'ADMIN'
  },
  actions: {
    setLogin(token, user) {
      this.token = token
      this.userInfo = user
      localStorage.setItem(TOKEN_KEY, token)
      localStorage.setItem(USER_KEY, JSON.stringify(user || {}))
    },
    /** 局部更新用户信息（如修改头像、昵称后同步顶栏） */
    setUser(user) {
      this.userInfo = { ...(this.userInfo || {}), ...(user || {}) }
      localStorage.setItem(USER_KEY, JSON.stringify(this.userInfo))
    },
    logout() {
      this.token = ''
      this.userInfo = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    }
  }
})
