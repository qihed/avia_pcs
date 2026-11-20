import { useCallback, useEffect, useState } from 'react'
import { analyticsApi, type AnalyticsSummary } from '../api/analytics'
import './katya-dashboard.css'

const statusNames: Record<string, string> = {
  CREATED: 'Создано',
  PAID: 'Оплачено',
  CHECKED_IN: 'Регистрация',
  COMPLETED: 'Завершено',
  CANCELLED: 'Отменено',
}

const classNames: Record<string, string> = {
  ECONOMY: 'Эконом',
  COMFORT: 'Комфорт',
  BUSINESS: 'Бизнес',
}

const colors = ['#60a5fa', '#a78bfa', '#34d399', '#fbbf24', '#fb7185']

export function AnalyticsPage() {
  const [data, setData] = useState<AnalyticsSummary | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      setData(await analyticsApi.summary())
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Не удалось получить аналитику')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { void load() }, [load])

  if (loading) return <div className="katya-page katya-message">Загрузка аналитики…</div>

  if (error) return (
    <main className="katya-page">
      <div className="katya-message error">{error}</div>
      <button className="katya-button primary" onClick={() => void load()}>Повторить</button>
    </main>
  )

  if (!data) return <div className="katya-page katya-message">Данные отсутствуют</div>

  const metrics = [
    { label: 'Всего бронирований', value: data.totalBookings, icon: '◈', color: 'blue' },
    { label: 'Активных бронирований', value: data.activeBookings, icon: '✓', color: 'green' },
    { label: 'Пассажиров', value: data.totalPassengers, icon: '♙', color: 'purple' },
    { label: 'Выручка', value: `${Number(data.revenue).toLocaleString('ru-RU')} ₽`, icon: '₽', color: 'amber' },
  ]

  const statuses = data.bookingsByStatus
  const fareClasses = data.bookingsByFareClass
  const statusTotal = statuses.reduce((sum, item) => sum + Number(item.count), 0)
  const classTotal = fareClasses.reduce((sum, item) => sum + Number(item.count), 0)

  return (
    <main className="katya-page">
      <header className="katya-heading">
        <div>
          <div className="katya-eyebrow">ОБЗОР / ПОКАЗАТЕЛИ</div>
          <h1>Аналитика бронирований</h1>
          <p>Ключевые показатели системы на основе данных PostgreSQL</p>
        </div>
        <button className="katya-button secondary" onClick={() => void load()}>↻ Обновить</button>
      </header>

      <div className="katya-metrics">
        {metrics.map(metric => (
          <article className={`katya-metric ${metric.color}`} key={metric.label}>
            <div className="katya-metric-top">
              <span>{metric.label}</span>
              <span className="katya-metric-icon">{metric.icon}</span>
            </div>
            <strong>{metric.value}</strong>
          </article>
        ))}
      </div>

      <div className="katya-chart-grid">
        <section className="katya-panel">
          <div className="katya-panel-heading">
            <div>
              <h2>Статусы бронирований</h2>
              <p>Распределение по текущему состоянию</p>
            </div>
            <span className="katya-counter">{statusTotal} всего</span>
          </div>

          {statusTotal > 0 ? (
            <>
              <div className="katya-segments">
                {statuses.map((item, index) => (
                  <div
                    key={item.name}
                    style={{
                      width: `${Number(item.count) / statusTotal * 100}%`,
                      background: colors[index % colors.length]
                    }}
                    title={`${statusNames[item.name] || item.name}: ${item.count}`}
                  />
                ))}
              </div>
              <div className="katya-legend">
                {statuses.map((item, index) => (
                  <div key={item.name} className="katya-legend-row">
                    <span className="katya-dot" style={{ background: colors[index % colors.length] }} />
                    <span>{statusNames[item.name] || item.name}</span>
                    <strong>{item.count}</strong>
                    <span className="katya-percent">
                      {Math.round(Number(item.count) / statusTotal * 100)}%
                    </span>
                  </div>
                ))}
              </div>
            </>
          ) : <div className="katya-message">Нет данных о статусах</div>}
        </section>

        <section className="katya-panel">
          <div className="katya-panel-heading">
            <div>
              <h2>Классы обслуживания</h2>
              <p>Популярность тарифных классов</p>
            </div>
            <span className="katya-counter">{classTotal} всего</span>
          </div>

          <div className="katya-bars">
            {fareClasses.length === 0 ? (
              <div className="katya-message">Нет данных о классах</div>
            ) : fareClasses.map((item, index) => {
              const percent = classTotal ? Number(item.count) / classTotal * 100 : 0
              return (
                <div className="katya-bar-item" key={item.name}>
                  <div className="katya-bar-label">
                    <span>{classNames[item.name] || item.name}</span>
                    <strong>{item.count} <small>({Math.round(percent)}%)</small></strong>
                  </div>
                  <div className="katya-bar-track">
                    <div
                      className="katya-bar-fill"
                      style={{ width: `${percent}%`, background: colors[index % colors.length] }}
                    />
                  </div>
                </div>
              )
            })}
          </div>
        </section>
      </div>

      <div className="katya-bottom-stats">
        <span>Всего рейсов: <strong>{data.totalFlights}</strong></span>
        <span>Бронирований в архиве: <strong>{data.archivedBookings}</strong></span>
      </div>
    </main>
  )
}
