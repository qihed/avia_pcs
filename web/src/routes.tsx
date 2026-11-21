import type { ReactNode } from 'react'
import type { Role } from './api/types'
import { BookingsPage } from './pages/BookingsPage'
import { ReferencesPage } from './pages/ReferencesPage'
import { HomePage } from './pages/HomePage'
import { Placeholder } from './pages/Placeholder'
import { BookingBulkActionsPanel } from './components/BookingBulkActionsPanel'
import { AnalyticsPage } from './pages/AnalyticsPage'
import { AuditPage } from './pages/AuditPage'

export interface AppRoute {
  path: string
  label: string
  /** Минимальная роль; пункт меню скрывается, а страница закрывается для остальных */
  role: Role
  element: ReactNode
}

/**
 * Единая таблица страниц. Чтобы подключить свою страницу, замените <Placeholder> на компонент:
 * маршрут, меню и проверка роли подхватятся автоматически.
 */
export const appRoutes: AppRoute[] = [
  { path: '/', label: 'Главная', role: 'VIEWER', element: <HomePage /> },
  { path: '/bookings', label: 'Брони', role: 'VIEWER', element: <BookingsPage /> },
  { path: '/flights', label: 'Рейсы', role: 'VIEWER', element: <ReferencesPage key="flights" kind="flights" /> },
  { path: '/passengers', label: 'Пассажиры', role: 'VIEWER', element: <ReferencesPage key="passengers" kind="passengers" /> },
  { path: '/analytics', label: 'Аналитика', role: 'VIEWER', element: <AnalyticsPage /> },
  { path: '/admin/users', label: 'Пользователи', role: 'ADMIN', element: <Placeholder title="Пользователи" owner="администрирование учётных записей" /> },
  { path: '/admin/audit', label: 'Аудит', role: 'ADMIN', element: <AuditPage /> },
]
