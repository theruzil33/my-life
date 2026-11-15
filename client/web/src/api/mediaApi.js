import { request } from './client'

function withQuery(path, params) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.append(key, value)
    }
  })
  const query = search.toString()
  return query ? `${path}?${query}` : path
}

export const mediaApi = {
  list(token, type) {
    return request(withQuery('/media/items', { type }), { token, requiresAuth: true })
  },

  create(token, item) {
    return request('/media/items', {
      method: 'POST',
      body: {
        type: item.type,
        title: item.title,
        description: item.description ? item.description : null,
        status: item.status,
      },
      token,
      requiresAuth: true,
    })
  },

  update(token, id, item) {
    return request(`/media/items/${id}`, {
      method: 'PUT',
      body: {
        title: item.title,
        description: item.description ? item.description : null,
        status: item.status,
      },
      token,
      requiresAuth: true,
    })
  },

  remove(token, id) {
    return request(`/media/items/${id}`, {
      method: 'DELETE',
      token,
      requiresAuth: true,
    })
  },
}
