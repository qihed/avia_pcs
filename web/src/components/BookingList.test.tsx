// @vitest-environment jsdom
import { act } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { MemoryRouter, useNavigate, useLocation, type NavigateFunction } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { bookingSearchApi } from '../api/bookingSearch'
import type { Booking, Page } from '../api/booking'
import { BookingList } from './BookingList'

vi.mock('../api/bookingSearch', async (original) => ({
  ...await original<typeof import('../api/bookingSearch')>(),
  bookingSearchApi: { list: vi.fn(), tags: vi.fn(), bulk: vi.fn() },
}))

const booking = (n = 1): Booking => ({
  id: `id-${n}`, bookingCode: `CODE${n}`, seatNumber: '1A', fareClass: 'ECONOMY', price: 1000,
  status: 'PAID', urgencyLevel: 'LOW', tags: ['vip'], version: 0, extraBaggage: false,
  archived: false, createdAt: '2026-11-21T10:00:00', updatedAt: '2026-11-21T10:00:00',
  passenger: { id: 'passenger', lastName: 'Иванов', firstName: 'Иван' } as Booking['passenger'],
  flight: { id: 'flight', flightNumber: 'SU100', departureTime: '2026-12-01T10:00:00' } as Booking['flight'],
})
const page = (rows: Booking[] = [booking()], number = 0, totalElements = rows.length): Page<Booking> => ({
  content: rows, number, size: 20, totalElements, totalPages: Math.ceil(totalElements / 20),
})

let container: HTMLDivElement
let root: Root
let navigate: NavigateFunction
let currentSearch: string

function Harness() {
  navigate = useNavigate()
  currentSearch = useLocation().search
  return <BookingList busy={false} reloadKey={0} editor onOpen={vi.fn()} onChanged={vi.fn()} />
}

async function mount(url = '/bookings') {
  await act(async () => root.render(<MemoryRouter initialEntries={[url]}><Harness /></MemoryRouter>))
}

async function click(element: Element | null) {
  if (!element) throw new Error('Элемент не найден')
  await act(async () => (element as HTMLElement).click())
}

async function typeSearch(value: string) {
  const input = container.querySelector<HTMLInputElement>('input[type="search"]')!
  await act(async () => {
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(input, value)
    input.dispatchEvent(new Event('input', { bubbles: true }))
  })
}

beforeEach(() => {
  Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true })
  vi.useFakeTimers()
  vi.mocked(bookingSearchApi.list).mockResolvedValue(page())
  vi.mocked(bookingSearchApi.tags).mockResolvedValue([{ tag: 'vip', count: 1 }])
  container = document.createElement('div')
  document.body.append(container)
  root = createRoot(container)
})

afterEach(async () => {
  await act(async () => root.unmount())
  container.remove()
  vi.useRealTimers()
  vi.resetAllMocks()
})

describe('Поиск и фильтры броней', () => {
  it('применяет текст с задержкой, сохраняя остальные фильтры и сбрасывая страницу', async () => {
    await mount('/bookings?status=PAID&page=1')
    await typeSearch(' Иванов ')
    expect(currentSearch).not.toContain('q=')
    await act(async () => vi.advanceTimersByTime(400))
    const query = new URLSearchParams(currentSearch)
    expect(query.get('q')).toBe('Иванов')
    expect(query.get('status')).toBe('PAID')
    expect(query.has('page')).toBe(false)
    expect(vi.mocked(bookingSearchApi.list).mock.lastCall![0].get('q')).toBe('Иванов')
  })

  it('синхронизирует поля с новой ссылкой и отменяет старый отложенный поиск', async () => {
    await mount('/bookings?q=Иванов')
    await typeSearch('Петров')
    await act(async () => { await navigate('/bookings?q=Сидоров&priceMin=5000') })
    expect(container.querySelector<HTMLInputElement>('input[type="search"]')!.value).toBe('Сидоров')
    expect(Array.from(container.querySelectorAll<HTMLInputElement>('input[type="number"]')).map((i) => i.value))
      .toContain('5000')
    await act(async () => vi.advanceTimersByTime(800))
    expect(new URLSearchParams(currentSearch).get('q')).toBe('Сидоров')
  })

  it('передаёт повторные статусы и теги из ссылки в API', async () => {
    await mount('/bookings?status=PAID&status=CREATED&tag=vip&tag=корпоратив')
    const query = vi.mocked(bookingSearchApi.list).mock.lastCall![0]
    expect(query.getAll('status')).toEqual(['PAID', 'CREATED'])
    expect(query.getAll('tag')).toEqual(['vip', 'корпоратив'])
    expect(container.querySelectorAll('.status-filter input:checked')).toHaveLength(2)
  })

  it('блокирует строки предыдущего результата при загрузке и убирает их при ошибке', async () => {
    await mount()
    vi.mocked(bookingSearchApi.list).mockReturnValueOnce(new Promise(() => {}))
    await act(async () => { await navigate('/bookings?q=ожидание') })
    expect(container.querySelector<HTMLButtonElement>('tbody .text-button')!.disabled).toBe(true)
    expect(container.querySelector<HTMLInputElement>('tbody input[type="checkbox"]')!.disabled).toBe(true)
    vi.mocked(bookingSearchApi.list).mockRejectedValueOnce(new Error('Нет соединения'))
    await act(async () => { await navigate('/bookings?q=ошибка') })
    expect(container.querySelectorAll('tbody tr')).toHaveLength(0)
  })

  it('сбрасывает выделение при переходе на другие фильтры', async () => {
    await mount()
    await click(container.querySelector('tbody input[type="checkbox"]'))
    expect(container.textContent).toContain('Выбрано: 1')
    await act(async () => { await navigate('/bookings?archived=true') })
    expect(container.textContent).not.toContain('Выбрано: 1')
  })

  it('ограничивает массовое выделение сотней броней на нескольких страницах', async () => {
    vi.mocked(bookingSearchApi.list).mockImplementation(async (query) => {
      const n = Number(query.get('page') ?? 0)
      return { ...page(Array.from({ length: 100 }, (_, i) => booking(n * 100 + i)), n, 200), size: 100, totalPages: 2 }
    })
    await mount('/bookings?size=100')
    await click(container.querySelector('tbody input[type="checkbox"]'))
    expect(container.textContent).toContain('Выбрано: 1')
    await act(async () => { await navigate('/bookings?size=100&page=1') })
    await click(container.querySelector('thead input[type="checkbox"]'))
    expect(container.textContent).toContain('Выбрано: 100')
    expect(container.querySelectorAll('tbody input:checked')).toHaveLength(99)
  })
})
