import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import MediaPage from '../../src/pages/MediaPage'
import { AuthProvider } from '../../src/auth/AuthProvider'
import { RequireAuth } from '../../src/auth/RequireAuth'
import { ApiError } from '../../src/api/client'

vi.mock('../../src/api/authApi', () => ({
  authApi: { login: vi.fn(), register: vi.fn(), refresh: vi.fn(), logout: vi.fn() },
}))

vi.mock('../../src/api/mediaApi', () => ({
  mediaApi: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    remove: vi.fn(),
  },
}))

import { authApi } from '../../src/api/authApi'
import { mediaApi } from '../../src/api/mediaApi'

const ITEMS = {
  FILM: [{ id: 1, type: 'FILM', title: 'Дюна', description: null, status: 'IN_PROGRESS' }],
  BOOK: [{ id: 2, type: 'BOOK', title: 'Солярис', description: null, status: 'NOT_STARTED' }],
  GAME: [],
}

function renderMedia(initialEntries = ['/media']) {
  return render(
    <MemoryRouter initialEntries={initialEntries}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<p>Форма входа</p>} />
          <Route path="/" element={<p>Главная страница</p>} />
          <Route
            path="/media"
            element={
              <RequireAuth>
                <MediaPage />
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
  authApi.refresh.mockResolvedValue({ accessToken: 'jwt-token', email: 'user@example.com' })
  mediaApi.list.mockImplementation((token, type) => Promise.resolve(ITEMS[type] ?? []))
})

describe('MediaPage', () => {
  it('открывает по умолчанию подвкладку «Фильмы и сериалы» и показывает вкладки по порядку', async () => {
    renderMedia()

    expect(await screen.findByRole('heading', { name: 'Медиатека' })).toBeInTheDocument()
    expect(screen.getAllByRole('tab').map((tab) => tab.textContent)).toEqual([
      'Фильмы и сериалы',
      'Книги',
      'Игры',
    ])
    expect(screen.getByRole('tab', { name: 'Фильмы и сериалы' })).toHaveAttribute(
      'aria-selected',
      'true',
    )
    expect(await screen.findByText('Дюна')).toBeInTheDocument()
    expect(mediaApi.list).toHaveBeenCalledWith('jwt-token', 'FILM')
  })

  it('загружает записи выбранной подвкладки', async () => {
    const user = userEvent.setup()
    renderMedia()
    await screen.findByText('Дюна')

    await user.click(screen.getByRole('tab', { name: 'Книги' }))

    expect(await screen.findByText('Солярис')).toBeInTheDocument()
    expect(mediaApi.list).toHaveBeenCalledWith('jwt-token', 'BOOK')
  })

  it('показывает пустое состояние для подвкладки без записей', async () => {
    const user = userEvent.setup()
    renderMedia()
    await screen.findByText('Дюна')

    await user.click(screen.getByRole('tab', { name: 'Игры' }))

    expect(await screen.findByText('Здесь пока нет игр.')).toBeInTheDocument()
  })

  it('сохраняет данные подвкладки при переключении и не перезапрашивает их', async () => {
    const user = userEvent.setup()
    renderMedia()
    await screen.findByText('Дюна')

    await user.click(screen.getByRole('tab', { name: 'Книги' }))
    await screen.findByText('Солярис')
    await user.click(screen.getByRole('tab', { name: 'Фильмы и сериалы' }))

    expect(await screen.findByText('Дюна')).toBeVisible()
    const filmCalls = mediaApi.list.mock.calls.filter((call) => call[1] === 'FILM')
    expect(filmCalls).toHaveLength(1)
  })

  it('возвращает на главную страницу по ссылке', async () => {
    const user = userEvent.setup()
    renderMedia()
    await screen.findByText('Дюна')

    await user.click(screen.getByRole('link', { name: /На главную/ }))

    expect(await screen.findByText('Главная страница')).toBeInTheDocument()
  })

  it('перенаправляет неаутентифицированного пользователя на вход', async () => {
    authApi.refresh.mockRejectedValue(new ApiError('no session', 401))

    renderMedia()

    expect(await screen.findByText('Форма входа')).toBeInTheDocument()
    expect(mediaApi.list).not.toHaveBeenCalled()
  })

  it('показывает ошибку загрузки внутри подвкладки', async () => {
    mediaApi.list.mockRejectedValue(new ApiError('Сервис недоступен', 500))

    renderMedia()

    expect(await screen.findByRole('alert')).toHaveTextContent('Сервис недоступен')
    expect(within(screen.getByRole('tabpanel')).getByRole('button', { name: 'Повторить' })).toBeInTheDocument()
  })
})
