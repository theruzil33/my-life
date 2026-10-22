import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import TodayTab from '../../src/tracker/TodayTab'
import { ApiError, NETWORK_ERROR_MESSAGE } from '../../src/api/client'

vi.mock('../../src/auth/AuthContext', () => ({
  useAuth: () => ({ accessToken: 'token' }),
}))

vi.mock('../../src/api/trackerApi', () => ({
  trackerApi: {
    listOnDate: vi.fn(),
    markCompleted: vi.fn(),
    unmarkCompleted: vi.fn(),
  },
}))

import { trackerApi } from '../../src/api/trackerApi'

const TODAY = '2026-09-17'

const morningHabit = {
  id: 1,
  name: 'Чистил зубы',
  type: 'HABIT',
  date: TODAY,
  completed: false,
  timeOfDay: ['MORNING', 'EVENING'],
  completions: [
    { timeOfDay: 'MORNING', completed: false },
    { timeOfDay: 'EVENING', completed: true },
  ],
}

const dayTask = {
  id: 2,
  name: 'Созвон',
  type: 'TASK',
  date: TODAY,
  completed: false,
  timeOfDay: ['DAY'],
  completions: [{ timeOfDay: 'DAY', completed: false }],
}

const allDayTask = {
  id: 3,
  name: 'Купить хлеб',
  type: 'TASK',
  date: TODAY,
  completed: true,
  timeOfDay: [],
  completions: [],
}

function setTime(hours, minutes = 0, day = 17) {
  vi.setSystemTime(new Date(2026, 8, day, hours, minutes))
}

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

function groupHeadings() {
  return screen.getAllByRole('heading', { level: 2 }).map((heading) => heading.textContent)
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date', 'setInterval', 'clearInterval'] })
  setTime(8)
  vi.resetAllMocks()
  trackerApi.listOnDate.mockResolvedValue([morningHabit, dayTask, allDayTask])
})

afterEach(() => {
  vi.useRealTimers()
})

