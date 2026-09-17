import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { AuthProvider } from '../../src/auth/AuthProvider'
import { RequireAuth } from '../../src/auth/RequireAuth'
import { ApiError } from '../../src/api/client'

vi.mock('../../src/api/authApi', () => ({
  authApi: { login: vi.fn(), register: vi.fn(), refresh: vi.fn(), logout: vi.fn() },
}))

import { authApi } from '../../src/api/authApi'

function renderApp() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<p>Форма входа</p>} />
          <Route
            path="/"
            element={
              <RequireAuth>
                <p>Защищённая страница</p>
              </RequireAuth>
            }
          />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.resetAllMocks()
  authApi.refresh.mockRejectedValue(new ApiError('no session', 401))
})

describe('RequireAuth', () => {
  it('перенаправляет на вход без действующей сессии', async () => {
    renderApp()

    expect(await screen.findByText('Форма входа')).toBeInTheDocument()
    expect(screen.queryByText('Защищённая страница')).not.toBeInTheDocument()
  })

  it('показывает защищённую страницу при восстановленной сессии', async () => {
    authApi.refresh.mockResolvedValue({ accessToken: 'restored-token', email: 'user@example.com' })

    renderApp()

    expect(await screen.findByText('Защищённая страница')).toBeInTheDocument()
    expect(screen.queryByText('Форма входа')).not.toBeInTheDocument()
  })
})
