import { useEffect, useRef, useState } from 'react'

/** Сортировка в формате backend: «поле,asc» или «поле,desc». */
export interface SortState {
  field: string
  dir: 'asc' | 'desc'
}

export function parseSort(value: string | null, fallback: SortState): SortState {
  if (!value) return fallback
  const [field, dir] = value.split(',')
  return field ? { field, dir: dir === 'desc' ? 'desc' : 'asc' } : fallback
}

export const formatSort = (s: SortState) => `${s.field},${s.dir}`

/** Заголовок колонки: щелчок сортирует по ней, повторный — меняет направление. */
export function SortHeader({
  field,
  label,
  sort,
  onSort,
  firstDir = 'asc',
}: {
  field: string
  label: string
  sort: SortState
  onSort: (next: SortState) => void
  /** Направление при первом щелчке: для дат и цен удобнее начинать с больших значений. */
  firstDir?: 'asc' | 'desc'
}) {
  const active = sort.field === field
  const next: SortState = active ? { field, dir: sort.dir === 'asc' ? 'desc' : 'asc' } : { field, dir: firstDir }
  return (
    <th aria-sort={active ? (sort.dir === 'asc' ? 'ascending' : 'descending') : 'none'}>
      <button type="button" className={'sort-button' + (active ? ' active' : '')} onClick={() => onSort(next)}>
        {label}
        <span aria-hidden="true">{active ? (sort.dir === 'asc' ? ' ▲' : ' ▼') : ' ↕'}</span>
      </button>
    </th>
  )
}

export const PAGE_SIZES = [10, 20, 50, 100]

/** Пагинация: «Назад / Далее», номер страницы и размер страницы. */
export function Pager({
  page,
  totalPages,
  totalElements,
  size,
  disabled,
  onPage,
  onSize,
}: {
  page: number
  totalPages: number
  totalElements: number
  size: number
  disabled?: boolean
  onPage: (page: number) => void
  onSize: (size: number) => void
}) {
  const pages = Math.max(1, totalPages)
  return (
    <div className="toolbar pager">
      <button className="btn ghost" disabled={disabled || page <= 0} onClick={() => onPage(0)} aria-label="Первая страница">
        «
      </button>
      <button className="btn ghost" disabled={disabled || page <= 0} onClick={() => onPage(page - 1)}>
        Назад
      </button>
      <span>
        Страница {Math.min(page + 1, pages)} из {pages}
      </span>
      <button className="btn ghost" disabled={disabled || page + 1 >= totalPages} onClick={() => onPage(page + 1)}>
        Далее
      </button>
      <button
        className="btn ghost"
        disabled={disabled || page + 1 >= totalPages}
        onClick={() => onPage(totalPages - 1)}
        aria-label="Последняя страница"
      >
        »
      </button>
      <span className="spacer" />
      <span className="muted">Всего: {totalElements}</span>
      <label className="inline-field">
        <span className="muted">По</span>
        <select value={size} disabled={disabled} onChange={(e) => onSize(Number(e.target.value))}>
          {PAGE_SIZES.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
      </label>
    </div>
  )
}

/** Черновик текстовых фильтров. Переход по URL отменяет отложенный ввод из предыдущего адреса. */
export function useUrlTextFields<T extends Record<string, string>>(
  values: T,
  onCommit: (changes: Record<string, string>) => void,
  delayMs = 300,
): [T, (fields: T) => void] {
  const source = JSON.stringify(values)
  const [draft, setDraft] = useState({ source, fields: values })
  const commit = useRef(onCommit)
  commit.current = onCommit
  if (draft.source !== source) setDraft({ source, fields: values })

  useEffect(() => {
    if (draft.source !== source) return
    const changes = Object.fromEntries(
      Object.entries(draft.fields)
        .map(([key, value]) => [key, value.trim()])
        .filter(([key, value]) => value !== values[key]),
    )
    if (!Object.keys(changes).length) return
    const timer = setTimeout(() => commit.current(changes), delayMs)
    return () => clearTimeout(timer)
  }, [draft, source, delayMs])

  return [draft.source === source ? draft.fields : values, (fields) => setDraft({ source, fields })]
}

/** Число из строки параметра URL или значение по умолчанию. */
export function intParam(value: string | null, fallback: number, allowed?: number[]): number {
  const n = Number(value)
  if (value === null || !Number.isSafeInteger(n) || n < 0) return fallback
  if (allowed && !allowed.includes(n)) return fallback
  return n
}

export function formatInstant(value?: string | null): string {
  if (!value) return '—'
  return new Date(value).toLocaleString('ru-RU', { dateStyle: 'short', timeStyle: 'short' })
}
