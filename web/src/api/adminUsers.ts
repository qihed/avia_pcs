import { api } from './client'
import type { Role } from './types'

export interface AdminUser {
  id: string
  login: string
  displayName: string
  roles: Role[]
  enabled: boolean
  mustChangePassword: boolean
  locked: boolean
  lockedUntil?: string
  failedAttempts: number
  lastLoginAt?: string
  createdAt: string
  updatedAt: string
  version: number
}

export interface UserWithPassword {
  user: AdminUser
  temporaryPassword: string
}

export interface PageResponse<T> {
  content: T[]
  number: number
  size: number
  totalElements: number
  totalPages: number
}

export const ROLE_TITLES: Record<Role, string> = {
  MODERATOR: 'Модератор',
  ADMIN: 'Администратор',
}

/** Роли вложены, поэтому в интерфейсе показываем и выбираем одну — старшую. */
export function topRole(roles: Role[]): Role {
  if (roles.includes('ADMIN')) return 'ADMIN'
  return 'MODERATOR'
}

export const adminUsersApi = {
  get: (id: string) => api<AdminUser>('/api/admin/users/' + id),
  list: (params: URLSearchParams) => api<PageResponse<AdminUser>>('/api/admin/users?' + params.toString()),

  create: (login: string, displayName: string, role: Role) =>
    api<UserWithPassword>('/api/admin/users', { method: 'POST', body: { login, displayName, roles: [role] } }),

  update: (user: AdminUser, displayName: string, role: Role, enabled: boolean) =>
    api<AdminUser>('/api/admin/users/' + user.id, {
      method: 'PUT',
      body: { displayName, roles: [role], enabled, version: user.version },
    }),

  resetPassword: (id: string) => api<UserWithPassword>(`/api/admin/users/${id}/reset-password`, { method: 'POST' }),

  unlock: (id: string) => api<AdminUser>(`/api/admin/users/${id}/unlock`, { method: 'POST' }),
}
