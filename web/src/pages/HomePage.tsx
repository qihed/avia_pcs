import { Link } from 'react-router-dom'
import { hasRole } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { appRoutes } from '../routes'

export function HomePage() {
  const { user } = useAuth()
  const links = appRoutes.filter((r) => r.path !== '/' && hasRole(user, r.role))
  return (
    <div className="stack">
      <h1>Здравствуйте, {user?.displayName}</h1>
      <p className="muted">Выберите раздел.</p>
      <div className="grid">
        {links.map((r) => (
          <Link key={r.path} to={r.path} className="card tile">
            {r.label}
          </Link>
        ))}
      </div>
    </div>
  )
}
