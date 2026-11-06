export type Role = 'VIEWER' | 'EDITOR' | 'ADMIN'

/** Порядок важен: более высокая роль включает низшие. */
export const ROLE_ORDER: Role[] = ['VIEWER', 'EDITOR', 'ADMIN']

export interface User {
  id: string
  login: string
  displayName: string
  roles: Role[]
  enabled: boolean
  mustChangePassword: boolean
  createdAt: string
  lastLoginAt?: string
}

export interface TokenResponse {
  tokenType: string
  accessToken: string
  expiresInSeconds: number
  refreshToken: string
  mustChangePassword: boolean
  user: User
}

export interface FieldViolation {
  field: string
  message: string
}

/** Единый формат ошибки backend. */
export interface ApiErrorBody {
  status: number
  code: string
  message: string
  path?: string
  correlationId?: string
  fieldErrors?: FieldViolation[]
}

export function hasRole(user: User | null, min: Role): boolean {
  if (!user) return false
  const need = ROLE_ORDER.indexOf(min)
  return user.roles.some((r) => ROLE_ORDER.indexOf(r) >= need)
}
