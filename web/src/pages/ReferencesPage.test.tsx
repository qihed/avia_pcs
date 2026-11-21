// @vitest-environment jsdom
import { act } from 'react'
import { createRoot } from 'react-dom/client'
import { afterEach, expect, it, vi } from 'vitest'
import { api } from '../api/client'
import type { Role } from '../api/types'
import { ReferencesPage } from './ReferencesPage'

const session = vi.hoisted(() => ({ role: 'MODERATOR' as Role }))
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ user: { roles: [session.role] } }) }))
vi.mock('../api/client', async (original) => ({ ...await original<typeof import('../api/client')>(), api: vi.fn() }))

afterEach(() => vi.resetAllMocks())

it.each(['flights', 'passengers'] as const)('модератор редактирует %s без кнопки удаления, администратор может удалять', async (kind) => {
  Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true })
  vi.mocked(api).mockResolvedValue([{
    id: 'record', flightNumber: 'SU100', departureTime: '2026-12-01T10:00:00', departureAirport: 'SVO', arrivalAirport: 'LED',
    lastName: 'Иванов', firstName: 'Иван', birthDate: '1990-01-01', email: 'test@example.org',
  }])
  const container = document.createElement('div')
  document.body.append(container)
  const root = createRoot(container)
  try {
    session.role = 'MODERATOR'
    await act(async () => root.render(<ReferencesPage kind={kind} />))
    const titles = () => Array.from(container.querySelectorAll('button')).map((b) => b.textContent?.trim())
    expect(titles()).toContain('Добавить')
    expect(titles()).toContain('Изменить')
    expect(titles()).not.toContain('Удалить')
    session.role = 'ADMIN'
    await act(async () => root.render(<ReferencesPage kind={kind} />))
    expect(titles()).toContain('Удалить')
  } finally {
    await act(async () => root.unmount())
    container.remove()
  }
})
