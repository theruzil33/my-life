export class ApiError extends Error {
  constructor(message, status) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

export const NETWORK_ERROR_MESSAGE =
  'Не удалось выполнить запрос. Проверьте подключение и попробуйте снова.'
export const SERVER_ERROR_MESSAGE = 'Сервис временно недоступен. Попробуйте позже.'
export const DEFAULT_ERROR_MESSAGE = 'Не удалось выполнить запрос.'
export const CSRF_ERROR_MESSAGE = 'Не удалось подтвердить безопасность запроса. Попробуйте снова.'

const MISSING_TOKEN_MESSAGE = 'Для выполнения запроса требуется авторизация.'

const CSRF_COOKIE_NAME = 'XSRF-TOKEN'
const CSRF_HEADER_NAME = 'X-XSRF-TOKEN'
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])

let unauthorizedHandler = null
let sessionRefresher = null
let cachedCsrfToken = null
let csrfNeedsRefresh = false
let refreshPromise = null

export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler
}

export function setSessionRefresher(refresher) {
  sessionRefresher = refresher
}

function notifyUnauthorized() {
  if (unauthorizedHandler) {
    unauthorizedHandler()
  }
}

async function readBody(response) {
  const text = await response.text()
  if (text === '') {
    return null
  }
  try {
    return JSON.parse(text)
  } catch {
    return text
  }
}

function errorMessageFor(status, data) {
  if (status === 403) {
    return CSRF_ERROR_MESSAGE
  }
  if (data && typeof data.message === 'string' && data.message.trim() !== '') {
    return data.message
  }
  if (status >= 500) {
    return SERVER_ERROR_MESSAGE
  }
  return DEFAULT_ERROR_MESSAGE
}

export function readCsrfCookie() {
  if (typeof document === 'undefined' || typeof document.cookie !== 'string') {
    return null
  }
  const entry = document.cookie.split('; ').find((row) => row.startsWith(`${CSRF_COOKIE_NAME}=`))
  return entry ? decodeURIComponent(entry.slice(CSRF_COOKIE_NAME.length + 1)) : null
}

async function ensureCsrfToken() {
  if (!csrfNeedsRefresh) {
    const fromCookie = readCsrfCookie()
    if (fromCookie) {
      cachedCsrfToken = fromCookie
      return cachedCsrfToken
    }
    if (cachedCsrfToken) {
      return cachedCsrfToken
    }
  }
  try {
    const response = await fetch(`${API_BASE_URL}/auth/csrf`, {
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    const data = response.ok ? await readBody(response) : null
    cachedCsrfToken = typeof data?.token === 'string' ? data.token : readCsrfCookie()
  } catch {
    cachedCsrfToken = readCsrfCookie()
  }
  csrfNeedsRefresh = false
  return cachedCsrfToken
}

export function clearCsrfToken() {
  cachedCsrfToken = null
  csrfNeedsRefresh = false
}

function invalidateCsrfToken() {
  cachedCsrfToken = null
  csrfNeedsRefresh = true
}

function refreshSession() {
  if (!sessionRefresher) {
    return Promise.resolve(null)
  }
  if (!refreshPromise) {
    refreshPromise = Promise.resolve()
      .then(() => sessionRefresher())
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}

export async function request(
  path,
  { method = 'GET', body, token, requiresAuth = false, retry = true } = {},
) {
  if (requiresAuth && !token) {
    throw new ApiError(MISSING_TOKEN_MESSAGE, 401)
  }

  const isMutating = !SAFE_METHODS.has(method.toUpperCase())
  const isFormData = typeof FormData !== 'undefined' && body instanceof FormData
  const headers = { Accept: 'application/json' }
  if (body !== undefined && !isFormData) {
    headers['Content-Type'] = 'application/json'
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  if (isMutating) {
    const csrfToken = await ensureCsrfToken()
    if (csrfToken) {
      headers[CSRF_HEADER_NAME] = csrfToken
    }
  }

  let response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers,
      credentials: 'include',
      body: body === undefined ? undefined : isFormData ? body : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(NETWORK_ERROR_MESSAGE, 0)
  }

  const data = await readBody(response)

  if (!response.ok) {
    if (isMutating && response.status === 403) {
      invalidateCsrfToken()
    }

    if (requiresAuth && response.status === 401) {
      if (retry) {
        let refreshedToken
        try {
          refreshedToken = await refreshSession()
        } catch {
          refreshedToken = null
        }
        if (refreshedToken) {
          return request(path, { method, body, token: refreshedToken, requiresAuth, retry: false })
        }
      }
      notifyUnauthorized()
    }

    throw new ApiError(errorMessageFor(response.status, data), response.status)
  }

  return data
}
