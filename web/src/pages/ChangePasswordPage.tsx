import { useState, type FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { authApi } from '../api/auth'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'

/** Те же правила, что у backend (PasswordPolicy): подсказки в реальном времени, окончательная проверка на сервере. */
function rules(password: string, login: string) {
  const localPart = login.includes('@') ? login.slice(0, login.indexOf('@')) : login
  return [
    { text: 'не короче 10 символов', ok: password.length >= 10 },
    { text: 'заглавная и строчная буквы', ok: /\p{Lu}/u.test(password) && /\p{Ll}/u.test(password) },
    { text: 'цифра', ok: /\d/.test(password) },
    { text: 'специальный символ', ok: /[^\p{L}\d]/u.test(password.replace(/\s/g, '')) },
    { text: 'без пробелов', ok: password.length > 0 && !/\s/.test(password) },
    {
      text: 'не содержит логин',
      ok: password.length > 0 && !(localPart.length >= 3 && password.toLowerCase().includes(localPart.toLowerCase())),
    },
  ]
}

export function ChangePasswordPage() {
  const { user, loading, applyTokens, logout } = useAuth()
  const navigate = useNavigate()

  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [repeat, setRepeat] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)

  if (loading) return <div className="center-screen muted">Загрузка…</div>
  if (!user) return <Navigate to="/login" replace />

  const checks = rules(next, user.login)
  const mismatch = repeat.length > 0 && next !== repeat
  const canSubmit = current.length > 0 && checks.every((c) => c.ok) && next === repeat && !submitting

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setFieldErrors({})
    setSubmitting(true)
    try {
      applyTokens(await authApi.changePassword(current, next))
      navigate('/', { replace: true })
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

  async function handleLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="center-screen">
      <form className="card auth-card stack" onSubmit={handleSubmit} noValidate>
        <div className="auth-head">
          <span className="logo">🔒</span>
          <h1>Смена пароля</h1>
          {user.mustChangePassword ? (
            <p className="muted">Вам выдан временный пароль. Задайте свой, чтобы продолжить работу.</p>
          ) : (
            <p className="muted">Все остальные сессии будут завершены.</p>
          )}
        </div>

        <label className="field">
          <span>Текущий пароль</span>
          <input type="password" value={current} onChange={(e) => setCurrent(e.target.value)} autoComplete="current-password" autoFocus />
          {fieldErrors.currentPassword && <small className="error">{fieldErrors.currentPassword}</small>}
        </label>

        <label className="field">
          <span>Новый пароль</span>
          <input type="password" value={next} onChange={(e) => setNext(e.target.value)} autoComplete="new-password" />
          {fieldErrors.newPassword && <small className="error">{fieldErrors.newPassword}</small>}
        </label>

        <ul className="rules">
          {checks.map((c) => (
            <li key={c.text} className={c.ok ? 'ok' : ''}>
              {c.ok ? '✓' : '○'} {c.text}
            </li>
          ))}
        </ul>

        <label className="field">
          <span>Повторите новый пароль</span>
          <input type="password" value={repeat} onChange={(e) => setRepeat(e.target.value)} autoComplete="new-password" aria-invalid={mismatch} />
          {mismatch && <small className="error">Пароли не совпадают</small>}
        </label>

        {error && (
          <div className="alert" role="alert">
            {error}
          </div>
        )}

        <button className="btn primary" type="submit" disabled={!canSubmit}>
          {submitting ? 'Сохраняем…' : 'Сменить пароль'}
        </button>
        <button className="btn ghost" type="button" onClick={handleLogout}>
          Выйти
        </button>
      </form>
    </div>
  )
}
