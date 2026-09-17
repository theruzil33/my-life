import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  ApiError,
  CSRF_ERROR_MESSAGE,
  NETWORK_ERROR_MESSAGE,
  SERVER_ERROR_MESSAGE,
  clearCsrfToken,
  request,
  setSessionRefresher,
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

function clearCookies() {
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'
}

beforeEach(() => {
  clearCookies()
})

afterEach(() => {
  clearCsrfToken()
  setUnauthorizedHandler(null)
  setSessionRefresher(null)
  clearCookies()
  vi.unstubAllGlobals()
})

describe('request', () => {
  it('отправляет метод, JSON-заголовки и тело и возвращает разобранный ответ', async () => {
    document.cookie = 'XSRF-TOKEN=csrf-token; path=/'
    const fetchMock = mockFetch(fakeResponse({ status: 200, body: { accessToken: 'token' } }))

    const data = await request('/auth/login', { method: 'POST', body: { email: 'a@b.com', password: 'secret123' } })

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, options] = fetchMock.mock.calls[0]
    expect(url).toBe('/auth/login')
    expect(options.method).toBe('POST')
    expect(options.credentials).toBe('include')
    expect(options.headers['Content-Type']).toBe('application/json')
    expect(options.headers.Accept).toBe('application/json')
    expect(options.body).toBe(JSON.stringify({ email: 'a@b.com', password: 'secret123' }))
    expect(data).toEqual({ accessToken: 'token' })
  })

  it('добавляет CSRF-заголовок к изменяющему запросу из cookie', async () => {
    document.cookie = 'XSRF-TOKEN=csrf-token; path=/'
    const fetchMock = mockFetch(fakeResponse({ status: 200, body: {} }))

    await request('/auth/refresh', { method: 'POST' })

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock.mock.calls[0][1].headers['X-XSRF-TOKEN']).toBe('csrf-token')
  })

  it('запрашивает CSRF-токен до первого изменяющего запроса', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(fakeResponse({ status: 200, body: { token: 'fresh-csrf' } }))
      .mockResolvedValueOnce(fakeResponse({ status: 200, body: {} }))
    vi.stubGlobal('fetch', fetchMock)

    await request('/auth/login', { method: 'POST', body: {} })

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(fetchMock.mock.calls[0][0]).toBe('/auth/csrf')
    expect(fetchMock.mock.calls[1][1].headers['X-XSRF-TOKEN']).toBe('fresh-csrf')
  })

  it('не добавляет CSRF-заголовок к безопасным запросам', async () => {
    document.cookie = 'XSRF-TOKEN=csrf-token; path=/'
    const fetchMock = mockFetch(fakeResponse({ status: 200, body: {} }))

    await request('/auth/csrf')

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock.mock.calls[0][1].headers['X-XSRF-TOKEN']).toBeUndefined()
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

  it('показывает понятное сообщение при отклонении запроса по CSRF', async () => {
    document.cookie = 'XSRF-TOKEN=stale; path=/'
    mockFetch(fakeResponse({ status: 403, body: {} }))

    await expect(request('/auth/login', { method: 'POST', body: {} })).rejects.toMatchObject({
      status: 403,
      message: CSRF_ERROR_MESSAGE,
    })
  })

  it('использует актуальный CSRF-cookie после его ротации сервером', async () => {
    document.cookie = 'XSRF-TOKEN=first; path=/'
    const fetchMock = mockFetch(fakeResponse({ status: 200, body: {} }))

    await request('/tracker/habits', { method: 'POST', token: 'access-token', requiresAuth: true })
    expect(fetchMock.mock.calls[0][1].headers['X-XSRF-TOKEN']).toBe('first')

    document.cookie = 'XSRF-TOKEN=second; path=/'
    await request('/tracker/habits', { method: 'POST', token: 'access-token', requiresAuth: true })

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(fetchMock.mock.calls[1][1].headers['X-XSRF-TOKEN']).toBe('second')
  })

  it('повторно получает CSRF-токен после отклонения по CSRF', async () => {
    document.cookie = 'XSRF-TOKEN=stale; path=/'
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(fakeResponse({ status: 403, body: {} }))
      .mockResolvedValueOnce(fakeResponse({ status: 200, body: { token: 'renewed-csrf' } }))
      .mockResolvedValueOnce(fakeResponse({ status: 200, body: {} }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(request('/auth/login', { method: 'POST', body: {} })).rejects.toMatchObject({ status: 403 })
    await request('/auth/login', { method: 'POST', body: {} })

    expect(fetchMock.mock.calls[1][0]).toBe('/auth/csrf')
    expect(fetchMock.mock.calls[2][1].headers['X-XSRF-TOKEN']).toBe('renewed-csrf')
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

  it('обновляет сессию и повторяет защищённый запрос при 401', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(fakeResponse({ status: 401, body: { message: 'auth' } }))
      .mockResolvedValueOnce(fakeResponse({ status: 200, body: { items: [] } }))
    vi.stubGlobal('fetch', fetchMock)
    const refresher = vi.fn().mockResolvedValue('new-token')
    setSessionRefresher(refresher)

    const data = await request('/tracker/habits', { requiresAuth: true, token: 'old-token' })

    expect(data).toEqual({ items: [] })
    expect(refresher).toHaveBeenCalledTimes(1)
    expect(fetchMock.mock.calls[1][1].headers.Authorization).toBe('Bearer new-token')
  })

  it('завершает сессию, если обновить её не удалось', async () => {
    mockFetch(fakeResponse({ status: 401, body: { message: 'auth' } }))
    const refresher = vi.fn().mockRejectedValue(new ApiError('no refresh', 401))
    const handler = vi.fn()
    setSessionRefresher(refresher)
    setUnauthorizedHandler(handler)

    await expect(request('/tracker/habits', { requiresAuth: true, token: 'expired' })).rejects.toMatchObject({
      status: 401,
    })

    expect(handler).toHaveBeenCalledTimes(1)
  })

  it('использует одно обновление сессии для параллельных запросов', async () => {
    let refreshCount = 0
    const fetchMock = vi.fn().mockImplementation((url, options) => {
      if (options?.headers?.Authorization === 'Bearer new-token') {
        return Promise.resolve(fakeResponse({ status: 200, body: { ok: true } }))
      }
      return Promise.resolve(fakeResponse({ status: 401, body: { message: 'auth' } }))
    })
    vi.stubGlobal('fetch', fetchMock)
    setSessionRefresher(async () => {
      refreshCount += 1
      return 'new-token'
    })

    await Promise.all([
      request('/tracker/a', { requiresAuth: true, token: 'expired' }),
      request('/tracker/b', { requiresAuth: true, token: 'expired' }),
    ])

    expect(refreshCount).toBe(1)
  })

  it('не сообщает о недействительном токене при 401 на публичном запросе', async () => {
    mockFetch(fakeResponse({ status: 401, body: { message: 'Invalid email or password' } }))
    const handler = vi.fn()
    setUnauthorizedHandler(handler)

    await expect(request('/auth/login', { method: 'POST', body: {} })).rejects.toMatchObject({ status: 401 })

    expect(handler).not.toHaveBeenCalled()
  })
})
