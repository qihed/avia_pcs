import { api } from './client'
import type { Booking, BookingStatus, Page } from './booking'

export type FareClass = 'ECONOMY' | 'COMFORT' | 'BUSINESS'
export type Urgency = 'NONE' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export const STATUSES: BookingStatus[] = ['CREATED', 'PAID', 'CHECKED_IN', 'COMPLETED', 'CANCELLED', 'EXPIRED']
export const FARE_CLASSES: FareClass[] = ['ECONOMY', 'COMFORT', 'BUSINESS']
export const URGENCIES: Urgency[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'NONE']

export const fareNames: Record<FareClass, string> = {
  ECONOMY: 'Эконом',
  COMFORT: 'Комфорт',
  BUSINESS: 'Бизнес',
}

/**
 * Параметры поиска, которые backend принимает в GET /api/items. Они же — параметры адреса страницы
 * /bookings, поэтому фильтр сохраняется при перезагрузке и им можно поделиться ссылкой.
 */
export const FILTER_KEYS = [
  'q',
  'status',
  'fareClass',
  'urgency',
  'tag',
  'flightId',
  'priceMin',
  'priceMax',
  'departureFrom',
  'departureTo',
  'extraBaggage',
] as const

export interface TagUsage {
  tag: string
  count: number
}

export type BulkAction = 'ADD_TAG' | 'REMOVE_TAG' | 'SET_STATUS' | 'ARCHIVE' | 'RESTORE'

export interface BulkResult {
  requested: number
  succeeded: number
  failures: { id: string; bookingCode?: string; message: string }[]
}

export const bookingSearchApi = {
  list: (params: URLSearchParams) => api<Page<Booking>>('/api/items?' + params.toString()),

  tags: () => api<TagUsage[]>('/api/items/tags'),

  setTags: (booking: Booking, tags: string[]) =>
    api<Booking>(`/api/items/${booking.id}/tags`, { method: 'PUT', body: { tags, version: booking.version } }),

  bulk: (ids: string[], action: BulkAction, extra: { status?: BookingStatus; tag?: string } = {}) =>
    api<BulkResult>('/api/items/bulk', { method: 'POST', body: { ids, action, ...extra } }),
}
