import { useState, type FormEvent } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'

export function LoginPage() {
  const { user, loading, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from ?? '/'

  const [loginName, setLoginName] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)

  if (loading) return <div className="center-screen muted">Загрузка…</div>
  if (user) return <Navigate to={user.mustChangePassword ? '/change-password' : from} replace />

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setFieldErrors({})
    setSubmitting(true)
    try {
      const signedIn = await login(loginName, password)
      navigate(signedIn.mustChangePassword ? '/change-password' : from, { replace: true })
    } catch (err) {
      if (err instanceof ApiError) {
        setFieldErrors(err.fieldErrors)
        setError(Object.keys(err.fieldErrors).length > 0 ? null : err.message)
      } else {
        setError('Не удалось связаться с сервером')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="center-screen">
      <form className="card auth-card stack" onSubmit={handleSubmit} noValidate>
        <div className="auth-head">
          <span className="logo">✈</span>
          <h1>Вход</h1>
          <p className="muted">Информационная система авиабронирования</p>
        </div>

        <label className="field">
          <span>Логин</span>
          <input
            value={loginName}
            onChange={(e) => setLoginName(e.target.value)}
            autoComplete="username"
            autoFocus
            aria-invalid={!!fieldErrors.login}
          />
          {fieldErrors.login && <small className="error">{fieldErrors.login}</small>}
        </label>

        <label className="field">
          <span>Пароль</span>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            aria-invalid={!!fieldErrors.password}
          />
          {fieldErrors.password && <small className="error">{fieldErrors.password}</small>}
        </label>

        {error && (
          <div className="alert" role="alert">
            {error}
          </div>
        )}

        <button className="btn primary" type="submit" disabled={submitting}>
          {submitting ? 'Входим…' : 'Войти'}
        </button>
      </form>
    </div>
  )
}
