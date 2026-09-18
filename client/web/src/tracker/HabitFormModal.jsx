import { useEffect, useState } from 'react'
import { todayIso } from './dates'
import { FREQUENCY, FREQUENCY_OPTIONS, WEEKDAY_OPTIONS } from './habitOptions'
import { hasValidationErrors, validateHabitForm } from './validation'

export default function HabitFormModal({ onClose, onSubmit }) {
  const [name, setName] = useState('')
  const [startDate, setStartDate] = useState(todayIso())
  const [frequency, setFrequency] = useState(FREQUENCY.DAILY)
  const [weekdays, setWeekdays] = useState([])
  const [fieldErrors, setFieldErrors] = useState({})
  const [formError, setFormError] = useState(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const today = todayIso()

  useEffect(() => {
    function handleKeyDown(event) {
      if (event.key === 'Escape') {
        onClose()
      }
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [onClose])

  function toggleWeekday(value) {
    setWeekdays((current) =>
      current.includes(value) ? current.filter((day) => day !== value) : [...current, value],
    )
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (isSubmitting) {
      return
    }

    const errors = validateHabitForm({ name, startDate, frequency, weekdays })
    setFieldErrors(errors)
    setFormError(null)
    if (hasValidationErrors(errors)) {
      return
    }

    setIsSubmitting(true)
    try {
      await onSubmit({
        name: name.trim(),
        startDate,
        frequency,
        weekdays: frequency === FREQUENCY.SPECIFIC_WEEKDAYS ? weekdays : [],
      })
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
        aria-labelledby="habit-modal-title"
        onClick={(event) => event.stopPropagation()}
      >
        <h2 id="habit-modal-title" className="modal-title">
          Новая привычка
        </h2>

        <form className="auth-form" onSubmit={handleSubmit} noValidate>
          <div className="auth-field">
            <label htmlFor="habit-name">Название</label>
            <input
              id="habit-name"
              name="name"
              type="text"
              value={name}
              disabled={isSubmitting}
              aria-invalid={Boolean(fieldErrors.name)}
              aria-describedby={fieldErrors.name ? 'habit-name-error' : undefined}
              onChange={(event) => setName(event.target.value)}
            />
            {fieldErrors.name && (
              <p id="habit-name-error" className="auth-field-error">
                {fieldErrors.name}
              </p>
            )}
          </div>

          <div className="auth-field">
            <label htmlFor="habit-start-date">Дата начала</label>
            <input
              id="habit-start-date"
              name="startDate"
              type="date"
              value={startDate}
              min={today}
              disabled={isSubmitting}
              aria-invalid={Boolean(fieldErrors.startDate)}
              aria-describedby={fieldErrors.startDate ? 'habit-start-date-error' : undefined}
              onChange={(event) => setStartDate(event.target.value)}
            />
            {fieldErrors.startDate && (
              <p id="habit-start-date-error" className="auth-field-error">
                {fieldErrors.startDate}
              </p>
            )}
          </div>

          <fieldset className="auth-field tracker-fieldset">
            <legend>Периодичность</legend>
            {FREQUENCY_OPTIONS.map((option) => (
              <label key={option.value} className="radio-option">
                <input
                  type="radio"
                  name="frequency"
                  value={option.value}
                  checked={frequency === option.value}
                  disabled={isSubmitting}
                  onChange={() => setFrequency(option.value)}
                />
                <span>{option.label}</span>
              </label>
            ))}
            {fieldErrors.frequency && <p className="auth-field-error">{fieldErrors.frequency}</p>}
          </fieldset>

          {frequency === FREQUENCY.SPECIFIC_WEEKDAYS && (
            <fieldset className="auth-field tracker-fieldset">
              <legend>Дни недели</legend>
              <div className="weekday-options">
                {WEEKDAY_OPTIONS.map((option) => (
                  <label key={option.value} className="weekday-option">
                    <input
                      type="checkbox"
                      name="weekdays"
                      value={option.value}
                      checked={weekdays.includes(option.value)}
                      disabled={isSubmitting}
                      onChange={() => toggleWeekday(option.value)}
                    />
                    <span>{option.fullLabel}</span>
                  </label>
                ))}
              </div>
            </fieldset>
          )}

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
              {isSubmitting ? 'Создание…' : 'Создать'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
