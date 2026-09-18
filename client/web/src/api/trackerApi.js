import { request } from './client'

function withQuery(path, params) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, value)
    }
  })
  const serialized = search.toString()
  return serialized === '' ? path : `${path}?${serialized}`
}

export const trackerApi = {
  listOnDate(token, date) {
    return request(withQuery('/tracker/habits', { date }), { token, requiresAuth: true })
  },

  history(token, from, to) {
    return request(withQuery('/tracker/habits/history', { from, to }), { token, requiresAuth: true })
  },

  create(token, { name, startDate, frequency, weekdays }) {
    const body = { name, startDate, frequency }
    if (frequency === 'SPECIFIC_WEEKDAYS') {
      body.weekdays = weekdays ?? []
    }
    return request('/tracker/habits', { method: 'POST', body, token, requiresAuth: true })
  },

  markCompleted(token, habitId, date) {
    return request(`/tracker/habits/${habitId}/completions/${date}`, {
      method: 'PUT',
      token,
      requiresAuth: true,
    })
  },

  unmarkCompleted(token, habitId, date) {
    return request(`/tracker/habits/${habitId}/completions/${date}`, {
      method: 'DELETE',
      token,
      requiresAuth: true,
    })
  },
}
