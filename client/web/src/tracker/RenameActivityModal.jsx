import { useEffect, useState } from 'react'
import { hasValidationErrors, validateActivityName } from './validation'

export default function RenameActivityModal({
  initialName,
  onClose,
  onSubmit,
  title = 'Переименовать привычку',
  entityLabel = 'привычки',
}) {
  const [name, setName] = useState(initialName ?? '')
  const [fieldErrors, setFieldErrors] = useState({})
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

    const errors = validateActivityName(name, entityLabel)
    setFieldErrors(errors)
    setFormError(null)
    if (hasValidationErrors(errors)) {
      return
    }

    setIsSubmitting(true)
    try {
      await onSubmit(name.trim())
    } catch (error) {
      setFormError(error.message)
      setIsSubmitting(false)
    }
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="rename-modal-title"
        onClick={(event) => event.stopPropagation()}
      >
        <h2 id="rename-modal-title" className="modal-title">
          {title}
        </h2>

        <form className="auth-form" onSubmit={handleSubmit} noValidate>
          <div className="auth-field">
            <label htmlFor="rename-name">Название</label>
            <input
              id="rename-name"
              name="name"
              type="text"
              value={name}
              disabled={isSubmitting}
              aria-invalid={Boolean(fieldErrors.name)}
              aria-describedby={fieldErrors.name ? 'rename-name-error' : undefined}
              onChange={(event) => setName(event.target.value)}
            />
            {fieldErrors.name && (
              <p id="rename-name-error" className="auth-field-error">
                {fieldErrors.name}
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
              {isSubmitting ? 'Переименование…' : 'Переименовать'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
