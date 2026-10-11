import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../../src/api/client', () => ({ request: vi.fn() }))

import { request } from '../../src/api/client'
import { financeApi } from '../../src/api/financeApi'

beforeEach(() => {
  vi.resetAllMocks()
})

describe('financeApi', () => {
  it('запрашивает баланс с токеном', async () => {
    request.mockResolvedValue({ startingBalance: 0, amount: 0 })

    const result = await financeApi.getBalance('token')

    expect(request).toHaveBeenCalledWith('/finances/balance', {
      token: 'token',
      requiresAuth: true,
    })
    expect(result).toEqual({ startingBalance: 0, amount: 0 })
  })

  it('изменяет начальный баланс через PUT', async () => {
    request.mockResolvedValue({ startingBalance: 100, amount: 100 })

    await financeApi.updateBalance('token', 100)

    expect(request).toHaveBeenCalledWith('/finances/balance', {
      method: 'PUT',
      body: { startingBalance: 100 },
      token: 'token',
      requiresAuth: true,
    })
  })

  it('запрашивает список категорий с токеном', async () => {
    request.mockResolvedValue([])

    const result = await financeApi.listCategories('token')

    expect(request).toHaveBeenCalledWith('/finances/categories', {
      token: 'token',
      requiresAuth: true,
    })
    expect(result).toEqual([])
  })

  it('создаёт категорию с типом, названием, группой и планом', async () => {
    request.mockResolvedValue({ id: 1 })

    await financeApi.createCategory('token', {
      type: 'INCOME',
      name: 'Зарплата',
      group: 'Работа',
      plannedAmount: 500.5,
    })

    expect(request).toHaveBeenCalledWith('/finances/categories', {
      method: 'POST',
      body: { type: 'INCOME', name: 'Зарплата', group: 'Работа', plannedAmount: 500.5 },
      token: 'token',
      requiresAuth: true,
    })
  })

  it('создаёт категорию без группы и без плана', async () => {
    request.mockResolvedValue({ id: 1 })

    await financeApi.createCategory('token', { type: 'INCOME', name: 'Зарплата' })

    expect(request).toHaveBeenCalledWith('/finances/categories', {
      method: 'POST',
      body: { type: 'INCOME', name: 'Зарплата', group: null, plannedAmount: null },
      token: 'token',
      requiresAuth: true,
    })
  })

  it('редактирует категорию через PUT с названием, группой и планом', async () => {
    request.mockResolvedValue({ id: 1 })

    await financeApi.updateCategory('token', 7, { name: 'Премия', group: 'Бонусы', plannedAmount: 300 })

    expect(request).toHaveBeenCalledWith('/finances/categories/7', {
      method: 'PUT',
      body: { name: 'Премия', group: 'Бонусы', plannedAmount: 300 },
      token: 'token',
      requiresAuth: true,
    })
  })

  it('редактирует категорию без плана, отправляя null', async () => {
    request.mockResolvedValue({ id: 1 })

    await financeApi.updateCategory('token', 7, { name: 'Премия', group: null })

    expect(request).toHaveBeenCalledWith('/finances/categories/7', {
      method: 'PUT',
      body: { name: 'Премия', group: null, plannedAmount: null },
      token: 'token',
      requiresAuth: true,
    })
  })

  it('удаляет категорию через DELETE', async () => {
    request.mockResolvedValue(null)

    await financeApi.removeCategory('token', 7)

    expect(request).toHaveBeenCalledWith('/finances/categories/7', {
      method: 'DELETE',
      token: 'token',
      requiresAuth: true,
    })
  })

  it('меняет порядок категорий типа через PUT', async () => {
    request.mockResolvedValue(null)

    await financeApi.reorderCategories('token', 'INCOME', [3, 1, 2])

    expect(request).toHaveBeenCalledWith('/finances/categories/order', {
      method: 'PUT',
      body: { type: 'INCOME', categoryIds: [3, 1, 2] },
      token: 'token',
      requiresAuth: true,
    })
  })

  it('запрашивает операции за дату с токеном', async () => {
    request.mockResolvedValue([])

    const result = await financeApi.listOperationsByDate('token', '2026-05-13')

    expect(request).toHaveBeenCalledWith('/finances/operations?date=2026-05-13', {
      token: 'token',
      requiresAuth: true,
    })
    expect(result).toEqual([])
  })

  it('запрашивает операции за диапазон с токеном', async () => {
    request.mockResolvedValue([])

    const result = await financeApi.listOperationsByRange('token', '2026-05-01', '2026-05-31')

    expect(request).toHaveBeenCalledWith('/finances/operations?from=2026-05-01&to=2026-05-31', {
      token: 'token',
      requiresAuth: true,
    })
    expect(result).toEqual([])
  })

  it('сохраняет операцию через PUT с категорией, датой и суммой', async () => {
    request.mockResolvedValue({ id: 1 })

    await financeApi.saveOperation('token', { categoryId: 12, date: '2026-05-13', amount: 100.5 })

    expect(request).toHaveBeenCalledWith('/finances/operations', {
      method: 'PUT',
      body: { categoryId: 12, date: '2026-05-13', amount: 100.5 },
      token: 'token',
      requiresAuth: true,
    })
  })

  it('удаляет операцию через DELETE', async () => {
    request.mockResolvedValue(null)

    await financeApi.removeOperation('token', 101)

    expect(request).toHaveBeenCalledWith('/finances/operations/101', {
      method: 'DELETE',
      token: 'token',
      requiresAuth: true,
    })
  })
})
