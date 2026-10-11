import { useEffect, useState } from 'react'

const ERROR_REQUIRED = 'Укажите начальный баланс'
const ERROR_INVALID = 'Начальный баланс должен быть числом'
const ERROR_DECIMALS = 'Не более двух знаков после запятой'

function validate(raw) {
  const normalized = raw.trim().replace(',', '.')
  if (normalized === '') {
    return { error: ERROR_REQUIRED }
  }
  if (!/^-?\d+(\.\d*)?$/.test(normalized)) {
    return { error: ERROR_INVALID }
  }
  const decimals = (normalized.split('.')[1] ?? '').length
  if (decimals > 2) {
    return { error: ERROR_DECIMALS }
  }
  return { value: Number(normalized) }
}

function initialValue(startingBalance) {
  if (startingBalance === null || startingBalance === undefined) {
    return ''
  }
  return Number(startingBalance).toFixed(2)
}

export default function BalanceFormModal({ startingBalance, onClose, onSubmit }) {
  const [value, setValue] = useState(initialValue(startingBalance))
  const [error, setError] = useState(null)
  const [formError, setFormError] = useState(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  useEffect(() => {
    function handleKeyDown(event) {
      if (event.key === 'Escape') {
        onClose()
      }
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [onClose])

  async function handleSubmit(event) {
    event.preventDefault()
    if (isSubmitting) {
      return
    }

    const validation = validate(value)
    setError(null)
    setFormError(null)
    if (validation.error) {
      setError(validation.error)
      return
    }

    setIsSubmitting(true)
    try {
      await onSubmit(validation.value)
    } catch (submitError) {
      setFormError(submitError.message)
      setIsSubmitting(false)
    }
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="balance-modal-title"
        onClick={(event) => event.stopPropagation()}
      >
        <h2 id="balance-modal-title" className="modal-title">
          Начальный баланс
        </h2>

        <form className="auth-form" onSubmit={handleSubmit} noValidate>
          <div className="auth-field">
            <label htmlFor="balance-starting">Начальный баланс</label>
            <input
              id="balance-starting"
              name="startingBalance"
              type="text"
              inputMode="decimal"
              value={value}
              disabled={isSubmitting}
              aria-invalid={Boolean(error)}
              aria-describedby={error ? 'balance-starting-error' : undefined}
              onChange={(event) => setValue(event.target.value)}
            />
            {error && (
              <p id="balance-starting-error" className="auth-field-error">
                {error}
              </p>
            )}
          </div>

          {formError && (
            <p className="auth-form-error" role="alert">
              {formError}
            </p>
          )}

          <div className="modal-actions">
            <button type="button" className="modal-cancel" onClick={onClose} disabled={isSubmitting}>
              Отмена
            </button>
            <button type="submit" className="auth-submit" disabled={isSubmitting}>
              {isSubmitting ? 'Сохранение…' : 'Сохранить'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
