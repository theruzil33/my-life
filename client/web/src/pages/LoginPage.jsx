import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { loginErrorMessage } from '../auth/errors'
import { hasValidationErrors, validateCredentials } from '../auth/validation'

export default function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [formError, setFormError] = useState(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

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
      await login({ email, password })
      navigate('/', { replace: true })
    } catch (error) {
      setFormError(loginErrorMessage(error))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <section className="auth-page">
      <h1 className="auth-title">Вход</h1>

      <form className="auth-form" onSubmit={handleSubmit} noValidate>
        <div className="auth-field">
          <label htmlFor="login-email">Email</label>
          <input
            id="login-email"
            name="email"
            type="email"
            autoComplete="email"
            value={email}
            disabled={isSubmitting}
            aria-invalid={Boolean(fieldErrors.email)}
            aria-describedby={fieldErrors.email ? 'login-email-error' : undefined}
            onChange={(event) => setEmail(event.target.value)}
          />
          {fieldErrors.email && (
            <p id="login-email-error" className="auth-field-error">
              {fieldErrors.email}
            </p>
          )}
        </div>

        <div className="auth-field">
          <label htmlFor="login-password">Пароль</label>
          <input
            id="login-password"
            name="password"
            type="password"
            autoComplete="current-password"
            value={password}
            disabled={isSubmitting}
            aria-invalid={Boolean(fieldErrors.password)}
            aria-describedby={fieldErrors.password ? 'login-password-error' : undefined}
            onChange={(event) => setPassword(event.target.value)}
          />
          {fieldErrors.password && (
            <p id="login-password-error" className="auth-field-error">
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
          {isSubmitting ? 'Выполняется вход…' : 'Войти'}
        </button>
      </form>

      <p className="auth-switch">
        Нет аккаунта? <Link to="/register">Зарегистрироваться</Link>
      </p>
    </section>
  )
}
