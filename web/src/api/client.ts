import type { ApiErrorBody, TokenResponse } from './types'

const REFRESH_KEY = 'avia.refreshToken'

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: Record<string, string>

  constructor(body: ApiErrorBody) {
    super(body.message)
    this.status = body.status
    this.code = body.code
    this.fieldErrors = Object.fromEntries((body.fieldErrors ?? []).map((e) => [e.field, e.message]))
  }
}

/** Access-токен живёт только в памяти, refresh-токен — в localStorage, чтобы пережить перезагрузку. */
let accessToken: string | null = null
let onSessionLost: () => void = () => {}

export function setSessionLostHandler(handler: () => void) {
  onSessionLost = handler
}

export function getRefreshToken(): string | null {
  try {
    return localStorage.getItem(REFRESH_KEY)
  } catch {
    return null
  }
}

export function storeTokens(tokens: Pick<TokenResponse, 'accessToken' | 'refreshToken'>) {
  accessToken = tokens.accessToken
  try {
    localStorage.setItem(REFRESH_KEY, tokens.refreshToken)
  } catch {
    /* хранилище недоступно — сессия живёт до перезагрузки */
  }
}

export function clearTokens() {
  accessToken = null
  try {
    localStorage.removeItem(REFRESH_KEY)
  } catch {
    /* ignore */
  }
}

async function parseError(res: Response): Promise<ApiError> {
  try {
    return new ApiError((await res.json()) as ApiErrorBody)
  } catch {
    return new ApiError({ status: res.status, code: 'UNKNOWN', message: 'Сервер недоступен или вернул неожиданный ответ' })
  }
}

let refreshing: Promise<boolean> | null = null

/** Одновременные 401 делят один запрос refresh: токен ротируется, второй запрос с ним был бы отклонён. */
export function refreshSession(): Promise<boolean> {
  if (!refreshing) {
    refreshing = (async () => {
      const refreshToken = getRefreshToken()
      if (!refreshToken) return false
      try {
        const res = await fetch('/api/auth/refresh', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken }),
        })
        if (!res.ok) {
          clearTokens()
          return false
        }
        storeTokens((await res.json()) as TokenResponse)
        return true
      } catch {
        return false
      }
    })().finally(() => {
      refreshing = null
    })
  }
  return refreshing
}

interface RequestOptions {
  method?: string
  body?: unknown
  /** false — запрос без токена (логин, refresh). */
  auth?: boolean
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, auth = true } = options

  const send = () =>
    fetch(path, {
      method,
      headers: {
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...(auth && accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    })

  let res = await send()
  if (res.status === 401 && auth && (await refreshSession())) {
    res = await send()
  }
  if (res.status === 401 && auth) {
    clearTokens()
    onSessionLost()
  }
  if (!res.ok) throw await parseError(res)
  return res.status === 204 ? (undefined as T) : ((await res.json()) as T)
}
