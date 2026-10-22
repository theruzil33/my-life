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

function activityBody({ name, startDate, frequency, weekdays, type, timeOfDay, markPastCompleted }) {
  const body = { name, startDate, frequency, type, timeOfDay: timeOfDay ?? [] }
  if (frequency === 'SPECIFIC_WEEKDAYS') {
    body.weekdays = weekdays ?? []
  }
  if (markPastCompleted !== undefined && markPastCompleted !== null) {
    body.markPastCompleted = markPastCompleted
  }
  return body
}

export const trackerApi = {
  listOnDate(token, date, type) {
    return request(withQuery('/tracker/habits', { date, type }), { token, requiresAuth: true })
  },

  history(token, from, to) {
    return request(withQuery('/tracker/habits/history', { from, to }), { token, requiresAuth: true })
  },

  create(token, definition) {
    return request('/tracker/habits', {
      method: 'POST',
      body: activityBody(definition),
      token,
      requiresAuth: true,
    })
  },

  update(token, id, definition) {
    return request(`/tracker/habits/${id}`, {
      method: 'PUT',
      body: activityBody(definition),
      token,
      requiresAuth: true,
    })
  },

  remove(token, id) {
    return request(`/tracker/habits/${id}`, {
      method: 'DELETE',
      token,
      requiresAuth: true,
    })
  },

  markCompleted(token, habitId, date, timeOfDay) {
    return request(withQuery(`/tracker/habits/${habitId}/completions/${date}`, { timeOfDay }), {
      method: 'PUT',
      token,
      requiresAuth: true,
    })
  },

  unmarkCompleted(token, habitId, date, timeOfDay) {
    return request(withQuery(`/tracker/habits/${habitId}/completions/${date}`, { timeOfDay }), {
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
    return request('/tracker/imports', {
      method: 'POST',
      body: formData,
      token,
      requiresAuth: true,
    })
  },
}
