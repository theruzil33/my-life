import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { AuthProvider } from '../../src/auth/AuthProvider'
import { RequireAuth } from '../../src/auth/RequireAuth'
import { writeStoredSession } from '../../src/auth/sessionStorage'

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

describe('RequireAuth', () => {
  it('перенаправляет на вход без сохранённого токена', () => {
    renderApp()

    expect(screen.getByText('Форма входа')).toBeInTheDocument()
    expect(screen.queryByText('Защищённая страница')).not.toBeInTheDocument()
  })

  it('показывает защищённую страницу при наличии токена', () => {
    writeStoredSession({ accessToken: 'jwt-token', email: 'user@example.com' })

    renderApp()

    expect(screen.getByText('Защищённая страница')).toBeInTheDocument()
    expect(screen.queryByText('Форма входа')).not.toBeInTheDocument()
  })
})
