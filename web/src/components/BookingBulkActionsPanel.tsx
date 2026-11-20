import { useCallback, useEffect, useState, type ChangeEvent } from 'react'
import {
  bookingManagementApi,
  type BookingCsvPreview,
  type BookingManagementRow,
} from '../api/bookingManagement'
import './booking-bulk-actions.css'

const statuses = ['CREATED', 'PAID', 'CHECKED_IN', 'COMPLETED', 'CANCELLED', 'EXPIRED']

const statusNames: Record<string, string> = {
  CREATED: 'Создано',
  PAID: 'Оплачено',
  CHECKED_IN: 'Регистрация',
  COMPLETED: 'Завершено',
  CANCELLED: 'Отменено',
  EXPIRED: 'Истекло',
}

export function BookingBulkActionsPanel() {
  const [rows, setRows] = useState<BookingManagementRow[]>([])
  const [selected, setSelected] = useState<string[]>([])
  const [status, setStatus] = useState('PAID')
  const [csv, setCsv] = useState('')
  const [fileName, setFileName] = useState('')
  const [preview, setPreview] = useState<BookingCsvPreview | null>(null)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setRows(await bookingManagementApi.list())
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Ошибка загрузки')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { void load() }, [load])

  const execute = async (operation: () => Promise<void>) => {
    setBusy(true)
    setError('')
    setMessage('')
    try {
      await operation()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Операция не выполнена')
    } finally {
      setBusy(false)
    }
  }

  const toggle = (id: string) => {
    setSelected(current =>
      current.includes(id) ? current.filter(x => x !== id) : [...current, id]
    )
  }

  const allSelected = rows.length > 0 && rows.every(r => selected.includes(r.id))

  const toggleAll = () => {
    setSelected(allSelected ? [] : rows.map(r => r.id))
  }

  const bulk = (statusValue?: string, archived?: boolean) => {
    if (!selected.length) return

    const action = archived === true
      ? 'переместить в архив'
      : archived === false
        ? 'вернуть из архива'
        : `изменить статус на «${statusNames[statusValue || ''] || statusValue}»`

    if (!window.confirm(`Вы действительно хотите ${action} для ${selected.length} бронирований?`)) return

    void execute(async () => {
      const result = await bookingManagementApi.bulkUpdate(selected, statusValue, archived)
      setMessage(`Успешно обновлено бронирований: ${result.updated}`)
      setSelected([])
      await load()
    })
  }

  const choose = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    setCsv(await file.text())
    setFileName(file.name)
    setPreview(null)
    setMessage('')
    setError('')
  }

  const runPreview = () => {
    void execute(async () => {
      setPreview(null)
      setPreview(await bookingManagementApi.previewCsv(csv))
    })
  }

  const apply = () => {
    if (!preview || preview.invalidRows > 0 || !preview.validRows) return

    if (!window.confirm(`Применить изменения из CSV? Корректных строк: ${preview.validRows}.`)) return

    void execute(async () => {
      const result = await bookingManagementApi.applyCsv(csv)
      setMessage(`CSV применён. Обновлено бронирований: ${result.updated}`)
      setPreview(null)
      setCsv('')
      setFileName('')
      setSelected([])
      await load()
    })
  }

  return (
    <section className="kb-root">
      <div className="kb-header">
        <div>
          <div className="kb-eyebrow">УПРАВЛЕНИЕ БРОНИРОВАНИЯМИ</div>
          <h2>Массовые операции и CSV</h2>
          <p>Изменение выбранных бронирований и импорт/экспорт данных</p>
        </div>
        <button className="kb-btn kb-secondary" disabled={busy} onClick={() => void load()}>
          ↻ Обновить
        </button>
      </div>

      {error && <div className="kb-notice kb-error" role="alert">{error}</div>}
      {message && <div className="kb-notice kb-success" role="status">{message}</div>}

      <div className="kb-panel">
        <div className="kb-panel-heading">
          <div>
            <h3>Массовое изменение</h3>
            <p>Выберите бронирования в таблице ниже</p>
          </div>
          <span className="kb-counter">Выбрано: {selected.length}</span>
        </div>
        <div className="kb-toolbar">
          <label className="kb-field">
            <span>Новый статус</span>
            <select value={status} onChange={e => setStatus(e.target.value)} disabled={busy}>
              {statuses.map(s => <option key={s} value={s}>{statusNames[s]}</option>)}
            </select>
          </label>
          <button className="kb-btn kb-primary" disabled={busy || !selected.length} onClick={() => bulk(status)}>
            Изменить статус
          </button>
          <button className="kb-btn kb-secondary" disabled={busy || !selected.length} onClick={() => bulk(undefined, true)}>
            В архив
          </button>
          <button className="kb-btn kb-secondary" disabled={busy || !selected.length} onClick={() => bulk(undefined, false)}>
            Вернуть из архива
          </button>
        </div>
      </div>

      <div className="kb-panel">
        <div className="kb-panel-heading">
          <div>
            <h3>Импорт и экспорт CSV</h3>
            <p>Перед импортом проверьте строки и возможные ошибки</p>
          </div>
          <button
            className="kb-btn kb-secondary"
            disabled={busy}
            onClick={() => void execute(async () => {
              await bookingManagementApi.exportCsv()
              setMessage('CSV-файл подготовлен к скачиванию')
            })}
          >
            ↓ Экспорт CSV
          </button>
        </div>

        <div className="kb-upload">
          <label className="kb-upload-label">
            <span className="kb-upload-icon">↑</span>
            <strong>{fileName || 'Выбрать CSV-файл'}</strong>
            <small>Нажмите для выбора файла с компьютера</small>
            <input type="file" accept=".csv,text/csv" onChange={choose} disabled={busy} />
          </label>
          <div className="kb-upload-actions">
            <button className="kb-btn kb-secondary" disabled={busy || !csv} onClick={runPreview}>
              Предпросмотр
            </button>
            <button
              className="kb-btn kb-primary"
              disabled={busy || !preview || preview.invalidRows > 0 || preview.validRows === 0}
              onClick={apply}
            >
              Применить CSV
            </button>
          </div>
        </div>

        {preview && (
          <div className="kb-preview">
            <div className="kb-preview-summary">
              <span>Всего строк: <b>{preview.totalRows}</b></span>
              <span>Корректных: <b className="kb-green">{preview.validRows}</b></span>
              <span>Ошибок: <b className="kb-red">{preview.invalidRows}</b></span>
            </div>
            {preview.invalidRows > 0 && (
              <p className="kb-warning">Импорт заблокирован: исправьте ошибки в CSV и повторите предпросмотр.</p>
            )}
            <div className="kb-table-wrap">
              <table className="kb-table">
                <thead><tr><th>Строка</th><th>Код брони</th><th>Статус</th><th>Архив</th><th>Проверка</th></tr></thead>
                <tbody>
                  {preview.rows.map((r, i) => (
                    <tr key={`${r.line}-${i}`}>
                      <td>{r.line}</td>
                      <td>{r.bookingCode || '—'}</td>
                      <td>{statusNames[r.status] || r.status || '—'}</td>
                      <td>{r.archived === undefined ? 'Без изменений' : r.archived ? 'Да' : 'Нет'}</td>
                      <td>
                        <span className={r.valid ? 'kb-green' : 'kb-red'}>
                          {r.valid ? '✓ Корректно' : `✕ ${r.error || 'Ошибка'}`}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>

      <div className="kb-panel">
        <div className="kb-panel-heading">
          <div>
            <h3>Список бронирований</h3>
            <p>Данные из PostgreSQL</p>
          </div>
          <span className="kb-counter">{rows.length} записей</span>
        </div>

        {loading ? <div className="kb-empty">Загрузка бронирований…</div> :
          rows.length === 0 ? <div className="kb-empty">Бронирования не найдены</div> : (
            <div className="kb-table-wrap">
              <table className="kb-table">
                <thead>
                  <tr>
                    <th><input aria-label="Выбрать все" type="checkbox" checked={allSelected} onChange={toggleAll} /></th>
                    <th>Код брони</th>
                    <th>Пассажир</th>
                    <th>Рейс</th>
                    <th>Стоимость</th>
                    <th>Статус</th>
                    <th>Архив</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map(r => (
                    <tr key={r.id} className={selected.includes(r.id) ? 'kb-selected' : ''}>
                      <td><input aria-label={`Выбрать ${r.bookingCode}`} type="checkbox" checked={selected.includes(r.id)} onChange={() => toggle(r.id)} /></td>
                      <td><strong>{r.bookingCode}</strong></td>
                      <td>{r.passenger}</td>
                      <td>{r.flightNumber}</td>
                      <td>{Number(r.price).toLocaleString('ru-RU')} ₽</td>
                      <td><span className="kb-status">{statusNames[r.status] || r.status}</span></td>
                      <td>{r.archived ? 'Да' : 'Нет'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
      </div>
    </section>
  )
}
