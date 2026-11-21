// @vitest-environment jsdom
import { act } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { MemoryRouter, useNavigate, type NavigateFunction } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { adminUsersApi, type AdminUser } from '../api/adminUsers'
import { ApiError } from '../api/client'
import { AdminUsersPage } from './AdminUsersPage'

vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ user: { id: 'me' } }) }))
vi.mock('../api/adminUsers', async (original) => ({
  ...await original<typeof import('../api/adminUsers')>(),
  adminUsersApi: { list: vi.fn(), get: vi.fn(), update: vi.fn(), create: vi.fn() },
}))

const user: AdminUser = {
  id: 'other', login: 'viewer', displayName: 'Наблюдатель', roles: ['MODERATOR'], enabled: true,
  mustChangePassword: false, locked: false, failedAttempts: 0, version: 1,
  createdAt: '2026-11-21T10:00:00Z', updatedAt: '2026-11-21T10:00:00Z',
}
let container: HTMLDivElement
let root: Root
let navigate: NavigateFunction

function Harness() {
  navigate = useNavigate()
  return <AdminUsersPage />
}

async function mount() {
  await act(async () => root.render(<MemoryRouter><Harness /></MemoryRouter>))
}
async function click(title: string) {
  const button = Array.from(container.querySelectorAll('button')).find((b) => b.textContent === title)
  if (!button) throw new Error(`Нет кнопки ${title}`)
  await act(async () => button.click())
}
async function submit() {
  await act(async () => container.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })))
}

beforeEach(() => {
  Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true })
  vi.mocked(adminUsersApi.list).mockResolvedValue({ content: [user], number: 0, size: 20, totalPages: 1, totalElements: 1 })
  container = document.createElement('div')
  document.body.append(container)
  root = createRoot(container)
})
afterEach(async () => {
  await act(async () => root.unmount())
  container.remove()
  vi.resetAllMocks()
})

it('убирает ошибку после успешного повторного поиска', async () => {
  vi.mocked(adminUsersApi.list).mockRejectedValueOnce(new Error('Соединение потеряно'))
  await mount()
  expect(container.querySelector('[role="alert"]')?.textContent).toContain('Соединение потеряно')
  await act(async () => { navigate('/?q=viewer') })
  expect(container.querySelector('[role="alert"]')).toBeNull()
  expect(container.querySelector('tbody')?.textContent).toContain('viewer')
})

it('после конфликта перечитывает пользователя и сохраняет с актуальной версией', async () => {
  await mount()
  await click('Изменить')
  vi.mocked(adminUsersApi.update).mockRejectedValueOnce(new ApiError({
    status: 409, code: 'VERSION_CONFLICT', message: 'Пользователь изменён',
  }))
  await submit()
  expect(container.querySelector('[role="alert"]')?.textContent).toContain('Пользователь изменён')
  const latest = { ...user, displayName: 'Новое имя', version: 42 }
  vi.mocked(adminUsersApi.get).mockResolvedValue(latest)
  await click('Перечитать данные')
  expect(container.querySelector<HTMLInputElement>('form input')!.value).toBe('Новое имя')
  vi.mocked(adminUsersApi.update).mockResolvedValue(latest)
  await submit()
  expect(vi.mocked(adminUsersApi.update).mock.lastCall![0].version).toBe(42)
  expect(container.querySelector('form')).toBeNull()
})

it('показывает выданный временный пароль и позволяет скрыть его', async () => {
  await mount()
  await click('Новый пользователь')
  const inputs = Array.from(container.querySelectorAll<HTMLInputElement>('form input'))
  await act(async () => {
    for (const [i, value] of [' new-viewer ', ' Новый пользователь '].entries()) {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(inputs[i], value)
      inputs[i].dispatchEvent(new Event('input', { bubbles: true }))
    }
  })
  vi.mocked(adminUsersApi.create).mockResolvedValue({ user, temporaryPassword: 'Temporary1!Example' })
  await submit()
  expect(adminUsersApi.create).toHaveBeenCalledWith('new-viewer', 'Новый пользователь', 'MODERATOR')
  expect(container.querySelector('.secret-value')?.textContent).toBe('Temporary1!Example')
  expect(container.querySelector('form')).toBeNull()
  await click('Скрыть')
  expect(container.querySelector('.secret-value')).toBeNull()
})
