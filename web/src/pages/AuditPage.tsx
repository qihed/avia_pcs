import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { auditApi, type AuditEvent } from '../api/audit'
import './katya-dashboard.css'

export function AuditPage() {
  const [items, setItems] = useState<AuditEvent[]>([])
  const [q, setQ] = useState('')
  const [eventType, setEventType] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = useCallback(async (search = '', type = '') => {
    setLoading(true)
    setError('')
    try {
      setItems(await auditApi.list(search, type))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Не удалось загрузить журнал')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { void load() }, [load])

  const submit = (e: FormEvent) => {
    e.preventDefault()
    void load(q.trim(), eventType.trim())
  }

  const reset = () => {
    setQ('')
    setEventType('')
    void load()
  }

  return (
    <main className="katya-page">
      <header className="katya-heading">
        <div>
          <div className="katya-eyebrow">АДМИНИСТРИРОВАНИЕ / БЕЗОПАСНОСТЬ</div>
          <h1>Журнал аудита</h1>
          <p>История действий пользователей и изменений в системе</p>
        </div>
        <button className="katya-button secondary" onClick={() => void load(q, eventType)}>
          ↻ Обновить
        </button>
      </header>

      <section className="katya-panel">
        <form className="katya-filters" onSubmit={submit}>
          <label className="katya-field">
            <span>Поиск по событиям</span>
            <input
              value={q}
              onChange={e => setQ(e.target.value)}
              placeholder="Пользователь, описание, ID..."
            />
          </label>
          <label className="katya-field">
            <span>Тип события</span>
            <input
              value={eventType}
              onChange={e => setEventType(e.target.value)}
              placeholder="Например, BOOKING_UPDATED"
            />
          </label>
          <button className="katya-button primary" type="submit">Найти</button>
          <button className="katya-button secondary" type="button" onClick={reset}>Сбросить</button>
        </form>
      </section>

      <section className="katya-panel katya-results">
        <div className="katya-panel-heading">
          <div>
            <h2>История событий</h2>
            <p>Действия, зарегистрированные сервером</p>
          </div>
          <span className="katya-counter">{loading ? 'Загрузка' : `${items.length} событий`}</span>
        </div>

        {error ? (
          <div className="katya-message error">Ошибка загрузки: {error}</div>
        ) : loading ? (
          <div className="katya-message">Загружаем события аудита…</div>
        ) : items.length === 0 ? (
          <div className="katya-empty">
            <div className="katya-empty-icon">◎</div>
            <h3>События не найдены</h3>
            <p>В журнале пока нет записей либо по заданным фильтрам ничего не найдено.</p>
            <button className="katya-button secondary" onClick={reset}>Показать все события</button>
          </div>
        ) : (
          <div className="katya-table-scroll">
            <table className="katya-table">
              <thead>
                <tr>
                  <th>Тип события</th>
                  <th>Пользователь</th>
                  <th>Описание</th>
                  <th>Correlation ID</th>
                </tr>
              </thead>
              <tbody>
                {items.map(item => (
                  <tr key={item.id}>
                    <td><span className="katya-event">{item.eventType}</span></td>
                    <td>{item.username || 'Система'}</td>
                    <td className="katya-description">{item.description || '—'}</td>
                    <td>
                      {item.correlationId ? (
                        <button
                          type="button"
                          className="katya-code"
                          title="Скопировать Correlation ID"
                          onClick={() => void navigator.clipboard.writeText(item.correlationId!)}
                        >
                          {item.correlationId}
                        </button>
                      ) : '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </main>
  )
}
