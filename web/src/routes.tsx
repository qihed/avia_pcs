import type { ReactNode } from 'react'
import type { Role } from './api/types'
import { BookingsPage } from './pages/BookingsPage'
import { ReferencesPage } from './pages/ReferencesPage'
import { AdminUsersPage } from './pages/AdminUsersPage'
import { HomePage } from './pages/HomePage'
import { Placeholder } from './pages/Placeholder'

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
  { path: '/', label: 'Главная', role: 'MODERATOR', element: <HomePage /> },
  { path: '/bookings', label: 'Брони', role: 'MODERATOR', element: <BookingsPage /> },
  { path: '/flights', label: 'Рейсы', role: 'MODERATOR', element: <ReferencesPage key="flights" kind="flights" /> },
  { path: '/passengers', label: 'Пассажиры', role: 'MODERATOR', element: <ReferencesPage key="passengers" kind="passengers" /> },
  { path: '/analytics', label: 'Аналитика', role: 'MODERATOR', element: <Placeholder title="Аналитика" owner="сводка и группировки" /> },
  { path: '/admin/users', label: 'Пользователи', role: 'ADMIN', element: <AdminUsersPage /> },
  { path: '/admin/audit', label: 'Аудит', role: 'ADMIN', element: <Placeholder title="Журнал аудита" owner="поиск и просмотр событий" /> },
]
