import { api, getRefreshToken } from './client'
import type { TokenResponse, User } from './types'

export const authApi = {
  login: (login: string, password: string) =>
    api<TokenResponse>('/api/auth/login', { method: 'POST', body: { login, password }, auth: false }),

  me: () => api<User>('/api/auth/me'),

  changePassword: (currentPassword: string, newPassword: string) =>
    api<TokenResponse>('/api/auth/change-password', { method: 'POST', body: { currentPassword, newPassword } }),

  logout: async () => {
    const refreshToken = getRefreshToken()
    if (refreshToken) {
      await api<void>('/api/auth/logout', { method: 'POST', body: { refreshToken }, auth: false })
    }
  },
}
