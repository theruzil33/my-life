import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import HomePage from '../../src/pages/HomePage'
import { AuthProvider } from '../../src/auth/AuthProvider'

vi.mock('../../src/api/authApi', () => ({
  authApi: { login: vi.fn(), register: vi.fn(), refresh: vi.fn(), logout: vi.fn() },
}))

import { authApi } from '../../src/api/authApi'

function renderHub(initialEntries = ['/']) {
  return render(
    <MemoryRouter initialEntries={initialEntries}>
      <AuthProvider>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/tracker" element={<p>Страница трекера</p>} />
          <Route path="/finances" element={<p>Страница финансов</p>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.resetAllMocks()
  authApi.refresh.mockResolvedValue({ accessToken: 'jwt-token', email: 'user@example.com' })
  authApi.logout.mockResolvedValue(null)
})

describe('HomePage', () => {
  it('показывает email пользователя и выход', async () => {
    renderHub()

    expect(await screen.findByText(/user@example\.com/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Выйти' })).toBeInTheDocument()
  })

  it('показывает активные карточки трекера и финансов и неактивную заглушку', async () => {
    renderHub()

    expect(await screen.findByRole('link', { name: /Трекер/ })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Финансы/ })).toBeInTheDocument()

    const placeholder = screen.getByText('Заметки').closest('[aria-disabled="true"]')
    expect(placeholder).not.toBeNull()
    expect(screen.queryByRole('link', { name: /Заметки/ })).toBeNull()
  })

  it('открывает трекер по клику на карточку', async () => {
    const user = userEvent.setup()
    renderHub()

    await user.click(await screen.findByRole('link', { name: /Трекер/ }))

    expect(await screen.findByText('Страница трекера')).toBeInTheDocument()
  })

  it('открывает финансы по клику на карточку', async () => {
    const user = userEvent.setup()
    renderHub()

    await user.click(await screen.findByRole('link', { name: /Финансы/ }))

    expect(await screen.findByText('Страница финансов')).toBeInTheDocument()
  })

  it('не выполняет переход по карточке-заглушке', async () => {
    const user = userEvent.setup()
    renderHub()

    await user.click(await screen.findByText('Заметки'))

    expect(screen.queryByText('Страница трекера')).not.toBeInTheDocument()
    expect(screen.getByText('Мои приложения')).toBeInTheDocument()
  })
})
