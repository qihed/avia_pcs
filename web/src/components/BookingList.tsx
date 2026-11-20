import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { date, refs, statusNames, urgencyNames, type Booking, type BookingStatus, type Flight, type Page } from '../api/booking'
import {
  bookingSearchApi,
  FARE_CLASSES,
  fareNames,
  FILTER_KEYS,
  STATUSES,
  URGENCIES,
  type BulkAction,
  type BulkResult,
  type FareClass,
  type TagUsage,
} from '../api/bookingSearch'
import { formatSort, intParam, PAGE_SIZES, Pager, parseSort, SortHeader, useDebounced, type SortState } from './ListControls'
import { OperationError } from './OperationError'

const DEFAULT_SORT: SortState = { field: 'createdAt', dir: 'desc' }

const BULK_TITLES: Record<BulkAction, string> = {
  ADD_TAG: 'Добавить тег',
  REMOVE_TAG: 'Снять тег',
  SET_STATUS: 'Сменить статус',
  ARCHIVE: 'В архив',
  RESTORE: 'Восстановить из архива',
}

interface Props {
  /** Идёт операция в карточке — список блокируется */
  busy: boolean
  /** Увеличивается родителем после изменения брони — список перечитывается */
  reloadKey: number
  selectedId?: string
  editor: boolean
  onOpen: (booking: Booking) => void
  /** Список изменил брони сам (массовая операция) — родитель обновит открытую карточку */
  onChanged: () => void
}

/**
 * Список броней: поиск, фильтры, сортировка по заголовкам, пагинация и массовые операции.
 * Все условия хранятся в адресе страницы и в том же виде уходят в GET /api/items.
 */
