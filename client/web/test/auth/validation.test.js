import { describe, expect, it } from 'vitest'
import { hasValidationErrors, MINIMUM_PASSWORD_LENGTH, validateCredentials, validateEmail, validatePassword } from '../../src/auth/validation'

describe('validateEmail', () => {
  it('принимает корректный email', () => {
    expect(validateEmail('user@example.com')).toBeNull()
  })

  it('отклоняет пустой email', () => {
    expect(validateEmail('')).toBe('Введите email')
  })

  it('отклоняет email без @', () => {
    expect(validateEmail('user.example.com')).toBe('Введите корректный email')
  })

  it('отклоняет email без домена', () => {
    expect(validateEmail('user@example')).toBe('Введите корректный email')
  })
})

describe('validatePassword', () => {
  it('отклоняет пустой пароль', () => {
    expect(validatePassword('')).toBe('Введите пароль')
  })

  it('отклоняет пароль короче минимальной длины', () => {
    expect(validatePassword('1234567')).toBe(
      `Пароль должен содержать не менее ${MINIMUM_PASSWORD_LENGTH} символов`,
    )
  })

  it('принимает пароль минимальной длины', () => {
    expect(validatePassword('12345678')).toBeNull()
  })
})

describe('validateCredentials', () => {
  it('возвращает ошибки для обоих полей', () => {
    const errors = validateCredentials({ email: '', password: '123' })

    expect(hasValidationErrors(errors)).toBe(true)
    expect(errors.email).toBe('Введите email')
    expect(errors.password).toBe('Пароль должен содержать не менее 8 символов')
  })

  it('не возвращает ошибок для корректных данных', () => {
    const errors = validateCredentials({ email: 'user@example.com', password: '12345678' })

    expect(hasValidationErrors(errors)).toBe(false)
  })
})
