import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import HabitsTab from '../../src/tracker/HabitsTab'
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
    isFuture: (iso) => iso > '2026-09-17',
  }
})

import { trackerApi } from '../../src/api/trackerApi'

const TODAY = '2026-09-17'

const dailyHabits = [
  {
    id: 1,
    name: 'Зарядка',
    startDate: '2026-09-01',
    frequency: 'DAILY',
    weekdays: [],
    date: TODAY,
    completed: false,
  },
  {
    id: 2,
    name: 'Чтение',
    startDate: '2026-09-01',
    frequency: 'DAILY',
    weekdays: [],
    date: TODAY,
    completed: true,
  },
]

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

async function openModal(user) {
  await user.click(screen.getByRole('button', { name: 'Добавить привычку' }))
  return screen.getByRole('dialog')
}

beforeEach(() => {
  vi.resetAllMocks()
  trackerApi.listOnDate.mockResolvedValue(dailyHabits)
  trackerApi.create.mockResolvedValue({ id: 99 })
})

describe('HabitsTab', () => {
  it('загружает привычки на сегодняшнюю дату', async () => {
    render(<HabitsTab />)

    expect(await screen.findByText('Зарядка')).toBeInTheDocument()
    expect(screen.getByText('Чтение')).toBeInTheDocument()
    expect(trackerApi.listOnDate).toHaveBeenCalledWith('token', TODAY)
  })

  it('показывает пустое состояние', async () => {
    trackerApi.listOnDate.mockResolvedValue([])
    render(<HabitsTab />)

    expect(await screen.findByText('На выбранную дату привычек нет.')).toBeInTheDocument()
  })

  it('показывает ошибку загрузки без списка', async () => {
    trackerApi.listOnDate.mockRejectedValue(new ApiError(NETWORK_ERROR_MESSAGE, 0))
    render(<HabitsTab />)

    expect(await screen.findByRole('alert')).toHaveTextContent(NETWORK_ERROR_MESSAGE)
    expect(screen.queryByText('Зарядка')).not.toBeInTheDocument()
  })

  it('загружает привычки на выбранную прошлую дату', async () => {
    trackerApi.listOnDate.mockImplementation((token, date) =>
      Promise.resolve(date === '2026-09-10' ? [{ ...dailyHabits[0], completed: true, date }] : dailyHabits),
    )
    render(<HabitsTab />)

    await screen.findByText('Зарядка')
    fireEvent.change(screen.getByLabelText('Дата'), { target: { value: '2026-09-10' } })

    await waitFor(() => expect(trackerApi.listOnDate).toHaveBeenCalledWith('token', '2026-09-10'))
    expect(await screen.findByRole('checkbox', { name: 'Зарядка' })).toBeChecked()
  })

  it('не позволяет выбрать будущую дату', async () => {
    render(<HabitsTab />)

    const dateInput = await screen.findByLabelText('Дата')
    expect(dateInput).toHaveAttribute('max', TODAY)

    fireEvent.change(dateInput, { target: { value: '2026-09-18' } })

    expect(dateInput.value).toBe(TODAY)
    expect(trackerApi.listOnDate).not.toHaveBeenCalledWith('token', '2026-09-18')
  })

  it('отмечает выполнение привычки', async () => {
    const user = userEvent.setup()
    trackerApi.markCompleted.mockResolvedValue({ ...dailyHabits[0], completed: true })
    render(<HabitsTab />)

    const checkbox = await screen.findByRole('checkbox', { name: 'Зарядка' })
    expect(checkbox).not.toBeChecked()

    await user.click(checkbox)

    await waitFor(() => expect(checkbox).toBeChecked())
    expect(trackerApi.markCompleted).toHaveBeenCalledWith('token', 1, TODAY)
  })

  it('снимает отметку выполнения', async () => {
    const user = userEvent.setup()
    trackerApi.unmarkCompleted.mockResolvedValue({ ...dailyHabits[1], completed: false })
    render(<HabitsTab />)

    const checkbox = await screen.findByRole('checkbox', { name: 'Чтение' })
    expect(checkbox).toBeChecked()

    await user.click(checkbox)

    await waitFor(() => expect(checkbox).not.toBeChecked())
    expect(trackerApi.unmarkCompleted).toHaveBeenCalledWith('token', 2, TODAY)
  })

  it('возвращает чекбокс в исходное состояние при ошибке отметки', async () => {
    const user = userEvent.setup()
    trackerApi.markCompleted.mockRejectedValue(new ApiError('Habit is not scheduled on the given date', 400))
    render(<HabitsTab />)

    const checkbox = await screen.findByRole('checkbox', { name: 'Зарядка' })
    await user.click(checkbox)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Habit is not scheduled on the given date',
    )
    expect(checkbox).not.toBeChecked()
  })

  it('блокирует чекбокс во время запроса', async () => {
    const user = userEvent.setup()
    const { promise, resolve } = deferred()
    trackerApi.markCompleted.mockReturnValue(promise)
    render(<HabitsTab />)

    const checkbox = await screen.findByRole('checkbox', { name: 'Зарядка' })
    await user.click(checkbox)

    expect(checkbox).toBeDisabled()

    resolve({ ...dailyHabits[0], completed: true })

    await waitFor(() => expect(checkbox).not.toBeDisabled())
    expect(checkbox).toBeChecked()
  })

  it('открывает модальное окно создания', async () => {
    const user = userEvent.setup()
    render(<HabitsTab />)
    await screen.findByText('Зарядка')

    const dialog = await openModal(user)

    expect(dialog).toBeInTheDocument()
    expect(screen.getByLabelText('Название')).toBeInTheDocument()
    expect(screen.getByLabelText('Дата начала')).toHaveValue(TODAY)
    expect(screen.getByRole('radio', { name: 'Ежедневно' })).toBeChecked()
    expect(screen.queryByRole('checkbox', { name: 'Понедельник' })).not.toBeInTheDocument()
  })

  it('отклоняет создание без названия', async () => {
    const user = userEvent.setup()
    render(<HabitsTab />)
    await screen.findByText('Зарядка')

    await openModal(user)
    await user.click(screen.getByRole('button', { name: 'Создать' }))

    expect(await screen.findByText('Укажите название привычки')).toBeInTheDocument()
    expect(trackerApi.create).not.toHaveBeenCalled()
  })

  it('отклоняет создание с датой начала в прошлом', async () => {
    const user = userEvent.setup()
    render(<HabitsTab />)
    await screen.findByText('Зарядка')

    await openModal(user)
    await user.type(screen.getByLabelText('Название'), 'Новая')
    fireEvent.change(screen.getByLabelText('Дата начала'), { target: { value: '2026-09-16' } })
    await user.click(screen.getByRole('button', { name: 'Создать' }))

    expect(await screen.findByText('Дата начала не может быть раньше сегодняшней')).toBeInTheDocument()
    expect(trackerApi.create).not.toHaveBeenCalled()
  })

  it('показывает дни недели только для периодичности по дням', async () => {
    const user = userEvent.setup()
    render(<HabitsTab />)
    await screen.findByText('Зарядка')

    await openModal(user)
    await user.click(screen.getByRole('radio', { name: 'По дням недели' }))

    expect(screen.getByRole('checkbox', { name: 'Понедельник' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Создать' }))
    expect(await screen.findByText('Выберите хотя бы один день недели')).toBeInTheDocument()
    expect(trackerApi.create).not.toHaveBeenCalled()
  })

  it('создаёт ежедневную привычку и обновляет список', async () => {
    const user = userEvent.setup()
    render(<HabitsTab />)
    await screen.findByText('Зарядка')

    await openModal(user)
    await user.type(screen.getByLabelText('Название'), 'Медитация')
    await user.click(screen.getByRole('button', { name: 'Создать' }))

    await waitFor(() =>
      expect(trackerApi.create).toHaveBeenCalledWith('token', {
        name: 'Медитация',
        startDate: TODAY,
        frequency: 'DAILY',
        weekdays: [],
      }),
    )
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(trackerApi.listOnDate).toHaveBeenCalledTimes(2)
  })

  it('создаёт привычку по конкретным дням недели', async () => {
    const user = userEvent.setup()
    render(<HabitsTab />)
    await screen.findByText('Зарядка')

    await openModal(user)
    await user.type(screen.getByLabelText('Название'), 'Бассейн')
    await user.click(screen.getByRole('radio', { name: 'По дням недели' }))
    await user.click(screen.getByRole('checkbox', { name: 'Понедельник' }))
    await user.click(screen.getByRole('button', { name: 'Создать' }))

    await waitFor(() =>
      expect(trackerApi.create).toHaveBeenCalledWith('token', {
        name: 'Бассейн',
        startDate: TODAY,
        frequency: 'SPECIFIC_WEEKDAYS',
        weekdays: ['MONDAY'],
      }),
    )
  })

  it('привычка с будущей датой начала не появляется в списке на сегодня', async () => {
    const user = userEvent.setup()
    trackerApi.listOnDate.mockResolvedValue([])
    render(<HabitsTab />)
    await screen.findByText('На выбранную дату привычек нет.')

    await openModal(user)
    await user.type(screen.getByLabelText('Название'), 'Йога')
    fireEvent.change(screen.getByLabelText('Дата начала'), { target: { value: '2026-09-20' } })
    await user.click(screen.getByRole('button', { name: 'Создать' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(await screen.findByText('На выбранную дату привычек нет.')).toBeInTheDocument()
  })

  it('закрывает модальное окно без создания', async () => {
    const user = userEvent.setup()
    render(<HabitsTab />)
    await screen.findByText('Зарядка')

    await openModal(user)
    await user.click(screen.getByRole('button', { name: 'Отмена' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(trackerApi.create).not.toHaveBeenCalled()
  })

  it('показывает ошибку трекера при создании и сохраняет введённые значения', async () => {
    const user = userEvent.setup()
    trackerApi.create.mockRejectedValue(new ApiError('Habit name must not be blank', 400))
    render(<HabitsTab />)
    await screen.findByText('Зарядка')

    await openModal(user)
    await user.type(screen.getByLabelText('Название'), 'Медитация')
    await user.click(screen.getByRole('button', { name: 'Создать' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Habit name must not be blank')
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByLabelText('Название')).toHaveValue('Медитация')
  })

  it('не предоставляет действий изменения и удаления привычки', async () => {
    render(<HabitsTab />)

    await screen.findByText('Зарядка')
    expect(screen.queryByRole('button', { name: /Изменить/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Удалить/ })).not.toBeInTheDocument()
  })
})
