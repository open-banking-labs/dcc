import axios, { AxiosError, type AxiosRequestConfig } from 'axios'

import type { ApiError } from '@/types/common'

import { getAccessToken, notifyUnauthorized } from './authToken'

const baseURL = import.meta.env.VITE_API_BASE ?? '/api'

/** 归一化后的请求错误：UI 只需读 status / code / message。 */
export class ApiClientError extends Error {
  readonly status: number
  readonly code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.name = 'ApiClientError'
    this.status = status
    this.code = code
  }
}

export const apiClient = axios.create({
  baseURL,
  headers: { 'Content-Type': 'application/json' },
})

apiClient.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiError>) => {
    const status = error.response?.status ?? 0
    const body = error.response?.data
    if (status === 401) {
      notifyUnauthorized()
    }
    const code = body?.error || (status ? `http_${status}` : 'network_error')
    const message = body?.message || error.message || '请求失败'
    return Promise.reject(new ApiClientError(status, code, message))
  },
)

/** 去掉 undefined / null / 空串的查询参数（后端 @RequestParam 可选）。 */
export function cleanParams<T extends object>(params: T): Partial<T> {
  return Object.fromEntries(
    Object.entries(params).filter(
      ([, value]) => value !== undefined && value !== null && value !== '',
    ),
  ) as Partial<T>
}

export async function httpGet<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const { data } = await apiClient.get<T>(url, config)
  return data
}

export async function httpPost<T>(
  url: string,
  body?: unknown,
  config?: AxiosRequestConfig,
): Promise<T> {
  const { data } = await apiClient.post<T>(url, body, config)
  return data
}

export async function httpPut<T>(
  url: string,
  body?: unknown,
  config?: AxiosRequestConfig,
): Promise<T> {
  const { data } = await apiClient.put<T>(url, body, config)
  return data
}

export async function httpDelete(url: string, config?: AxiosRequestConfig): Promise<void> {
  await apiClient.delete(url, config)
}
