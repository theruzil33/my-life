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

const MISSING_TOKEN_MESSAGE = 'Для выполнения запроса требуется авторизация.'

let unauthorizedHandler = null

export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler
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
  if (data && typeof data.message === 'string' && data.message.trim() !== '') {
    return data.message
  }
  if (status >= 500) {
    return SERVER_ERROR_MESSAGE
  }
  return DEFAULT_ERROR_MESSAGE
}

export async function request(path, { method = 'GET', body, token, requiresAuth = false } = {}) {
  if (requiresAuth && !token) {
    throw new ApiError(MISSING_TOKEN_MESSAGE, 401)
  }

  const headers = { Accept: 'application/json' }
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  let response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(NETWORK_ERROR_MESSAGE, 0)
  }

  const data = await readBody(response)

  if (!response.ok) {
    if (requiresAuth && response.status === 401) {
      notifyUnauthorized()
    }
    throw new ApiError(errorMessageFor(response.status, data), response.status)
  }

  return data
}
