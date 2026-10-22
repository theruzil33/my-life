import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import TrackerPage from '../../src/pages/TrackerPage'
import { AuthProvider } from '../../src/auth/AuthProvider'
import { RequireAuth } from '../../src/auth/RequireAuth'
import { ApiError } from '../../src/api/client'

vi.mock('../../src/api/authApi', () => ({
  authApi: { login: vi.fn(), register: vi.fn(), refresh: vi.fn(), logout: vi.fn() },
}))

vi.mock('../../src/api/trackerApi', () => ({
  trackerApi: {
    listOnDate: vi.fn(),
    history: vi.fn(),
    create: vi.fn(),
    markCompleted: vi.fn(),
    unmarkCompleted: vi.fn(),
    importFile: vi.fn(),
  },
}))

import { authApi } from '../../src/api/authApi'
import { trackerApi } from '../../src/api/trackerApi'

function renderTracker() {
  return render(
    <MemoryRouter initialEntries={['/tracker']}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<p>Форма входа</p>} />
          <Route
            path="/tracker"
            element={
              <RequireAuth>
                <TrackerPage />
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
  trackerApi.listOnDate.mockResolvedValue([])
  trackerApi.history.mockResolvedValue([])
  trackerApi.importFile.mockResolvedValue({ createdActivities: 1, savedCompletions: 2 })
})

describe('TrackerPage', () => {
  it('по умолчанию открывает вкладку привычек', async () => {
    renderTracker()

    expect(await screen.findByRole('heading', { name: 'Трекер привычек' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Привычки' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByLabelText('Дата')).toBeInTheDocument()
    expect(screen.queryByLabelText('С')).not.toBeInTheDocument()
  })

  it('переключается на вкладку статистики', async () => {
    const user = userEvent.setup()
    renderTracker()

    await screen.findByRole('heading', { name: 'Трекер привычек' })
    await user.click(screen.getByRole('tab', { name: 'Статистика' }))

    expect(screen.getByRole('tab', { name: 'Статистика' })).toHaveAttribute(
      'aria-selected',
      'true',
    )
    expect(await screen.findByLabelText('С')).toBeVisible()
    expect(screen.getByLabelText('Дата')).not.toBeVisible()
  })

  it('переключается на вкладку задач и грузит только задачи', async () => {
    const user = userEvent.setup()
    renderTracker()

    await screen.findByRole('heading', { name: 'Трекер привычек' })
    await user.click(screen.getByRole('tab', { name: 'Задачи' }))

    expect(screen.getByRole('tab', { name: 'Задачи' })).toHaveAttribute('aria-selected', 'true')
    await waitFor(() =>
      expect(trackerApi.listOnDate).toHaveBeenCalledWith('jwt-token', expect.any(String), 'TASK'),
    )
    expect(await screen.findByText('На выбранную дату задач нет.')).toBeInTheDocument()
  })

  it('сохраняет выбранную дату вкладки привычек после открытия задач', async () => {
    const user = userEvent.setup()
    renderTracker()

    await screen.findByRole('heading', { name: 'Трекер привычек' })
    fireEvent.change(document.getElementById('habits-date'), { target: { value: '2026-09-10' } })
    expect(document.getElementById('habits-date').value).toBe('2026-09-10')

    await user.click(screen.getByRole('tab', { name: 'Задачи' }))
    await user.click(screen.getByRole('tab', { name: 'Привычки' }))

    expect(document.getElementById('habits-date').value).toBe('2026-09-10')
  })

  it('сохраняет выбранную дату при переключении вкладок', async () => {
    const user = userEvent.setup()
    renderTracker()

    const dateInput = await screen.findByLabelText('Дата')
    fireEvent.change(dateInput, { target: { value: '2026-09-10' } })
    expect(dateInput.value).toBe('2026-09-10')

    await user.click(screen.getByRole('tab', { name: 'Статистика' }))
    await user.click(screen.getByRole('tab', { name: 'Привычки' }))

    expect(screen.getByLabelText('Дата').value).toBe('2026-09-10')
  })

  it('перенаправляет неаутентифицированного пользователя на вход', async () => {
    authApi.refresh.mockRejectedValue(new ApiError('no session', 401))
    renderTracker()

    expect(await screen.findByText('Форма входа')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Трекер привычек' })).not.toBeInTheDocument()
  })

  it('показывает кнопку импорта и открывает модальное окно', async () => {
    const user = userEvent.setup()
    renderTracker()

    await screen.findByRole('heading', { name: 'Трекер привычек' })
    await user.click(screen.getByRole('button', { name: 'Импорт' }))

    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByLabelText('Месяц и год')).toHaveAttribute('type', 'month')
    expect(within(dialog).getByLabelText('Файл CSV')).toHaveAttribute('type', 'file')
  })

  it('импортирует файл, показывает сводку и перечитывает данные', async () => {
    const user = userEvent.setup()
    renderTracker()

    await screen.findByRole('heading', { name: 'Трекер привычек' })
    await waitFor(() => expect(trackerApi.listOnDate).toHaveBeenCalledTimes(1))

    await user.click(screen.getByRole('button', { name: 'Импорт' }))
    const dialog = screen.getByRole('dialog')
    await user.upload(
      within(dialog).getByLabelText('Файл CSV'),
      new File(['csv'], 'Продуктивность.csv', { type: 'text/csv' }),
    )
    await user.click(within(dialog).getByRole('button', { name: 'Импортировать' }))

    expect(trackerApi.importFile).toHaveBeenCalledWith('jwt-token', {
      year: expect.any(Number),
      month: expect.any(Number),
      file: expect.any(File),
    })
    expect(
      await within(dialog).findByText('Создано активностей: 1. Сохранено отметок: 2.'),
    ).toBeInTheDocument()
    await waitFor(() => expect(trackerApi.listOnDate).toHaveBeenCalledTimes(2))
  })

  it('не отправляет импорт без выбранного файла', async () => {
    const user = userEvent.setup()
    renderTracker()

    await screen.findByRole('heading', { name: 'Трекер привычек' })
    await user.click(screen.getByRole('button', { name: 'Импорт' }))
    const dialog = screen.getByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Импортировать' }))

    expect(within(dialog).getByRole('alert')).toHaveTextContent('Выберите файл для импорта')
    expect(trackerApi.importFile).not.toHaveBeenCalled()
  })

  it('показывает ошибку импорта', async () => {
    trackerApi.importFile.mockRejectedValue(new Error('Файл не содержит активностей'))
    const user = userEvent.setup()
    renderTracker()

    await screen.findByRole('heading', { name: 'Трекер привычек' })
    await user.click(screen.getByRole('button', { name: 'Импорт' }))
    const dialog = screen.getByRole('dialog')
    await user.upload(
      within(dialog).getByLabelText('Файл CSV'),
      new File(['csv'], 'Продуктивность.csv', { type: 'text/csv' }),
    )
    await user.click(within(dialog).getByRole('button', { name: 'Импортировать' }))

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Файл не содержит активностей')
    expect(within(dialog).queryByRole('status')).not.toBeInTheDocument()
  })

  it('закрывает модальное окно импорта без отправки', async () => {
    const user = userEvent.setup()
    renderTracker()

    await screen.findByRole('heading', { name: 'Трекер привычек' })
    await user.click(screen.getByRole('button', { name: 'Импорт' }))
    const dialog = screen.getByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Отмена' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(trackerApi.importFile).not.toHaveBeenCalled()
  })
})
