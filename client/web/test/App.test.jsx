import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import App from '../src/App'
import { AuthProvider } from '../src/auth/AuthProvider'
import { AUTH_STORAGE_KEY, writeStoredSession } from '../src/auth/sessionStorage'

function renderApp(initialEntries) {
  return render(
    <MemoryRouter initialEntries={initialEntries}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('App', () => {
  it('показывает форму входа для неаутентифицированного пользователя', () => {
    renderApp(['/login'])

    expect(screen.getByRole('heading', { name: 'Вход' })).toBeInTheDocument()
  })

  it('перенаправляет неаутентифицированного пользователя с защищённой страницы на вход', () => {
    renderApp(['/'])

    expect(screen.getByRole('heading', { name: 'Вход' })).toBeInTheDocument()
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
    writeStoredSession({ accessToken: 'jwt-token', email: 'user@example.com' })
    const user = userEvent.setup()
    renderApp(['/'])

    expect(screen.getByText(/user@example\.com/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Выйти' }))

    expect(screen.getByRole('heading', { name: 'Вход' })).toBeInTheDocument()
    expect(window.localStorage.getItem(AUTH_STORAGE_KEY)).toBeNull()
  })
})
