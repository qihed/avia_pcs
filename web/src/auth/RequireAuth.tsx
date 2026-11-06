import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { hasRole, type Role } from '../api/types'
import { useAuth } from './AuthContext'

interface Props {
  /** Минимальная роль для страницы; без неё достаточно войти */
  role?: Role
  children: ReactNode
}

/** Защита маршрута: гость -> /login, временный пароль -> /change-password, нехватка роли -> 403. */
export function RequireAuth({ role, children }: Props) {
  const { user, loading } = useAuth()
  const location = useLocation()

  if (loading) return <div className="center-screen muted">Загрузка…</div>
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  if (user.mustChangePassword) return <Navigate to="/change-password" replace />
  if (role && !hasRole(user, role)) {
    return (
      <div className="card stack">
        <h2>Недостаточно прав</h2>
        <p className="muted">Для этой страницы нужна роль {role}.</p>
      </div>
    )
  }
  return <>{children}</>
}
