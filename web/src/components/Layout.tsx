import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { hasRole } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { appRoutes } from '../routes'

export function Layout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  async function handleLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="app">
      <header className="topbar">
        <span className="brand">✈ Авиабронирование</span>
        {user && !user.mustChangePassword && (
          <nav className="nav">
            {appRoutes
              .filter((r) => hasRole(user, r.role))
              .map((r) => (
                <NavLink key={r.path} to={r.path} end={r.path === '/'}>
                  {r.label}
                </NavLink>
              ))}
          </nav>
        )}
        <span className="spacer" />
        {user && (
          <div className="user">
            <span>{user.displayName}</span>
            <span className="badge">{user.roles.at(-1)}</span>
            <button className="btn ghost" onClick={handleLogout}>
              Выйти
            </button>
          </div>
        )}
      </header>
      <main className="content">
        <Outlet />
      </main>
    </div>
  )
}
