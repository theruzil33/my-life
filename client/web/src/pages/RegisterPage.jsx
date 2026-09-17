import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { registrationErrorMessage } from '../auth/errors'
import { hasValidationErrors, validateCredentials } from '../auth/validation'

export default function RegisterPage() {
  const { register } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [formError, setFormError] = useState(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isRegistered, setIsRegistered] = useState(false)

  async function handleSubmit(event) {
    event.preventDefault()
    if (isSubmitting) {
      return
    }

    const errors = validateCredentials({ email, password })
    setFieldErrors(errors)
    setFormError(null)
    if (hasValidationErrors(errors)) {
      return
    }

    setIsSubmitting(true)
    try {
      await register({ email, password })
      setIsRegistered(true)
    } catch (error) {
      setFormError(registrationErrorMessage(error))
    } finally {
      setIsSubmitting(false)
    }
  }

  if (isRegistered) {
    return (
      <section className="auth-page">
        <h1 className="auth-title">Регистрация завершена</h1>
        <p className="auth-success" role="status">
          Пользователь {email} успешно зарегистрирован.
        </p>
        <p className="auth-switch">
          <Link to="/login">Перейти ко входу</Link>
        </p>
      </section>
    )
  }

  return (
    <section className="auth-page">
      <h1 className="auth-title">Регистрация</h1>

      <form className="auth-form" onSubmit={handleSubmit} noValidate>
        <div className="auth-field">
          <label htmlFor="register-email">Email</label>
          <input
            id="register-email"
            name="email"
            type="email"
            autoComplete="email"
            value={email}
            disabled={isSubmitting}
            aria-invalid={Boolean(fieldErrors.email)}
            aria-describedby={fieldErrors.email ? 'register-email-error' : undefined}
            onChange={(event) => setEmail(event.target.value)}
          />
          {fieldErrors.email && (
            <p id="register-email-error" className="auth-field-error">
              {fieldErrors.email}
            </p>
          )}
        </div>

        <div className="auth-field">
          <label htmlFor="register-password">Пароль</label>
          <input
            id="register-password"
            name="password"
            type="password"
            autoComplete="new-password"
            value={password}
            disabled={isSubmitting}
            aria-invalid={Boolean(fieldErrors.password)}
            aria-describedby={fieldErrors.password ? 'register-password-error' : undefined}
            onChange={(event) => setPassword(event.target.value)}
          />
          {fieldErrors.password && (
            <p id="register-password-error" className="auth-field-error">
              {fieldErrors.password}
            </p>
          )}
        </div>

        {formError && (
          <p className="auth-form-error" role="alert">
            {formError}
          </p>
        )}

        <button type="submit" className="auth-submit" disabled={isSubmitting}>
          {isSubmitting ? 'Выполняется регистрация…' : 'Зарегистрироваться'}
        </button>
      </form>

      <p className="auth-switch">
        Уже есть аккаунт? <Link to="/login">Войти</Link>
      </p>
    </section>
  )
}
