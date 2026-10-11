import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { NETWORK_ERROR_MESSAGE, clearCsrfToken } from '../../src/api/client'
import { financeApi } from '../../src/api/financeApi'

function fakeResponse({ status = 200, body = null } = {}) {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => (body === null ? '' : JSON.stringify(body)),
  }
}

function csvFile() {
  return new File(['Статьи расходов'], 'Деньги.csv', { type: 'text/csv' })
}

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=csrf-token; path=/'
})

afterEach(() => {
  clearCsrfToken()
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'
  vi.unstubAllGlobals()
})

describe('financeApi.importFile', () => {
  it('отправляет multipart с файлом, месяцем, годом, токеном и CSRF-заголовком', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(fakeResponse({ status: 201, body: { createdCategories: 1, savedOperations: 2 } }))
    vi.stubGlobal('fetch', fetchMock)
    const file = csvFile()

    const result = await financeApi.importFile('access-token', { year: 2026, month: 5, file })

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, options] = fetchMock.mock.calls[0]
    expect(url).toBe('/finances/imports')
    expect(options.method).toBe('POST')
    expect(options.credentials).toBe('include')
    expect(options.headers.Authorization).toBe('Bearer access-token')
    expect(options.headers['X-XSRF-TOKEN']).toBe('csrf-token')
    expect(options.headers['Content-Type']).toBeUndefined()
    expect(options.body).toBeInstanceOf(FormData)
    expect(options.body.get('file')).toBe(file)
    expect(options.body.get('year')).toBe('2026')
    expect(options.body.get('month')).toBe('5')
    expect(result).toEqual({ createdCategories: 1, savedOperations: 2 })
  })

  it('преобразует ошибку сервера в ApiError с сообщением', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(fakeResponse({ status: 400, body: { message: 'Файл не содержит статей' } })))

    await expect(financeApi.importFile('access-token', { year: 2026, month: 5, file: csvFile() })).rejects.toMatchObject({
      status: 400,
      message: 'Файл не содержит статей',
    })
  })

  it('преобразует сетевую ошибку в понятное сообщение', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))

    await expect(financeApi.importFile('access-token', { year: 2026, month: 5, file: csvFile() })).rejects.toMatchObject({
      status: 0,
      message: NETWORK_ERROR_MESSAGE,
    })
  })
})
