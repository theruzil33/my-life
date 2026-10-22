import { useEffect, useState } from 'react'
import { TIME_OF_DAY_OPTIONS } from './habitOptions'

export default function TimeOfDayModal({
  initialTimeOfDay,
  onClose,
  onSubmit,
  title = 'Время суток',
}) {
  const [timeOfDay, setTimeOfDay] = useState(initialTimeOfDay ?? [])
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

  function toggle(value) {
    setTimeOfDay((current) =>
      current.includes(value) ? current.filter((slot) => slot !== value) : [...current, value],
    )
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (isSubmitting) {
      return
    }
    setFormError(null)
    setIsSubmitting(true)
    try {
      await onSubmit(timeOfDay)
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
        aria-labelledby="time-of-day-modal-title"
        onClick={(event) => event.stopPropagation()}
      >
        <h2 id="time-of-day-modal-title" className="modal-title">
          {title}
        </h2>

        <form className="auth-form" onSubmit={handleSubmit} noValidate>
          <fieldset className="auth-field tracker-fieldset">
            <legend>Части дня</legend>
            <div className="weekday-options">
              {TIME_OF_DAY_OPTIONS.map((option) => (
                <label key={option.value} className="weekday-option">
                  <input
                    type="checkbox"
                    name="timeOfDay"
                    value={option.value}
                    checked={timeOfDay.includes(option.value)}
                    disabled={isSubmitting}
                    onChange={() => toggle(option.value)}
                  />
                  <span>{option.label}</span>
                </label>
              ))}
            </div>
          </fieldset>

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
