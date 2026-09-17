import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { authApi } from '../../src/api/authApi'
import { clearCsrfToken } from '../../src/api/client'

function fakeResponse({ status = 200, body = null } = {}) {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => (body === null ? '' : JSON.stringify(body)),
  }
}

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=csrf-token; path=/'
})

afterEach(() => {
  clearCsrfToken()
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'
  vi.unstubAllGlobals()
})

describe('authApi', () => {
  it('регистрирует пользователя через POST /auth/register и возвращает { id, email }', async () => {
    const fetchMock = vi.fn().mockResolvedValue(fakeResponse({ status: 201, body: { id: 1, email: 'a@b.com' } }))
    vi.stubGlobal('fetch', fetchMock)

    const result = await authApi.register('a@b.com', 'secret123')

    const [url, options] = fetchMock.mock.calls[0]
    expect(url).toBe('/auth/register')
    expect(options.method).toBe('POST')
    expect(JSON.parse(options.body)).toEqual({ email: 'a@b.com', password: 'secret123' })
    expect(result).toEqual({ id: 1, email: 'a@b.com' })
  })

  it('выполняет вход через POST /auth/login и возвращает access-токен и email', async () => {
    const tokenResponse = { accessToken: 'jwt-token', tokenType: 'Bearer', expiresIn: 900, email: 'a@b.com' }
    const fetchMock = vi.fn().mockResolvedValue(fakeResponse({ status: 200, body: tokenResponse }))
    vi.stubGlobal('fetch', fetchMock)

    const result = await authApi.login('a@b.com', 'secret123')

    const [url, options] = fetchMock.mock.calls[0]
    expect(url).toBe('/auth/login')
    expect(options.method).toBe('POST')
    expect(JSON.parse(options.body)).toEqual({ email: 'a@b.com', password: 'secret123' })
    expect(result).toEqual(tokenResponse)
  })

  it('обновляет сессию через POST /auth/refresh', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(fakeResponse({ status: 200, body: { accessToken: 'jwt', email: 'a@b.com' } }))
    vi.stubGlobal('fetch', fetchMock)

    await authApi.refresh()

    expect(fetchMock.mock.calls[0][0]).toBe('/auth/refresh')
    expect(fetchMock.mock.calls[0][1].method).toBe('POST')
  })

  it('завершает сессию через POST /auth/logout', async () => {
    const fetchMock = vi.fn().mockResolvedValue(fakeResponse({ status: 204, body: null }))
    vi.stubGlobal('fetch', fetchMock)

    await authApi.logout()

    expect(fetchMock.mock.calls[0][0]).toBe('/auth/logout')
    expect(fetchMock.mock.calls[0][1].method).toBe('POST')
  })

  it('запрашивает CSRF-токен через GET /auth/csrf', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(fakeResponse({ status: 200, body: { token: 'csrf', headerName: 'X-XSRF-TOKEN' } }))
    vi.stubGlobal('fetch', fetchMock)

    const result = await authApi.csrf()

    expect(fetchMock.mock.calls[0][0]).toBe('/auth/csrf')
    expect(result).toEqual({ token: 'csrf', headerName: 'X-XSRF-TOKEN' })
  })
})
