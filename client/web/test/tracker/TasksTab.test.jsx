import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import TasksTab from '../../src/tracker/TasksTab'
import { ApiError } from '../../src/api/client'

vi.mock('../../src/auth/AuthContext', () => ({
  useAuth: () => ({ accessToken: 'token' }),
}))

vi.mock('../../src/api/trackerApi', () => ({
  trackerApi: {
    listOnDate: vi.fn(),
    history: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    remove: vi.fn(),
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

const tasks = [
  {
    id: 5,
    name: 'Прочитать главу',
    startDate: '2026-09-01',
    frequency: 'DAILY',
    weekdays: [],
    type: 'TASK',
    date: TODAY,
    completed: false,
  },
]

beforeEach(() => {
  vi.resetAllMocks()
  trackerApi.listOnDate.mockResolvedValue(tasks)
  trackerApi.create.mockResolvedValue({ id: 99 })
})

describe('TasksTab', () => {
  it('загружает задачи на сегодняшнюю дату с типом TASK', async () => {
    render(<TasksTab />)

    expect(await screen.findByText('Прочитать главу')).toBeInTheDocument()
    expect(trackerApi.listOnDate).toHaveBeenCalledWith('token', TODAY, 'TASK')
  })

  it('показывает пустое состояние', async () => {
    trackerApi.listOnDate.mockResolvedValue([])
    render(<TasksTab />)

    expect(await screen.findByText('На выбранную дату задач нет.')).toBeInTheDocument()
  })

  it('отмечает выполнение задачи', async () => {
    const user = userEvent.setup()
    trackerApi.markCompleted.mockResolvedValue({ ...tasks[0], completed: true })
    render(<TasksTab />)

    const checkbox = await screen.findByRole('checkbox', { name: 'Прочитать главу' })
    await user.click(checkbox)

    await waitFor(() => expect(checkbox).toBeChecked())
    expect(trackerApi.markCompleted).toHaveBeenCalledWith('token', 5, TODAY)
  })

  it('создаёт задачу с типом TASK', async () => {
    const user = userEvent.setup()
    render(<TasksTab />)
    await screen.findByText('Прочитать главу')

    await user.click(screen.getByRole('button', { name: 'Добавить задачу' }))
    await user.type(screen.getByLabelText('Название'), 'Полить цветы')
    await user.click(screen.getByRole('button', { name: 'Создать' }))

    await waitFor(() =>
      expect(trackerApi.create).toHaveBeenCalledWith('token', {
        name: 'Полить цветы',
        startDate: TODAY,
        frequency: 'DAILY',
        weekdays: [],
        type: 'TASK',
      }),
    )
  })

  it('отправляет признак отметки прошлого при создании задачи', async () => {
    const user = userEvent.setup()
    render(<TasksTab />)
    await screen.findByText('Прочитать главу')

    await user.click(screen.getByRole('button', { name: 'Добавить задачу' }))
    await user.type(screen.getByLabelText('Название'), 'Полить цветы')
    const pastCheckbox = screen.getByRole('checkbox', { name: 'Отметить прошлое выполненным' })
    expect(pastCheckbox).not.toBeChecked()
    await user.click(pastCheckbox)
    await user.click(screen.getByRole('button', { name: 'Создать' }))

    await waitFor(() =>
      expect(trackerApi.create).toHaveBeenCalledWith('token', {
        name: 'Полить цветы',
        startDate: TODAY,
        frequency: 'DAILY',
        weekdays: [],
        type: 'TASK',
        markPastCompleted: true,
      }),
    )
  })

  it('сообщает об отсутствии названия задачи', async () => {
    const user = userEvent.setup()
    render(<TasksTab />)
    await screen.findByText('Прочитать главу')

    await user.click(screen.getByRole('button', { name: 'Добавить задачу' }))
    await user.click(screen.getByRole('button', { name: 'Создать' }))

    expect(await screen.findByText('Укажите название задачи')).toBeInTheDocument()
    expect(trackerApi.create).not.toHaveBeenCalled()
  })

  it('переименовывает задачу и обновляет название в списке', async () => {
    const user = userEvent.setup()
    trackerApi.update.mockResolvedValue({ id: 5, name: 'Прочитать две главы' })
    render(<TasksTab />)
    await screen.findByText('Прочитать главу')

    await user.click(screen.getByRole('button', { name: 'Переименовать Прочитать главу' }))
    expect(screen.getByLabelText('Название')).toHaveValue('Прочитать главу')
    await user.clear(screen.getByLabelText('Название'))
    await user.type(screen.getByLabelText('Название'), 'Прочитать две главы')
    await user.click(screen.getByRole('button', { name: 'Переименовать' }))

    await waitFor(() =>
      expect(trackerApi.update).toHaveBeenCalledWith('token', 5, {
        name: 'Прочитать две главы',
        startDate: '2026-09-01',
        frequency: 'DAILY',
        weekdays: [],
        type: 'TASK',
      }),
    )
    await waitFor(() => expect(screen.getByText('Прочитать две главы')).toBeInTheDocument())
  })

  it('отклоняет переименование задачи без названия', async () => {
    const user = userEvent.setup()
    render(<TasksTab />)
    await screen.findByText('Прочитать главу')

    await user.click(screen.getByRole('button', { name: 'Переименовать Прочитать главу' }))
    await user.clear(screen.getByLabelText('Название'))
    await user.click(screen.getByRole('button', { name: 'Переименовать' }))

    expect(await screen.findByText('Укажите название задачи')).toBeInTheDocument()
    expect(trackerApi.update).not.toHaveBeenCalled()
  })

  it('сохраняет прежнее название при ошибке переименования задачи', async () => {
    const user = userEvent.setup()
    trackerApi.update.mockRejectedValue(new ApiError('Habit name must not be blank', 400))
    render(<TasksTab />)
    await screen.findByText('Прочитать главу')

    await user.click(screen.getByRole('button', { name: 'Переименовать Прочитать главу' }))
    await user.clear(screen.getByLabelText('Название'))
    await user.type(screen.getByLabelText('Название'), 'Другое')
    await user.click(screen.getByRole('button', { name: 'Переименовать' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Habit name must not be blank')
    expect(screen.getByText('Прочитать главу')).toBeInTheDocument()
  })

  it('удаляет задачу после подтверждения', async () => {
    const user = userEvent.setup()
    trackerApi.remove.mockResolvedValue(null)
    render(<TasksTab />)
    await screen.findByText('Прочитать главу')

    await user.click(screen.getByRole('button', { name: 'Удалить Прочитать главу' }))
    await user.click(screen.getByRole('button', { name: 'Удалить' }))

    await waitFor(() => expect(trackerApi.remove).toHaveBeenCalledWith('token', 5))
    await waitFor(() => expect(screen.queryByText('Прочитать главу')).not.toBeInTheDocument())
  })

  it('отмена удаления сохраняет задачу', async () => {
    const user = userEvent.setup()
    render(<TasksTab />)
    await screen.findByText('Прочитать главу')

    await user.click(screen.getByRole('button', { name: 'Удалить Прочитать главу' }))
    await user.click(screen.getByRole('button', { name: 'Отмена' }))

    expect(trackerApi.remove).not.toHaveBeenCalled()
    expect(screen.getByText('Прочитать главу')).toBeInTheDocument()
  })

  it('сохраняет задачу при ошибке удаления', async () => {
    const user = userEvent.setup()
    trackerApi.remove.mockRejectedValue(new ApiError('Habit not found', 404))
    render(<TasksTab />)
    await screen.findByText('Прочитать главу')

    await user.click(screen.getByRole('button', { name: 'Удалить Прочитать главу' }))
    await user.click(screen.getByRole('button', { name: 'Удалить' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Habit not found')
    expect(screen.getByText('Прочитать главу')).toBeInTheDocument()
  })
})
