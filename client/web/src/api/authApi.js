import { request } from './client'

export const authApi = {
  register(email, password) {
    return request('/auth/register', { method: 'POST', body: { email, password } })
  },

  login(email, password) {
    return request('/auth/login', { method: 'POST', body: { email, password } })
  },
}