describe('TodayTab', () => {
  it('загружает активности обоих типов на сегодня без параметра типа', async () => {
    render(<TodayTab />)

    expect(await screen.findByRole('checkbox', { name: 'Чистил зубы — Утро' })).toBeInTheDocument()
    expect(screen.getByText('Созвон')).toBeInTheDocument()
    expect(screen.getByText('Купить хлеб')).toBeInTheDocument()
    expect(trackerApi.listOnDate).toHaveBeenCalledWith('token', TODAY)
    expect(screen.queryByLabelText('Дата')).not.toBeInTheDocument()
  })

  it('показывает утренний порядок групп', async () => {
    render(<TodayTab />)

    await screen.findByRole('heading', { name: 'Утро' })
    expect(groupHeadings()).toEqual(['Утро', 'День', 'Вечер', 'Весь день'])
  })

  it('показывает дневной порядок групп', async () => {
    setTime(13)
    render(<TodayTab />)

    await screen.findByRole('heading', { name: 'День' })
    expect(groupHeadings()).toEqual(['День', 'Вечер', 'Утро', 'Весь день'])
  })

  it('показывает вечерний порядок групп', async () => {
    setTime(20)
    render(<TodayTab />)

    await screen.findByRole('heading', { name: 'Вечер' })
    expect(groupHeadings()).toEqual(['Вечер', 'Утро', 'День', 'Весь день'])
  })

  it.each([
    [5, 59, 'Вечер'],
    [6, 0, 'Утро'],
    [11, 59, 'Утро'],
    [12, 0, 'День'],
    [17, 59, 'День'],
    [18, 0, 'Вечер'],
  ])('в %i:%i первой группой идёт «%s»', async (hours, minutes, expected) => {
    setTime(hours, minutes)
    render(<TodayTab />)

    await screen.findByRole('heading', { name: expected })
    expect(groupHeadings()[0]).toBe(expected)
  })

  it('отмечает текущую группу', async () => {
    render(<TodayTab />)

    await screen.findByRole('heading', { name: 'Утро' })
    expect(screen.getByText('Сейчас')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Утро' }).closest('section')).toHaveClass(
      'today-group-current',
    )
  })

  it('показывает активность с несколькими частями дня в каждой группе', async () => {
    render(<TodayTab />)

    expect(await screen.findByRole('checkbox', { name: 'Чистил зубы — Утро' })).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'Чистил зубы — Вечер' })).toBeInTheDocument()
    expect(screen.getAllByText('Чистил зубы')).toHaveLength(2)
  })

  it('скрывает пустые группы', async () => {
    trackerApi.listOnDate.mockResolvedValue([morningHabit])
    render(<TodayTab />)

    await screen.findByRole('heading', { name: 'Утро' })
    expect(screen.queryByRole('heading', { name: 'День' })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Весь день' })).not.toBeInTheDocument()
  })

  it('показывает метки типа активности', async () => {
    render(<TodayTab />)

    await screen.findByRole('checkbox', { name: 'Чистил зубы — Утро' })
    expect(screen.getAllByText('Привычка').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Задача').length).toBeGreaterThan(0)
  })

  it('отмечает часть дня и не меняет другую группу', async () => {
    const user = userEvent.setup()
    trackerApi.markCompleted.mockResolvedValue({
      ...morningHabit,
      completed: true,
      completions: [
        { timeOfDay: 'MORNING', completed: true },
        { timeOfDay: 'EVENING', completed: true },
      ],
    })
    render(<TodayTab />)

    const morning = await screen.findByRole('checkbox', { name: 'Чистил зубы — Утро' })
    const evening = screen.getByRole('checkbox', { name: 'Чистил зубы — Вечер' })
    expect(morning).not.toBeChecked()

    await user.click(morning)

    await waitFor(() => expect(morning).toBeChecked())
    expect(trackerApi.markCompleted).toHaveBeenCalledWith('token', 1, TODAY, 'MORNING')
    expect(evening).toBeChecked()
  })

  it('снимает общую отметку активности без частей дня', async () => {
    const user = userEvent.setup()
    trackerApi.unmarkCompleted.mockResolvedValue({ ...allDayTask, completed: false })
    render(<TodayTab />)

    const checkbox = await screen.findByRole('checkbox', { name: 'Купить хлеб' })
    expect(checkbox).toBeChecked()

    await user.click(checkbox)

    await waitFor(() => expect(checkbox).not.toBeChecked())
    expect(trackerApi.unmarkCompleted).toHaveBeenCalledWith('token', 3, TODAY)
  })

  it('возвращает чекбокс в исходное состояние при ошибке отметки', async () => {
    const user = userEvent.setup()
    trackerApi.markCompleted.mockRejectedValue(new ApiError('Habit not found', 404))
    render(<TodayTab />)

    const checkbox = await screen.findByRole('checkbox', { name: 'Чистил зубы — Утро' })
    await user.click(checkbox)

    expect(await screen.findByRole('alert')).toHaveTextContent('Habit not found')
    expect(checkbox).not.toBeChecked()
  })

  it('блокирует чекбокс во время запроса', async () => {
    const user = userEvent.setup()
    const { promise, resolve } = deferred()
    trackerApi.markCompleted.mockReturnValue(promise)
    render(<TodayTab />)

    const checkbox = await screen.findByRole('checkbox', { name: 'Чистил зубы — Утро' })
    await user.click(checkbox)

    expect(checkbox).toBeDisabled()

    resolve({
      ...morningHabit,
      completions: [
        { timeOfDay: 'MORNING', completed: true },
        { timeOfDay: 'EVENING', completed: true },
      ],
    })

    await waitFor(() => expect(checkbox).not.toBeDisabled())
    expect(checkbox).toBeChecked()
  })

  it('показывает признак загрузки', async () => {
    const { promise, resolve } = deferred()
    trackerApi.listOnDate.mockReturnValue(promise)
    render(<TodayTab />)

    expect(screen.getByText('Загрузка…')).toBeInTheDocument()

    resolve([])

    expect(await screen.findByText('На сегодня активностей нет.')).toBeInTheDocument()
  })

  it('показывает пустое состояние', async () => {
    trackerApi.listOnDate.mockResolvedValue([])
    render(<TodayTab />)

    expect(await screen.findByText('На сегодня активностей нет.')).toBeInTheDocument()
  })

  it('показывает ошибку загрузки без списка', async () => {
    trackerApi.listOnDate.mockRejectedValue(new ApiError(NETWORK_ERROR_MESSAGE, 0))
    render(<TodayTab />)

    expect(await screen.findByRole('alert')).toHaveTextContent(NETWORK_ERROR_MESSAGE)
    expect(screen.queryByText('Чистил зубы')).not.toBeInTheDocument()
  })

  it('перечитывает активности при смене refreshToken', async () => {
    const { rerender } = render(<TodayTab />)

    await screen.findByRole('checkbox', { name: 'Чистил зубы — Утро' })
    expect(trackerApi.listOnDate).toHaveBeenCalledTimes(1)

    rerender(<TodayTab refreshToken={1} />)

    await waitFor(() => expect(trackerApi.listOnDate).toHaveBeenCalledTimes(2))
  })

  it('меняет порядок групп при смене части дня без перезагрузки', async () => {
    setTime(11, 59)
    render(<TodayTab />)

    await screen.findByRole('heading', { name: 'Утро' })
    expect(groupHeadings()[0]).toBe('Утро')

    act(() => {
      vi.setSystemTime(new Date(2026, 8, 17, 12, 0))
      vi.advanceTimersByTime(60 * 1000)
    })

    await waitFor(() => expect(groupHeadings()[0]).toBe('День'))
    expect(groupHeadings()).toEqual(['День', 'Вечер', 'Утро', 'Весь день'])
  })

  it('перечитывает активности при смене сегодняшней даты', async () => {
    setTime(23, 59)
    render(<TodayTab />)

    await screen.findByRole('checkbox', { name: 'Чистил зубы — Утро' })
    expect(trackerApi.listOnDate).toHaveBeenCalledWith('token', TODAY)

    act(() => {
      vi.setSystemTime(new Date(2026, 8, 18, 0, 0))
      vi.advanceTimersByTime(60 * 1000)
    })

    await waitFor(() =>
      expect(trackerApi.listOnDate).toHaveBeenCalledWith('token', '2026-09-18'),
    )
  })
})
