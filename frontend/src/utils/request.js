import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 180000
})

request.interceptors.request.use((config) => {
  const store = useUserStore()
  if (store.token) {
    config.headers.Authorization = 'Bearer ' + store.token
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const data = response.data
    if (data && data.code === 200) {
      return data
    }
    ElMessage.error((data && data.message) || '请求失败')
    return Promise.reject(new Error((data && data.message) || '请求失败'))
  },
  (error) => {
    const status = error.response && error.response.status
    if (status === 401) {
      const store = useUserStore()
      store.logout()
      router.push('/login')
      ElMessage.error('登录已过期，请重新登录')
    } else {
      // 注意：后端的业务错误都会带 JSON 的 message 字段。
      // 一旦拿不到 message，多半不是后端在报错，而是请求根本没到后端：
      //   - vite 代理连不上上游（后端未启动 / 正在重启 / 被关掉）会直接返回 500，
      //     body 不是 JSON，于是只剩 axios 的英文 "Request failed with status code 500"。
      // 这里统一翻译成可操作的中文，避免用户误以为系统坏了。
      let msg = error.response?.data?.message
      if (!msg) {
        if (error.code === 'ECONNABORTED') {
          msg = '请求超时：AI 生成耗时较长，可稍后到「我的方案」查看结果'
        } else if (!status) {
          msg = '无法连接后端服务，请确认已运行 start.cmd'
        } else if (status >= 500) {
          msg = `后端服务暂时不可用（HTTP ${status}）：可能正在启动或已退出，请等几秒重试；仍失败请运行 start.cmd，或查看 logs\\backend.log`
        } else {
          msg = `请求失败（HTTP ${status}）`
        }
      }
      ElMessage.error(msg)
    }
    return Promise.reject(error)
  }
)

export default request
