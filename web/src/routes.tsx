import type { ReactNode } from 'react'
import type { Role } from './api/types'
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
  { path: '/', label: 'Главная', role: 'VIEWER', element: <HomePage /> },
  { path: '/bookings', label: 'Брони', role: 'VIEWER', element: <Placeholder title="Брони" owner="список, форма, заметки, теги, массовые операции, CSV" /> },
  { path: '/flights', label: 'Рейсы', role: 'VIEWER', element: <Placeholder title="Рейсы" owner="справочник рейсов" /> },
  { path: '/passengers', label: 'Пассажиры', role: 'VIEWER', element: <Placeholder title="Пассажиры" owner="справочник пассажиров" /> },
  { path: '/analytics', label: 'Аналитика', role: 'VIEWER', element: <Placeholder title="Аналитика" owner="сводка и группировки" /> },
  { path: '/admin/users', label: 'Пользователи', role: 'ADMIN', element: <Placeholder title="Пользователи" owner="администрирование учётных записей" /> },
  { path: '/admin/audit', label: 'Аудит', role: 'ADMIN', element: <Placeholder title="Журнал аудита" owner="поиск и просмотр событий" /> },
]
