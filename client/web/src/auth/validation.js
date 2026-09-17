export const MINIMUM_PASSWORD_LENGTH = 8

const EMAIL_PATTERN = /^[^@\s]+@[^@\s]+\.[^@\s]+$/

export function validateEmail(email) {
  if (!email || email.trim() === '') {
    return 'Введите email'
  }
  if (!EMAIL_PATTERN.test(email.trim())) {
    return 'Введите корректный email'
  }
  return null
}

export function validatePassword(password) {
  if (!password) {
    return 'Введите пароль'
  }
  if (password.length < MINIMUM_PASSWORD_LENGTH) {
    return `Пароль должен содержать не менее ${MINIMUM_PASSWORD_LENGTH} символов`
  }
  return null
}

export function validateCredentials({ email, password }) {
  return {
    email: validateEmail(email),
    password: validatePassword(password),
  }
}

export function hasValidationErrors(errors) {
  return Object.values(errors).some((error) => Boolean(error))
}
