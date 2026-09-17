import { ApiError } from '../api/client'

export const INVALID_CREDENTIALS_MESSAGE = 'Неверный email или пароль'
export const EMAIL_ALREADY_REGISTERED_MESSAGE = 'Пользователь с таким email уже зарегистрирован'
export const GENERIC_REQUEST_ERROR_MESSAGE = 'Не удалось выполнить запрос. Попробуйте позже.'

function statusOf(error) {
  return error instanceof ApiError ? error.status : undefined
}

function fallbackMessage(error) {
  return typeof error?.message === 'string' && error.message !== ''
    ? error.message
    : GENERIC_REQUEST_ERROR_MESSAGE
}

export function loginErrorMessage(error) {
  if (statusOf(error) === 401) {
    return INVALID_CREDENTIALS_MESSAGE
  }
  return fallbackMessage(error)
}

export function registrationErrorMessage(error) {
  if (statusOf(error) === 409) {
    return EMAIL_ALREADY_REGISTERED_MESSAGE
  }
  return fallbackMessage(error)
}
