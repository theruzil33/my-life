import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../../src/api/client', () => ({ request: vi.fn() }))

import { request } from '../../src/api/client'
import { trackerApi } from '../../src/api/trackerApi'

beforeEach(() => {
  vi.resetAllMocks()
})

describe('trackerApi', () => {
  it('запрашивает список активностей на дату с типом и токеном', async () => {
    request.mockResolvedValue([])

    const result = await trackerApi.listOnDate('token', '2026-09-17', 'TASK')

    expect(request).toHaveBeenCalledWith('/tracker/habits?date=2026-09-17&type=TASK', {
      token: 'token',
      requiresAuth: true,
    })
    expect(result).toEqual([])
  })

  it('запрашивает историю за диапазон с обеими границами', async () => {
    request.mockResolvedValue([])

    await trackerApi.history('token', '2026-09-01', '2026-09-17')

    expect(request).toHaveBeenCalledWith(
      '/tracker/habits/history?from=2026-09-01&to=2026-09-17',
      { token: 'token', requiresAuth: true },
    )
  })

  it('создаёт ежедневную привычку без дней недели', async () => {
    request.mockResolvedValue({ id: 1 })

    await trackerApi.create('token', {
      name: 'Зарядка',
      startDate: '2026-09-17',
      frequency: 'DAILY',
      weekdays: [],
      type: 'HABIT',
    })

    expect(request).toHaveBeenCalledWith('/tracker/habits', {
      method: 'POST',
      body: { name: 'Зарядка', startDate: '2026-09-17', frequency: 'DAILY', type: 'HABIT', timeOfDay: [] },
      token: 'token',
      requiresAuth: true,
    })
  })

  it('передаёт признак отметки прошлого при создании', async () => {
    request.mockResolvedValue({ id: 5 })

    await trackerApi.create('token', {
      name: 'Бег',
      startDate: '2026-09-01',
      frequency: 'DAILY',
      weekdays: [],
      type: 'HABIT',
      markPastCompleted: true,
    })

    expect(request).toHaveBeenCalledWith('/tracker/habits', {
      method: 'POST',
      body: {
        name: 'Бег',
        startDate: '2026-09-01',
        frequency: 'DAILY',
        type: 'HABIT',
        timeOfDay: [],
        markPastCompleted: true,
      },
      token: 'token',
      requiresAuth: true,
    })
  })

  it('не включает признак отметки прошлого, если он не задан', async () => {
    request.mockResolvedValue({ id: 6 })

    await trackerApi.create('token', {
      name: 'Бег',
      startDate: '2026-09-01',
      frequency: 'DAILY',
      weekdays: [],
      type: 'HABIT',
    })

    expect(request).toHaveBeenCalledWith('/tracker/habits', {
      method: 'POST',
      body: { name: 'Бег', startDate: '2026-09-01', frequency: 'DAILY', type: 'HABIT', timeOfDay: [] },
      token: 'token',
      requiresAuth: true,
    })
  })

  it('создаёт привычку по конкретным дням недели', async () => {
    request.mockResolvedValue({ id: 2 })

    await trackerApi.create('token', {
      name: 'Бассейн',
      startDate: '2026-09-17',
      frequency: 'SPECIFIC_WEEKDAYS',
      weekdays: ['MONDAY', 'WEDNESDAY'],
      type: 'TASK',
    })

    expect(request).toHaveBeenCalledWith('/tracker/habits', {
      method: 'POST',
      body: {
        name: 'Бассейн',
        startDate: '2026-09-17',
        frequency: 'SPECIFIC_WEEKDAYS',
        weekdays: ['MONDAY', 'WEDNESDAY'],
        type: 'TASK',
        timeOfDay: [],
      },
      token: 'token',
      requiresAuth: true,
    })
  })

  it('переименовывает активность через PUT с полным определением', async () => {
    request.mockResolvedValue({ id: 3 })

    await trackerApi.update('token', 3, {
      name: 'Новое имя',
      startDate: '2026-09-01',
      frequency: 'SPECIFIC_WEEKDAYS',
      weekdays: ['MONDAY'],
      type: 'HABIT',
    })

    expect(request).toHaveBeenCalledWith('/tracker/habits/3', {
      method: 'PUT',
      body: {
        name: 'Новое имя',
        startDate: '2026-09-01',
        frequency: 'SPECIFIC_WEEKDAYS',
        weekdays: ['MONDAY'],
        type: 'HABIT',
        timeOfDay: [],
      },
      token: 'token',
      requiresAuth: true,
    })
  })

  it('переименовывает ежедневную активность без дней недели', async () => {
    request.mockResolvedValue({ id: 4 })

    await trackerApi.update('token', 4, {
      name: 'Новое имя',
      startDate: '2026-09-01',
      frequency: 'DAILY',
      weekdays: [],
      type: 'TASK',
    })

    expect(request).toHaveBeenCalledWith('/tracker/habits/4', {
      method: 'PUT',
      body: { name: 'Новое имя', startDate: '2026-09-01', frequency: 'DAILY', type: 'TASK', timeOfDay: [] },
      token: 'token',
      requiresAuth: true,
    })
  })


  it('передаёт части дня при создании', async () => {
    request.mockResolvedValue({ id: 8 })

    await trackerApi.create('token', {
      name: 'Чистил зубы',
      startDate: '2026-09-17',
      frequency: 'DAILY',
      weekdays: [],
      type: 'HABIT',
      timeOfDay: ['MORNING', 'EVENING'],
    })

    expect(request).toHaveBeenCalledWith('/tracker/habits', {
      method: 'POST',
      body: {
        name: 'Чистил зубы',
        startDate: '2026-09-17',
        frequency: 'DAILY',
        type: 'HABIT',
        timeOfDay: ['MORNING', 'EVENING'],
      },
      token: 'token',
      requiresAuth: true,
    })
  })

  it('передаёт части дня при обновлении', async () => {
    request.mockResolvedValue({ id: 8 })

    await trackerApi.update('token', 8, {
      name: 'Чистил зубы',
      startDate: '2026-09-17',
      frequency: 'DAILY',
      weekdays: [],
      type: 'HABIT',
      timeOfDay: ['MORNING'],
    })

    expect(request).toHaveBeenCalledWith('/tracker/habits/8', {
      method: 'PUT',
      body: {
        name: 'Чистил зубы',
        startDate: '2026-09-17',
        frequency: 'DAILY',
        type: 'HABIT',
        timeOfDay: ['MORNING'],
      },
      token: 'token',
      requiresAuth: true,
    })
  })

  it('отмечает часть дня через query-параметр', async () => {
    request.mockResolvedValue({ completed: false })

    await trackerApi.markCompleted('token', 42, '2026-09-17', 'MORNING')

    expect(request).toHaveBeenCalledWith(
      '/tracker/habits/42/completions/2026-09-17?timeOfDay=MORNING',
      { method: 'PUT', token: 'token', requiresAuth: true },
    )
  })

  it('отменяет часть дня через query-параметр', async () => {
    request.mockResolvedValue({ completed: false })

    await trackerApi.unmarkCompleted('token', 42, '2026-09-17', 'EVENING')

    expect(request).toHaveBeenCalledWith(
      '/tracker/habits/42/completions/2026-09-17?timeOfDay=EVENING',
      { method: 'DELETE', token: 'token', requiresAuth: true },
    )
  })

  it('удаляет активность через DELETE', async () => {
    request.mockResolvedValue(null)

    await trackerApi.remove('token', 7)

    expect(request).toHaveBeenCalledWith('/tracker/habits/7', {
      method: 'DELETE',
      token: 'token',
      requiresAuth: true,
    })
  })

  it('отмечает выполнение через PUT на дату', async () => {
    request.mockResolvedValue({ completed: true })

    await trackerApi.markCompleted('token', 42, '2026-09-17')

    expect(request).toHaveBeenCalledWith('/tracker/habits/42/completions/2026-09-17', {
      method: 'PUT',
      token: 'token',
      requiresAuth: true,
    })
  })

  it('отменяет выполнение через DELETE на дату', async () => {
    request.mockResolvedValue({ completed: false })

    await trackerApi.unmarkCompleted('token', 42, '2026-09-17')

    expect(request).toHaveBeenCalledWith('/tracker/habits/42/completions/2026-09-17', {
      method: 'DELETE',
      token: 'token',
      requiresAuth: true,
    })
  })
})