export function BookingList({ busy, reloadKey, selectedId, editor, onOpen, onChanged }: Props) {
  const [params, setParams] = useSearchParams()
  const archived = params.get('archived') === 'true'
  const page = intParam(params.get('page'), 0)
  const size = intParam(params.get('size'), 20, PAGE_SIZES)
  const sort = parseSort(params.get('sort'), DEFAULT_SORT)
  const statuses = (params.get('status') ?? '').split(',').filter((s): s is BookingStatus =>
    (STATUSES as string[]).includes(s),
  )

  // текстовые поля применяются с задержкой, чтобы не отправлять запрос на каждую букву
  const [text, setText] = useState({
    q: params.get('q') ?? '',
    priceMin: params.get('priceMin') ?? '',
    priceMax: params.get('priceMax') ?? '',
  })
  const debouncedText = useDebounced(text, 400)

  const [data, setData] = useState<Page<Booking> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)
  const [localReload, setLocalReload] = useState(0)
  const [tags, setTags] = useState<TagUsage[]>([])
  const [flights, setFlights] = useState<Flight[]>([])
  const [moreFilters, setMoreFilters] = useState(() =>
    ['flightId', 'priceMin', 'priceMax', 'departureFrom', 'departureTo', 'extraBaggage'].some((k) => params.has(k)),
  )

  const [selected, setSelected] = useState<Map<string, string>>(new Map())
  const [bulkAction, setBulkAction] = useState<BulkAction>('ADD_TAG')
  const [bulkTag, setBulkTag] = useState('')
  const [bulkStatus, setBulkStatus] = useState<BookingStatus>('PAID')
  const [bulkBusy, setBulkBusy] = useState(false)
  const [bulkResult, setBulkResult] = useState<BulkResult | null>(null)

  /** Меняет параметры адреса. Смена фильтра сбрасывает страницу и выделение. */
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
    if (resetPage) setSelected(new Map())
  }

  useEffect(() => {
    const changes: Record<string, string> = {}
    for (const key of ['q', 'priceMin', 'priceMax'] as const) {
      const value = debouncedText[key].trim()
      if (value !== (params.get(key) ?? '')) changes[key] = value
    }
    if (Object.keys(changes).length) update(changes)
  }, [debouncedText])

  // запрос к backend собирается только из известных параметров адреса
  const queryKey = useMemo(() => {
    const query = new URLSearchParams()
    for (const key of FILTER_KEYS) {
      const value = params.get(key)
      if (value) query.set(key, value)
    }
    query.set('archived', String(archived))
    query.set('page', String(page))
    query.set('size', String(size))
    query.set('sort', formatSort(sort))
    return query.toString()
  }, [params])

  useEffect(() => {
    let live = true
    setLoading(true)
    setError(null)
    bookingSearchApi
      .list(new URLSearchParams(queryKey))
      .then((r) => {
        if (!live) return
        setData(r)
        if (r.totalPages > 0 && page >= r.totalPages) update({ page: String(r.totalPages - 1) }, false)
      })
      .catch((e) => live && setError(e))
      .finally(() => live && setLoading(false))
    return () => {
      live = false
    }
  }, [queryKey, reloadKey, localReload])

  // справочники для фильтров: теги с количеством и рейсы
  useEffect(() => {
    bookingSearchApi.tags().then(setTags, () => setTags([]))
  }, [reloadKey, localReload])
  useEffect(() => {
    if (moreFilters && !flights.length) refs.flights().then(setFlights, () => setFlights([]))
  }, [moreFilters])

  // в архиве доступно только восстановление, в текущих — всё остальное
  useEffect(() => {
    setBulkAction(archived ? 'RESTORE' : 'ADD_TAG')
    setBulkResult(null)
  }, [archived])

  function toggleStatus(status: BookingStatus) {
    const next = statuses.includes(status) ? statuses.filter((s) => s !== status) : [...statuses, status]
    update({ status: next.join(',') })
  }

  function resetFilters() {
    setText({ q: '', priceMin: '', priceMax: '' })
    update(Object.fromEntries(FILTER_KEYS.map((k) => [k, ''])))
  }

  const activeFilters = FILTER_KEYS.filter((k) => params.get(k)).length
  const rows = data?.content ?? []
  const allOnPage = rows.length > 0 && rows.every((b) => selected.has(b.id))

  function toggleRow(b: Booking) {
    const next = new Map(selected)
    if (next.has(b.id)) next.delete(b.id)
    else next.set(b.id, b.bookingCode)
    setSelected(next)
  }

  function togglePage() {
    const next = new Map(selected)
    rows.forEach((b) => (allOnPage ? next.delete(b.id) : next.set(b.id, b.bookingCode)))
    setSelected(next)
  }

  async function runBulk() {
    const extra =
      bulkAction === 'SET_STATUS'
        ? { status: bulkStatus }
        : bulkAction === 'ADD_TAG' || bulkAction === 'REMOVE_TAG'
          ? { tag: bulkTag }
          : {}
    if (!window.confirm(`${BULK_TITLES[bulkAction]} — ${selected.size} ${plural(selected.size)}. Продолжить?`)) return
    setBulkBusy(true)
    setError(null)
    try {
      const result = await bookingSearchApi.bulk([...selected.keys()], bulkAction, extra)
      setBulkResult(result)
      // неудачные остаются выделенными: их можно поправить и повторить
      const failed = new Set(result.failures.map((f) => f.id))
      setSelected(new Map([...selected].filter(([id]) => failed.has(id))))
      setLocalReload((k) => k + 1)
      onChanged()
    } catch (e) {
      setError(e)
    } finally {
      setBulkBusy(false)
    }
  }

  const disabled = busy || bulkBusy
  const bulkReady =
    selected.size > 0 && ((bulkAction !== 'ADD_TAG' && bulkAction !== 'REMOVE_TAG') || bulkTag.trim().length > 0)
  const onSort = (s: SortState) => update({ sort: formatSort(s) }, false)

  return (
    <div className="stack">
      <div className="toolbar">
        <button
          className={'btn ' + (!archived ? 'primary' : 'ghost')}
          disabled={disabled}
          onClick={() => update({ archived: '' })}
        >
          Текущие
        </button>
        <button
          className={'btn ' + (archived ? 'primary' : 'ghost')}
          disabled={disabled}
          onClick={() => update({ archived: 'true' })}
        >
          Архив
        </button>
        <span className="muted">{data?.totalElements ?? 0} записей</span>
      </div>

      <div className="card filters" role="search">
        <label className="field wide">
          <span>Поиск</span>
          <input
            type="search"
            maxLength={100}
            value={text.q}
            placeholder="Код, фамилия, e-mail, документ, рейс, аэропорт, место"
            onChange={(e) => setText({ ...text, q: e.target.value })}
          />
        </label>
        <label className="field">
          <span>Класс</span>
          <select value={params.get('fareClass') ?? ''} onChange={(e) => update({ fareClass: e.target.value })}>
            <option value="">Любой</option>
            {FARE_CLASSES.map((f: FareClass) => (
              <option key={f} value={f}>
                {fareNames[f]}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span>Срочность</span>
          <select value={params.get('urgency') ?? ''} onChange={(e) => update({ urgency: e.target.value })}>
            <option value="">Любая</option>
            {URGENCIES.map((u) => (
              <option key={u} value={u}>
                {urgencyNames[u]}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span>Тег</span>
          <select value={params.get('tag') ?? ''} onChange={(e) => update({ tag: e.target.value })}>
            <option value="">Любой</option>
            {params.get('tag') && !tags.some((t) => t.tag === params.get('tag')) && (
              <option value={params.get('tag')!}>{params.get('tag')}</option>
            )}
            {tags.map((t) => (
              <option key={t.tag} value={t.tag}>
                {t.tag} ({t.count})
              </option>
            ))}
          </select>
        </label>

        <fieldset className="status-filter">
          <legend>Статус</legend>
          {STATUSES.map((s) => (
            <label key={s} className={'chip toggle' + (statuses.includes(s) ? ' on' : '')}>
              <input type="checkbox" checked={statuses.includes(s)} onChange={() => toggleStatus(s)} />
              {statusNames[s]}
            </label>
          ))}
        </fieldset>

        {moreFilters && (
          <>
            <label className="field">
              <span>Рейс</span>
              <select value={params.get('flightId') ?? ''} onChange={(e) => update({ flightId: e.target.value })}>
                <option value="">Любой</option>
                {flights.map((f) => (
                  <option key={f.id} value={f.id}>
                    {f.flightNumber} · {f.departureAirport}→{f.arrivalAirport} · {date(f.departureTime)}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              <span>Цена от, ₽</span>
              <input
                type="number"
                min="0"
                step="1"
                inputMode="decimal"
                value={text.priceMin}
                onChange={(e) => setText({ ...text, priceMin: e.target.value })}
              />
            </label>
            <label className="field">
              <span>Цена до, ₽</span>
              <input
                type="number"
                min="0"
                step="1"
                inputMode="decimal"
                value={text.priceMax}
                onChange={(e) => setText({ ...text, priceMax: e.target.value })}
              />
            </label>
            <label className="field">
              <span>Вылет с (UTC)</span>
              <input
                type="date"
                value={params.get('departureFrom') ?? ''}
                onChange={(e) => update({ departureFrom: e.target.value })}
              />
            </label>
            <label className="field">
              <span>Вылет по (UTC)</span>
              <input
                type="date"
                value={params.get('departureTo') ?? ''}
                onChange={(e) => update({ departureTo: e.target.value })}
              />
            </label>
            <label className="field">
              <span>Доп. багаж</span>
              <select value={params.get('extraBaggage') ?? ''} onChange={(e) => update({ extraBaggage: e.target.value })}>
                <option value="">Не важно</option>
                <option value="true">Есть</option>
                <option value="false">Нет</option>
              </select>
            </label>
          </>
        )}

        <div className="toolbar filter-actions">
          <button className="btn ghost" onClick={() => setMoreFilters(!moreFilters)}>
            {moreFilters ? 'Меньше фильтров' : 'Ещё фильтры'}
          </button>
          {activeFilters > 0 && (
            <button className="btn ghost" onClick={resetFilters}>
              Сбросить фильтры ({activeFilters})
            </button>
          )}
        </div>
      </div>

      <OperationError error={error} />

      {editor && selected.size > 0 && (
        <div className="card toolbar bulk-bar">
          <strong>Выбрано: {selected.size}</strong>
          <select
            value={bulkAction}
            disabled={disabled}
            onChange={(e) => setBulkAction(e.target.value as BulkAction)}
            aria-label="Действие"
          >
            {(Object.keys(BULK_TITLES) as BulkAction[])
              .filter((a) => (archived ? a === 'RESTORE' : a !== 'RESTORE'))
              .map((a) => (
                <option key={a} value={a}>
                  {BULK_TITLES[a]}
                </option>
              ))}
          </select>
          {(bulkAction === 'ADD_TAG' || bulkAction === 'REMOVE_TAG') && (
            <>
              <input
                className="bulk-input"
                list="known-tags"
                maxLength={40}
                placeholder="тег"
                value={bulkTag}
                disabled={disabled}
                onChange={(e) => setBulkTag(e.target.value)}
                aria-label="Тег"
              />
              <datalist id="known-tags">
                {tags.map((t) => (
                  <option key={t.tag} value={t.tag} />
                ))}
              </datalist>
            </>
          )}
          {bulkAction === 'SET_STATUS' && (
            <select
              value={bulkStatus}
              disabled={disabled}
              onChange={(e) => setBulkStatus(e.target.value as BookingStatus)}
              aria-label="Новый статус"
            >
              {STATUSES.filter((s) => s !== 'CREATED' && s !== 'EXPIRED').map((s) => (
                <option key={s} value={s}>
                  {statusNames[s]}
                </option>
              ))}
            </select>
          )}
          <button className="btn primary" disabled={disabled || !bulkReady} onClick={() => void runBulk()}>
            Применить
          </button>
          <span className="spacer" />
          <button className="btn ghost" disabled={disabled} onClick={() => setSelected(new Map())}>
            Снять выделение
          </button>
        </div>
      )}

      {bulkResult && (
        <div className={'card stack bulk-result' + (bulkResult.failures.length ? ' partial' : '')} role="status">
          <div className="toolbar">
            <strong>
              Изменено {bulkResult.succeeded} из {bulkResult.requested}
            </strong>
            <span className="spacer" />
            <button className="btn ghost" onClick={() => setBulkResult(null)}>
              Скрыть
            </button>
          </div>
          {bulkResult.failures.length > 0 && (
            <ul className="failures">
              {bulkResult.failures.map((f) => (
                <li key={f.id}>
                  <strong>{f.bookingCode ?? f.id.slice(0, 8)}</strong>: {f.message}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      <div className="card table-wrap" aria-busy={loading}>
        <table>
          <thead>
            <tr>
              {editor && (
                <th className="check">
                  <input
                    type="checkbox"
                    aria-label="Выбрать все на странице"
                    checked={allOnPage}
                    disabled={disabled || !rows.length}
                    onChange={togglePage}
                  />
                </th>
              )}
              <SortHeader field="code" label="Код" sort={sort} onSort={onSort} />
              <SortHeader field="passenger" label="Пассажир" sort={sort} onSort={onSort} />
              <SortHeader field="flight" label="Рейс" sort={sort} onSort={onSort} />
              <SortHeader field="departure" label="Вылет (UTC)" sort={sort} onSort={onSort} />
              <SortHeader field="seat" label="Место" sort={sort} onSort={onSort} />
              <SortHeader field="price" label="Стоимость" firstDir="desc" sort={sort} onSort={onSort} />
              <SortHeader field="status" label="Статус" sort={sort} onSort={onSort} />
              <th>Срочность</th>
              <th>Теги</th>
              <SortHeader field="createdAt" label="Создана" firstDir="desc" sort={sort} onSort={onSort} />
            </tr>
          </thead>
          <tbody>
            {rows.map((b) => (
              <tr key={b.id} className={b.id === selectedId ? 'selected' : undefined}>
                {editor && (
                  <td className="check">
                    <input
                      type="checkbox"
                      aria-label={'Выбрать ' + b.bookingCode}
                      checked={selected.has(b.id)}
                      disabled={disabled}
                      onChange={() => toggleRow(b)}
                    />
                  </td>
                )}
                <td>
                  <button className="text-button" disabled={disabled} onClick={() => onOpen(b)}>
                    {b.bookingCode}
                  </button>
                </td>
                <td>
                  {b.passenger.lastName} {b.passenger.firstName}
                </td>
                <td>
                  {b.flight.flightNumber}
                  <br />
                  <small className="muted">
                    {b.flight.departureAirport} → {b.flight.arrivalAirport}
                  </small>
                </td>
                <td>{date(b.flight.departureTime)}</td>
                <td>
                  {b.seatNumber}
                  <br />
                  <small className="muted">{fareNames[b.fareClass as FareClass] ?? b.fareClass}</small>
                </td>
                <td className="num">{b.price.toLocaleString('ru-RU')} ₽</td>
                <td>{statusNames[b.status]}</td>
                <td>
                  <span className={'urgency ' + b.urgencyLevel.toLowerCase()}>{urgencyNames[b.urgencyLevel]}</span>
                </td>
                <td>
                  <span className="chips">
                    {b.tags.map((t) => (
                      <button
                        key={t}
                        className="chip tag"
                        title="Показать брони с этим тегом"
                        onClick={() => update({ tag: t })}
                      >
                        {t}
                      </button>
                    ))}
                  </span>
                </td>
                <td>{date(b.createdAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {loading && !data && <p className="empty">Загрузка…</p>}
        {!loading && !rows.length && (
          <p className="empty">
            {activeFilters ? 'Ничего не найдено — измените условия поиска' : archived ? 'Архив пуст' : 'Бронирований нет'}
          </p>
        )}
      </div>

      <Pager
        page={page}
        size={size}
        totalPages={data?.totalPages ?? 0}
        totalElements={data?.totalElements ?? 0}
        disabled={loading || disabled}
        onPage={(p) => update({ page: String(p) }, false)}
        onSize={(s) => update({ size: String(s) })}
      />
    </div>
  )
}

function plural(n: number): string {
  const mod10 = n % 10
  const mod100 = n % 100
  if (mod10 === 1 && mod100 !== 11) return 'бронь'
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return 'брони'
  return 'броней'
}
