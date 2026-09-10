import axios, { AxiosError } from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'

export interface ApiResult<T = any> {
  code: number
  message: string
  data: T
}

const client = axios.create({
  baseURL: '/api',
  timeout: 30000
})

client.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

let logoutTipShown = false

client.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResult
    // 文件流等非标准结构直接放行
    if (body === null || typeof body !== 'object' || body.code === undefined) {
      return response
    }
    if (body.code === 0) {
      return response
    }
    // 401：登录过期，统一跳转
    if (body.code === 40100) {
      const auth = useAuthStore()
      auth.logout()
      if (!logoutTipShown) {
        logoutTipShown = true
        ElMessage.error('登录已过期，请重新登录')
        setTimeout(() => (logoutTipShown = false), 2000)
      }
      router.replace('/login')
      return Promise.reject(new ApiBusinessError(body.code, body.message))
    }
    // 其余业务错误：提示后抛出，由页面决定是否继续处理错误体
    ElMessage.error(body.message || '请求失败')
    return Promise.reject(new ApiBusinessError(body.code, body.message, body.data))
  },
  (error: AxiosError) => {
    if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请稍后重试')
    } else if (!error.response) {
      ElMessage.error('网络异常，无法连接服务器')
    } else {
      ElMessage.error(`服务异常（HTTP ${error.response.status}）`)
    }
    return Promise.reject(error)
  }
)

export class ApiBusinessError extends Error {
  code: number
  data?: any
  constructor(code: number, message: string, data?: any) {
    super(message)
    this.code = code
    this.data = data
  }
}

export async function get<T = any>(url: string, params?: Record<string, any>): Promise<T> {
  const resp = await client.get(url, { params })
  return (resp.data as ApiResult<T>).data
}

export async function post<T = any>(url: string, data?: any): Promise<T> {
  const resp = await client.post(url, data)
  return (resp.data as ApiResult<T>).data
}

export default client
