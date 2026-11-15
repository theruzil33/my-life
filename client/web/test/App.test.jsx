import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import App from '../src/App'
import { AuthProvider } from '../src/auth/AuthProvider'
import { ApiError } from '../src/api/client'

vi.mock('../src/api/authApi', () => ({
  authApi: { login: vi.fn(), register: vi.fn(), refresh: vi.fn(), logout: vi.fn() },
}))

vi.mock('../src/api/mediaApi', () => ({
  mediaApi: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    remove: vi.fn(),
  },
}))

vi.mock('../src/api/financeApi', () => ({
  financeApi: {
    getBalance: vi.fn(),
    updateBalance: vi.fn(),
    listCategories: vi.fn(),
    createCategory: vi.fn(),
    updateCategory: vi.fn(),
    removeCategory: vi.fn(),
    listOperationsByDate: vi.fn(),
    listOperationsByRange: vi.fn(),
    saveOperation: vi.fn(),
    removeOperation: vi.fn(),
  },
}))

import { authApi } from '../src/api/authApi'
import { financeApi } from '../src/api/financeApi'
import { mediaApi } from '../src/api/mediaApi'

function renderApp(initialEntries) {
  return render(
    <MemoryRouter initialEntries={initialEntries}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.resetAllMocks()
  authApi.refresh.mockRejectedValue(new ApiError('no session', 401))
  authApi.logout.mockResolvedValue(null)
  financeApi.getBalance.mockResolvedValue({ startingBalance: 0, amount: 0 })
  financeApi.listCategories.mockResolvedValue([])
  financeApi.listOperationsByDate.mockResolvedValue([])
  mediaApi.list.mockResolvedValue([])
})

describe('App', () => {
  it('показывает форму входа для неаутентифицированного пользователя', () => {
    renderApp(['/login'])

    expect(screen.getByRole('heading', { name: 'Вход' })).toBeInTheDocument()
  })

  it('перенаправляет неаутентифицированного пользователя с защищённой страницы на вход', async () => {
    renderApp(['/'])

    expect(await screen.findByRole('heading', { name: 'Вход' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Выйти' })).not.toBeInTheDocument()
  })

  it('переключает пользователя между входом и регистрацией', async () => {
    const user = userEvent.setup()
    renderApp(['/login'])

    await user.click(screen.getByRole('link', { name: 'Зарегистрироваться' }))
    expect(screen.getByRole('heading', { name: 'Регистрация' })).toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: 'Войти' }))
    expect(screen.getByRole('heading', { name: 'Вход' })).toBeInTheDocument()
  })

  it('показывает email пользователя и выходит из системы по кнопке', async () => {
    authApi.refresh.mockResolvedValue({ accessToken: 'jwt-token', email: 'user@example.com' })
    const user = userEvent.setup()
    renderApp(['/'])

    expect(await screen.findByText(/user@example\.com/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Выйти' }))

    expect(await screen.findByRole('heading', { name: 'Вход' })).toBeInTheDocument()
    expect(authApi.logout).toHaveBeenCalledTimes(1)
    expect(window.localStorage.getItem('my-life.auth')).toBeNull()
  })

  it('открывает страницу финансов для аутентифицированного пользователя', async () => {
    authApi.refresh.mockResolvedValue({ accessToken: 'jwt-token', email: 'user@example.com' })

    renderApp(['/finances'])

    expect(await screen.findByRole('heading', { name: 'Финансы' })).toBeInTheDocument()
  })

  it('перенаправляет неаутентифицированного пользователя с финансов на вход', async () => {
    renderApp(['/finances'])

    expect(await screen.findByRole('heading', { name: 'Вход' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Финансы' })).not.toBeInTheDocument()
  })

  it('открывает страницу медиатеки для аутентифицированного пользователя', async () => {
    authApi.refresh.mockResolvedValue({ accessToken: 'jwt-token', email: 'user@example.com' })

    renderApp(['/media'])

    expect(await screen.findByRole('heading', { name: 'Медиатека' })).toBeInTheDocument()
  })

  it('перенаправляет неаутентифицированного пользователя с медиатеки на вход', async () => {
    renderApp(['/media'])

    expect(await screen.findByRole('heading', { name: 'Вход' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Медиатека' })).not.toBeInTheDocument()
  })
})
