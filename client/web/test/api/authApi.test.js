import { afterEach, describe, expect, it, vi } from 'vitest'
import { authApi } from '../../src/api/authApi'

function fakeResponse({ status = 200, body = null } = {}) {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => (body === null ? '' : JSON.stringify(body)),
  }
}

afterEach(() => {
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

  it('выполняет вход через POST /auth/login и возвращает { accessToken, tokenType, expiresIn }', async () => {
    const tokenResponse = { accessToken: 'jwt-token', tokenType: 'Bearer', expiresIn: 3600 }
    const fetchMock = vi.fn().mockResolvedValue(fakeResponse({ status: 200, body: tokenResponse }))
    vi.stubGlobal('fetch', fetchMock)

    const result = await authApi.login('a@b.com', 'secret123')

    const [url, options] = fetchMock.mock.calls[0]
    expect(url).toBe('/auth/login')
    expect(options.method).toBe('POST')
    expect(JSON.parse(options.body)).toEqual({ email: 'a@b.com', password: 'secret123' })
    expect(result).toEqual(tokenResponse)
  })
})
