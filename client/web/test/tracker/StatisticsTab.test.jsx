import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import StatisticsTab from '../../src/tracker/StatisticsTab'
import { ApiError, NETWORK_ERROR_MESSAGE } from '../../src/api/client'

vi.mock('../../src/auth/AuthContext', () => ({
  useAuth: () => ({ accessToken: 'token' }),
}))

vi.mock('../../src/api/trackerApi', () => ({
  trackerApi: {
    listOnDate: vi.fn(),
    history: vi.fn(),
    create: vi.fn(),
    markCompleted: vi.fn(),
    unmarkCompleted: vi.fn(),
  },
}))

vi.mock('../../src/tracker/dates', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    todayIso: () => '2026-09-17',
    firstDayOfMonthIso: () => '2026-09-01',
    isFuture: (iso) => iso > '2026-09-17',
  }
})

import { trackerApi } from '../../src/api/trackerApi'

const historyRecords = [
  {
    id: 1,
    name: 'Зарядка',
    startDate: '2026-09-01',
    frequency: 'DAILY',
    weekdays: [],
    days: [
      { date: '2026-09-02', completed: true },
      { date: '2026-09-03', completed: false },
    ],
  },
]

function deferred() {
  let resolve
  const promise = new Promise((resolvePromise) => {
    resolve = resolvePromise
  })
  return { promise, resolve }
}

beforeEach(() => {
  vi.resetAllMocks()
  trackerApi.history.mockResolvedValue(historyRecords)
})

describe('StatisticsTab', () => {
  it('загружает статистику за текущий месяц по умолчанию', async () => {
    render(<StatisticsTab />)

    await waitFor(() =>
      expect(trackerApi.history).toHaveBeenCalledWith('token', '2026-09-01', '2026-09-17'),
    )
    expect(screen.getByLabelText('С')).toHaveValue('2026-09-01')
    expect(screen.getByLabelText('По')).toHaveValue('2026-09-17')
  })

  it('показывает индикацию загрузки', async () => {
    const { promise, resolve } = deferred()
    trackerApi.history.mockReturnValue(promise)
    render(<StatisticsTab />)

    expect(screen.getByText('Загрузка…')).toBeInTheDocument()

    resolve(historyRecords)
    await waitFor(() => expect(screen.queryByText('Загрузка…')).not.toBeInTheDocument())
  })

  it('показывает пустое состояние без привычек', async () => {
    trackerApi.history.mockResolvedValue([])
    render(<StatisticsTab />)

    expect(await screen.findByText('За выбранный период привычек нет.')).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('показывает ошибку загрузки без матрицы', async () => {
    trackerApi.history.mockRejectedValue(new ApiError(NETWORK_ERROR_MESSAGE, 0))
    render(<StatisticsTab />)

    expect(await screen.findByRole('alert')).toHaveTextContent(NETWORK_ERROR_MESSAGE)
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('отображает матрицу выполнения со статусами квадратов', async () => {
    render(<StatisticsTab />)

    expect(
      await screen.findByLabelText('Зарядка, 2026-09-02: выполнено'),
    ).toHaveClass('stats-square-completed')
    expect(screen.getByLabelText('Зарядка, 2026-09-03: не выполнено')).toHaveClass(
      'stats-square-scheduled',
    )
    expect(screen.getByLabelText('Зарядка, 2026-09-04: не запланировано')).toHaveClass(
      'stats-square-unscheduled',
    )
    expect(screen.getByRole('rowheader', { name: 'Зарядка' })).toBeInTheDocument()
    expect(screen.getByRole('table')).toBeInTheDocument()
  })

  it('применяет выбранный диапазон', async () => {
    const user = userEvent.setup()
    render(<StatisticsTab />)
    await screen.findByRole('table')

    fireEvent.change(screen.getByLabelText('С'), { target: { value: '2026-09-05' } })
    fireEvent.change(screen.getByLabelText('По'), { target: { value: '2026-09-10' } })
    await user.click(screen.getByRole('button', { name: 'Применить' }))

    await waitFor(() =>
      expect(trackerApi.history).toHaveBeenLastCalledWith('token', '2026-09-05', '2026-09-10'),
    )
    expect(screen.getByLabelText('С')).toHaveValue('2026-09-05')
    expect(screen.getByLabelText('По')).toHaveValue('2026-09-10')
  })

  it('не применяет диапазон с началом позже конца', async () => {
    const user = userEvent.setup()
    render(<StatisticsTab />)
    await screen.findByRole('table')

    fireEvent.change(screen.getByLabelText('С'), { target: { value: '2026-09-10' } })
    fireEvent.change(screen.getByLabelText('По'), { target: { value: '2026-09-05' } })
    await user.click(screen.getByRole('button', { name: 'Применить' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Начало диапазона не может быть позже конца',
    )
    expect(trackerApi.history).toHaveBeenCalledTimes(1)
  })

  it('не применяет диапазон с будущей границей', async () => {
    const user = userEvent.setup()
    render(<StatisticsTab />)
    await screen.findByRole('table')

    fireEvent.change(screen.getByLabelText('По'), { target: { value: '2026-09-18' } })
    await user.click(screen.getByRole('button', { name: 'Применить' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Дата диапазона не может быть позже сегодняшней',
    )
    expect(trackerApi.history).toHaveBeenCalledTimes(1)
  })

  it('сохраняет прежний диапазон и матрицу при ошибке валидации от трекера', async () => {
    const user = userEvent.setup()
    trackerApi.history
      .mockResolvedValueOnce(historyRecords)
      .mockRejectedValueOnce(new ApiError('Requested date is before the earliest habit start date', 400))
    render(<StatisticsTab />)
    await screen.findByRole('table')

    fireEvent.change(screen.getByLabelText('С'), { target: { value: '2026-08-01' } })
    await user.click(screen.getByRole('button', { name: 'Применить' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Requested date is before the earliest habit start date',
    )
    expect(screen.getByLabelText('С')).toHaveValue('2026-09-01')
    expect(screen.getByLabelText('Зарядка, 2026-09-02: выполнено')).toBeInTheDocument()
  })
})
