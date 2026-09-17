import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  ApiError,
  NETWORK_ERROR_MESSAGE,
  SERVER_ERROR_MESSAGE,
  request,
  setUnauthorizedHandler,
} from '../../src/api/client'

function fakeResponse({ status = 200, body = null } = {}) {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => (body === null ? '' : JSON.stringify(body)),
  }
}

function mockFetch(response) {
  const fetchMock = vi.fn().mockResolvedValue(response)
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

afterEach(() => {
  setUnauthorizedHandler(null)
  vi.unstubAllGlobals()
})

describe('request', () => {
  it('отправляет метод, JSON-заголовки и тело и возвращает разобранный ответ', async () => {
    const fetchMock = mockFetch(fakeResponse({ status: 200, body: { accessToken: 'token' } }))

    const data = await request('/auth/login', { method: 'POST', body: { email: 'a@b.com', password: 'secret123' } })

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, options] = fetchMock.mock.calls[0]
    expect(url).toBe('/auth/login')
    expect(options.method).toBe('POST')
    expect(options.headers['Content-Type']).toBe('application/json')
    expect(options.headers.Accept).toBe('application/json')
    expect(options.body).toBe(JSON.stringify({ email: 'a@b.com', password: 'secret123' }))
    expect(data).toEqual({ accessToken: 'token' })
  })

  it('преобразует ответ об ошибке в ApiError со статусом и сообщением gateway', async () => {
    mockFetch(fakeResponse({ status: 409, body: { message: 'Email is already registered' } }))

    await expect(request('/auth/register', { method: 'POST', body: {} })).rejects.toMatchObject({
      status: 409,
      message: 'Email is already registered',
    })
    await expect(request('/auth/register', { method: 'POST', body: {} })).rejects.toBeInstanceOf(ApiError)
  })

  it('использует запасное сообщение для ошибки сервера без message', async () => {
    mockFetch(fakeResponse({ status: 503, body: null }))

    await expect(request('/auth/login', { method: 'POST', body: {} })).rejects.toMatchObject({
      status: 503,
      message: SERVER_ERROR_MESSAGE,
    })
  })

  it('преобразует сетевую ошибку в ApiError с сообщением о недоступности', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))

    await expect(request('/auth/login', { method: 'POST', body: {} })).rejects.toMatchObject({
      status: 0,
      message: NETWORK_ERROR_MESSAGE,
    })
  })

  it('добавляет заголовок Authorization при наличии токена', async () => {
    const fetchMock = mockFetch(fakeResponse({ status: 200, body: {} }))

    await request('/tracker/habits', { token: 'access-token' })

    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer access-token')
  })

  it('не добавляет заголовок Authorization без токена', async () => {
    const fetchMock = mockFetch(fakeResponse({ status: 200, body: {} }))

    await request('/auth/login', { method: 'POST', body: {} })

    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBeUndefined()
  })

  it('не отправляет защищённый запрос без токена', async () => {
    const fetchMock = mockFetch(fakeResponse({ status: 200, body: {} }))

    await expect(request('/tracker/habits', { requiresAuth: true, token: null })).rejects.toMatchObject({
      status: 401,
    })
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('сообщает о недействительном токене при 401 на защищённом запросе', async () => {
    mockFetch(fakeResponse({ status: 401, body: { message: 'authentication required' } }))
    const handler = vi.fn()
    setUnauthorizedHandler(handler)

    await expect(
      request('/tracker/habits', { requiresAuth: true, token: 'expired-token' }),
    ).rejects.toMatchObject({ status: 401 })

    expect(handler).toHaveBeenCalledTimes(1)
  })

  it('не сообщает о недействительном токене при 401 на публичном запросе', async () => {
    mockFetch(fakeResponse({ status: 401, body: { message: 'Invalid email or password' } }))
    const handler = vi.fn()
    setUnauthorizedHandler(handler)

    await expect(request('/auth/login', { method: 'POST', body: {} })).rejects.toMatchObject({ status: 401 })

    expect(handler).not.toHaveBeenCalled()
  })
})
