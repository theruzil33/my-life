import { request } from './client'

export const financeApi = {
  getBalance(token) {
    return request('/finances/balance', { token, requiresAuth: true })
  },

  updateBalance(token, startingBalance) {
    return request('/finances/balance', {
      method: 'PUT',
      body: { startingBalance },
      token,
      requiresAuth: true,
    })
  },

  listCategories(token) {
    return request('/finances/categories', { token, requiresAuth: true })
  },

  createCategory(token, category) {
    return request('/finances/categories', {
      method: 'POST',
      body: {
        type: category.type,
        name: category.name,
        group: category.group ? category.group : null,
        plannedAmount: category.plannedAmount ?? null,
      },
      token,
      requiresAuth: true,
    })
  },

  updateCategory(token, id, category) {
    return request(`/finances/categories/${id}`, {
      method: 'PUT',
      body: {
        name: category.name,
        group: category.group ? category.group : null,
        plannedAmount: category.plannedAmount ?? null,
      },
      token,
      requiresAuth: true,
    })
  },

  removeCategory(token, id) {
    return request(`/finances/categories/${id}`, {
      method: 'DELETE',
      token,
      requiresAuth: true,
    })
  },

  reorderCategories(token, type, categoryIds) {
    return request('/finances/categories/order', {
      method: 'PUT',
      body: { type, categoryIds },
      token,
      requiresAuth: true,
    })
  },

  listOperationsByDate(token, date) {
    return request(`/finances/operations?date=${date}`, { token, requiresAuth: true })
  },

  listOperationsByRange(token, from, to) {
    return request(`/finances/operations?from=${from}&to=${to}`, { token, requiresAuth: true })
  },

  saveOperation(token, operation) {
    return request('/finances/operations', {
      method: 'PUT',
      body: { categoryId: operation.categoryId, date: operation.date, amount: operation.amount },
      token,
      requiresAuth: true,
    })
  },

  removeOperation(token, id) {
    return request(`/finances/operations/${id}`, {
      method: 'DELETE',
      token,
      requiresAuth: true,
    })
  },

  importFile(token, { year, month, file }) {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('year', year)
    formData.append('month', month)
    return request('/finances/imports', {
      method: 'POST',
      body: formData,
      token,
      requiresAuth: true,
    })
  },
}
