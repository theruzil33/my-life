import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../../src/api/client', () => ({ request: vi.fn() }))

import { request } from '../../src/api/client'
import { trackerApi } from '../../src/api/trackerApi'

beforeEach(() => {
  vi.resetAllMocks()
})

describe('trackerApi', () => {
  it('запрашивает список привычек на дату с токеном', async () => {
    request.mockResolvedValue([])

    const result = await trackerApi.listOnDate('token', '2026-09-17')

    expect(request).toHaveBeenCalledWith('/tracker/habits?date=2026-09-17', {
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
    })

    expect(request).toHaveBeenCalledWith('/tracker/habits', {
      method: 'POST',
      body: { name: 'Зарядка', startDate: '2026-09-17', frequency: 'DAILY' },
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
    })

    expect(request).toHaveBeenCalledWith('/tracker/habits', {
      method: 'POST',
      body: {
        name: 'Бассейн',
        startDate: '2026-09-17',
        frequency: 'SPECIFIC_WEEKDAYS',
        weekdays: ['MONDAY', 'WEDNESDAY'],
      },
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
