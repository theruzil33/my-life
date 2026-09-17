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

  it('показывает карточку трекера и неактивные карточки-заглушки', async () => {
    renderHub()

    expect(await screen.findByRole('link', { name: /Трекер/ })).toBeInTheDocument()

    const placeholder = screen.getByText('Финансы').closest('[aria-disabled="true"]')
    expect(placeholder).not.toBeNull()
    expect(screen.queryByRole('link', { name: /Финансы/ })).toBeNull()
  })

  it('открывает трекер по клику на карточку', async () => {
    const user = userEvent.setup()
    renderHub()

    await user.click(await screen.findByRole('link', { name: /Трекер/ }))

    expect(await screen.findByText('Страница трекера')).toBeInTheDocument()
  })

  it('не выполняет переход по карточке-заглушке', async () => {
    const user = userEvent.setup()
    renderHub()

    await user.click(await screen.findByText('Финансы'))

    expect(screen.queryByText('Страница трекера')).not.toBeInTheDocument()
    expect(screen.getByText('Мои приложения')).toBeInTheDocument()
  })
})
