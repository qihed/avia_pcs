import { useEffect, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { adminUsersApi, ROLE_TITLES, topRole, type AdminUser, type PageResponse, type UserWithPassword } from '../api/adminUsers'
import { ApiError } from '../api/client'
import { ROLE_ORDER, type Role } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import {
  formatInstant,
  formatSort,
  intParam,
  PAGE_SIZES,
  Pager,
  parseSort,
  SortHeader,
  useUrlTextFields,
  type SortState,
} from '../components/ListControls'
import { OperationError } from '../components/OperationError'

const DEFAULT_SORT: SortState = { field: 'login', dir: 'asc' }

type Editor =
  | { mode: 'create'; login: string; displayName: string; role: Role }
  | { mode: 'edit'; user: AdminUser; displayName: string; role: Role; enabled: boolean }

/**
 * Администрирование пользователей. Состояние фильтров хранится в адресе страницы (?q=…&role=…),
 * поэтому список переживает перезагрузку и ссылкой можно поделиться.
 */
export function AdminUsersPage() {
  const { user: me } = useAuth()
  const [params, setParams] = useSearchParams()

  const q = params.get('q') ?? ''
  const role = (ROLE_ORDER as string[]).includes(params.get('role') ?? '') ? (params.get('role') as Role) : ''
  const enabled = params.get('enabled') === 'true' || params.get('enabled') === 'false' ? params.get('enabled')! : ''
  const page = intParam(params.get('page'), 0)
  const size = intParam(params.get('size'), 20, PAGE_SIZES)
  const sort = parseSort(params.get('sort'), DEFAULT_SORT)

  const [text, setText] = useUrlTextFields({ q }, update)
  const [data, setData] = useState<PageResponse<AdminUser> | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [editor, setEditor] = useState<Editor | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [secret, setSecret] = useState<UserWithPassword | null>(null)
  const [copied, setCopied] = useState(false)

  /** Меняет параметры адреса; при смене фильтра возвращаемся на первую страницу. */
  function update(changes: Record<string, string>, resetPage = true) {
    setParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        for (const [key, value] of Object.entries(changes)) {
          if (value === '') next.delete(key)
          else next.set(key, value)
        }
        if (resetPage) next.delete('page')
        return next
      },
      { replace: true },
    )
  }

  const query = new URLSearchParams()
  if (q) query.set('q', q)
  if (role) query.set('role', role)
  if (enabled) query.set('enabled', enabled)
  query.set('page', String(page))
  query.set('size', String(size))
  query.set('sort', formatSort(sort))
  const queryKey = query.toString()

  useEffect(() => {
    let live = true
    setLoading(true)
    setError(null)
    adminUsersApi
      .list(new URLSearchParams(queryKey))
      .then((r) => {
        if (!live) return
        setData(r)
        // после удаления фильтром последней строки страницы — шаг назад
        if (page > 0 && page >= r.totalPages) update({ page: String(Math.max(0, r.totalPages - 1)) }, false)
      })
      .catch((e) => {
        if (live) {
          setError(e)
          setData(null)
        }
      })
      .finally(() => live && setLoading(false))
    return () => {
      live = false
    }
  }, [queryKey, reloadKey])

  async function run(action: () => Promise<void>) {
    setBusy(true)
    setError(null)
    setFieldErrors({})
    try {
      await action()
    } catch (e) {
      if (e instanceof ApiError && Object.keys(e.fieldErrors).length) setFieldErrors(e.fieldErrors)
      setError(e)
    } finally {
      setBusy(false)
    }
  }

  function openCreate() {
    setSecret(null)
    setFieldErrors({})
    setError(null)
    setEditor({ mode: 'create', login: '', displayName: '', role: 'MODERATOR' })
  }

  function openEdit(u: AdminUser) {
    setSecret(null)
    setFieldErrors({})
    setError(null)
    setEditor({ mode: 'edit', user: u, displayName: u.displayName, role: topRole(u.roles), enabled: u.enabled })
  }

  function showSecret(result: UserWithPassword) {
    setSecret(result)
    setCopied(false)
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (!editor) return
    await run(async () => {
      if (editor.mode === 'create') {
        showSecret(await adminUsersApi.create(editor.login.trim(), editor.displayName.trim(), editor.role))
        setEditor(null)
      } else {
        await adminUsersApi.update(editor.user, editor.displayName.trim(), editor.role, editor.enabled)
        setEditor(null)
      }
      setReloadKey((k) => k + 1)
    })
  }

  async function resetPassword(u: AdminUser) {
    if (!window.confirm(`Сбросить пароль пользователя ${u.login}? Его текущие сессии будут закрыты.`)) return
    await run(async () => {
      showSecret(await adminUsersApi.resetPassword(u.id))
      setEditor(null)
      setReloadKey((k) => k + 1)
    })
  }

  async function unlock(u: AdminUser) {
    await run(async () => {
      const saved = await adminUsersApi.unlock(u.id)
      if (editor?.mode === 'edit') setEditor({ ...editor, user: saved })
      setReloadKey((k) => k + 1)
    })
  }

  async function reloadEditor(u: AdminUser) {
    await run(async () => {
      openEdit(await adminUsersApi.get(u.id))
      setReloadKey((k) => k + 1)
    })
  }

  async function copySecret() {
    if (!secret) return
    try {
      await navigator.clipboard.writeText(secret.temporaryPassword)
      setCopied(true)
    } catch {
      setCopied(false)
    }
  }

  const isSelf = editor?.mode === 'edit' && editor.user.id === me?.id
  const filtersActive = Boolean(q || role || enabled)

  return (
    <section className="stack">
      <div className="toolbar">
        <h1>Пользователи</h1>
        <span className="spacer" />
        <button className="btn primary" disabled={busy} onClick={openCreate}>
          Новый пользователь
        </button>
        <button className="btn ghost" disabled={busy || loading} onClick={() => setReloadKey((k) => k + 1)}>
          Обновить
        </button>
      </div>

      <OperationError error={error} />

      {secret && (
        <div className="card stack secret" role="status">
          <h2>Временный пароль для {secret.user.login}</h2>
          <p className="muted">
            Пароль показывается один раз. Передайте его пользователю — при первом входе система попросит его сменить.
          </p>
          <div className="toolbar">
            <code className="secret-value">{secret.temporaryPassword}</code>
            <button className="btn ghost" onClick={() => void copySecret()}>
              {copied ? 'Скопировано' : 'Копировать'}
            </button>
            <span className="spacer" />
            <button className="btn ghost" onClick={() => setSecret(null)}>
              Скрыть
            </button>
          </div>
        </div>
      )}

      {editor && (
        <form className="card stack" onSubmit={submit} noValidate>
          <h2>{editor.mode === 'create' ? 'Новый пользователь' : `Пользователь ${editor.user.login}`}</h2>
          <fieldset disabled={busy} className="form-grid">
            {editor.mode === 'create' ? (
              <label className="field">
                <span>Логин</span>
                <input
                  required
                  autoFocus
                  maxLength={120}
                  value={editor.login}
                  aria-invalid={Boolean(fieldErrors.login)}
                  onChange={(e) => setEditor({ ...editor, login: e.target.value })}
                  placeholder="ivanov или ivanov@mail.ru"
                />
                {fieldErrors.login && <small className="error">{fieldErrors.login}</small>}
              </label>
            ) : (
              <div className="field">
                <span>Логин</span>
                <p>{editor.user.login}</p>
              </div>
            )}
            <label className="field">
              <span>Имя</span>
              <input
                required
                maxLength={120}
                value={editor.displayName}
                aria-invalid={Boolean(fieldErrors.displayName)}
                onChange={(e) => setEditor({ ...editor, displayName: e.target.value })}
              />
              {fieldErrors.displayName && <small className="error">{fieldErrors.displayName}</small>}
            </label>
            <label className="field">
              <span>Роль</span>
              <select
                value={editor.role}
                disabled={isSelf}
                onChange={(e) => setEditor({ ...editor, role: e.target.value as Role })}
              >
                {ROLE_ORDER.map((r) => (
                  <option key={r} value={r}>
                    {ROLE_TITLES[r]}
                  </option>
                ))}
              </select>
              {fieldErrors.roles && <small className="error">{fieldErrors.roles}</small>}
            </label>
            {editor.mode === 'edit' && (
              <label className="field">
                <span>Учётная запись активна</span>
                <input
                  type="checkbox"
                  checked={editor.enabled}
                  disabled={isSelf}
                  onChange={(e) => setEditor({ ...editor, enabled: e.target.checked })}
                />
              </label>
            )}
          </fieldset>
          <p className="muted">
            {editor.mode === 'create'
              ? 'Пароль задавать не нужно: система сгенерирует временный и покажет его один раз.'
              : isSelf
                ? 'Это ваша учётная запись: роль и активность меняет другой администратор.'
                : 'Отключённый пользователь сразу теряет доступ, его сессии закрываются.'}
          </p>
          <div className="toolbar">
            <button className="btn primary" disabled={busy}>
              {editor.mode === 'create' ? 'Создать' : 'Сохранить'}
            </button>
            {editor.mode === 'edit' && !isSelf && (
              <button type="button" className="btn ghost" disabled={busy} onClick={() => void resetPassword(editor.user)}>
                Сбросить пароль
              </button>
            )}
            {editor.mode === 'edit' && (
              <button type="button" className="btn ghost" disabled={busy} onClick={() => void reloadEditor(editor.user)}>
                Перечитать данные
              </button>
            )}
            {editor.mode === 'edit' && editor.user.locked && (
              <button type="button" className="btn ghost" disabled={busy} onClick={() => void unlock(editor.user)}>
                Снять блокировку
              </button>
            )}
            <button type="button" className="btn ghost" disabled={busy} onClick={() => setEditor(null)}>
              Отмена
            </button>
          </div>
        </form>
      )}

      <div className="card filters" role="search">
        <label className="field">
          <span>Поиск</span>
          <input
            type="search"
            value={text.q}
            maxLength={100}
            placeholder="Логин или имя"
            onChange={(e) => setText({ q: e.target.value })}
          />
        </label>
        <label className="field">
          <span>Роль</span>
          <select value={role} onChange={(e) => update({ role: e.target.value })}>
            <option value="">Любая</option>
            {ROLE_ORDER.map((r) => (
              <option key={r} value={r}>
                {ROLE_TITLES[r]}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span>Статус</span>
          <select value={enabled} onChange={(e) => update({ enabled: e.target.value })}>
            <option value="">Все</option>
            <option value="true">Активные</option>
            <option value="false">Отключённые</option>
          </select>
        </label>
        {filtersActive && (
          <button
            className="btn ghost"
            onClick={() => {
              setText({ q: '' })
              update({ q: '', role: '', enabled: '' })
            }}
          >
            Сбросить фильтры
          </button>
        )}
      </div>

      <div className="card table-wrap" aria-busy={loading}>
        <table>
          <thead>
            <tr>
              <SortHeader field="login" label="Логин" sort={sort} onSort={(s) => update({ sort: formatSort(s) }, false)} />
              <SortHeader
                field="displayName"
                label="Имя"
                sort={sort}
                onSort={(s) => update({ sort: formatSort(s) }, false)}
              />
              <th>Роль</th>
              <th>Состояние</th>
              <SortHeader
                field="lastLoginAt"
                label="Последний вход"
                firstDir="desc"
                sort={sort}
                onSort={(s) => update({ sort: formatSort(s) }, false)}
              />
              <SortHeader
                field="createdAt"
                label="Создан"
                firstDir="desc"
                sort={sort}
                onSort={(s) => update({ sort: formatSort(s) }, false)}
              />
              <th />
            </tr>
          </thead>
          <tbody>
            {data?.content.map((u) => (
              <tr key={u.id} className={editor?.mode === 'edit' && editor.user.id === u.id ? 'selected' : undefined}>
                <td>
                  {u.login}
                  {u.id === me?.id && <span className="muted"> (вы)</span>}
                </td>
                <td>{u.displayName}</td>
                <td>{ROLE_TITLES[topRole(u.roles)]}</td>
                <td>
                  <span className="chips">
                    {u.enabled ? <span className="chip ok">Активен</span> : <span className="chip off">Отключён</span>}
                    {u.locked && <span className="chip warn">Заблокирован</span>}
                    {u.mustChangePassword && <span className="chip">Временный пароль</span>}
                  </span>
                </td>
                <td>{formatInstant(u.lastLoginAt)}</td>
                <td>{formatInstant(u.createdAt)}</td>
                <td>
                  <button className="text-button" disabled={busy || loading} onClick={() => openEdit(u)}>
                    Изменить
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {loading && !data && <p className="empty">Загрузка…</p>}
        {!loading && !error && !data?.content.length && (
          <p className="empty">{filtersActive ? 'Никого не нашлось — измените условия поиска' : 'Пользователей нет'}</p>
        )}
      </div>

      <Pager
        page={page}
        size={size}
        totalPages={data?.totalPages ?? 0}
        totalElements={data?.totalElements ?? 0}
        disabled={loading || busy}
        onPage={(p) => update({ page: String(p) }, false)}
        onSize={(s) => update({ size: String(s) })}
      />
    </section>
  )
}
