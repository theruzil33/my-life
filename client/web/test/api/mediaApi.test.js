import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../../src/api/client', () => ({ request: vi.fn() }))

import { request } from '../../src/api/client'
import { mediaApi } from '../../src/api/mediaApi'

beforeEach(() => {
  vi.resetAllMocks()
})

describe('mediaApi', () => {
  it('запрашивает список записей вида с токеном', async () => {
    request.mockResolvedValue([{ id: 1, title: 'Дюна' }])

    const result = await mediaApi.list('token', 'BOOK')

    expect(request).toHaveBeenCalledWith('/media/items?type=BOOK', {
      token: 'token',
      requiresAuth: true,
    })
    expect(result).toEqual([{ id: 1, title: 'Дюна' }])
  })

  it('запрашивает список без вида, если вид не указан', async () => {
    request.mockResolvedValue([])

    await mediaApi.list('token')

    expect(request).toHaveBeenCalledWith('/media/items', {
      token: 'token',
      requiresAuth: true,
    })
  })

  it('создаёт запись с видом, названием, описанием и статусом', async () => {
    request.mockResolvedValue({ id: 1 })

    await mediaApi.create('token', {
      type: 'FILM',
      title: 'Дюна',
      description: 'Экранизация',
      status: 'IN_PROGRESS',
    })

    expect(request).toHaveBeenCalledWith('/media/items', {
      method: 'POST',
      body: { type: 'FILM', title: 'Дюна', description: 'Экранизация', status: 'IN_PROGRESS' },
      token: 'token',
      requiresAuth: true,
    })
  })

  it('создаёт запись без описания как null', async () => {
    request.mockResolvedValue({ id: 1 })

    await mediaApi.create('token', { type: 'GAME', title: 'Hades', status: 'NOT_STARTED' })

    expect(request).toHaveBeenCalledWith('/media/items', {
      method: 'POST',
      body: { type: 'GAME', title: 'Hades', description: null, status: 'NOT_STARTED' },
      token: 'token',
      requiresAuth: true,
    })
  })

  it('изменяет запись через PUT без вида', async () => {
    request.mockResolvedValue({ id: 1 })

    await mediaApi.update('token', 1, { title: 'Солярис', description: 'Лем', status: 'COMPLETED' })

    expect(request).toHaveBeenCalledWith('/media/items/1', {
      method: 'PUT',
      body: { title: 'Солярис', description: 'Лем', status: 'COMPLETED' },
      token: 'token',
      requiresAuth: true,
    })
  })

  it('удаляет запись через DELETE', async () => {
    request.mockResolvedValue(null)

    await mediaApi.remove('token', 7)

    expect(request).toHaveBeenCalledWith('/media/items/7', {
      method: 'DELETE',
      token: 'token',
      requiresAuth: true,
    })
  })
})
