import { useEffect, useState } from 'react'
import {
  DEFAULT_MEDIA_STATUS,
  MEDIA_STATUS_OPTIONS,
} from './mediaOptions'

export default function MediaFormModal({
  title,
  submitLabel = 'Сохранить',
  initialValues,
  onSubmit,
  onClose,
}) {
  const [itemTitle, setItemTitle] = useState(initialValues?.title ?? '')
  const [description, setDescription] = useState(initialValues?.description ?? '')
  const [status, setStatus] = useState(initialValues?.status ?? DEFAULT_MEDIA_STATUS)
  const [titleError, setTitleError] = useState(null)
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

    if (itemTitle.trim() === '') {
      setTitleError('Укажите название.')
      setFormError(null)
      return
    }

    setTitleError(null)
    setFormError(null)
    setIsSubmitting(true)
    try {
      await onSubmit({
        title: itemTitle.trim(),
        description: description.trim(),
        status,
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
        aria-labelledby="media-form-modal-title"
        onClick={(event) => event.stopPropagation()}
      >
        <h2 id="media-form-modal-title" className="modal-title">
          {title}
        </h2>

        <form className="auth-form" onSubmit={handleSubmit} noValidate>
          <div className="auth-field">
            <label htmlFor="media-form-title">Название</label>
            <input
              id="media-form-title"
              name="title"
              type="text"
              maxLength={200}
              value={itemTitle}
              disabled={isSubmitting}
              aria-invalid={Boolean(titleError)}
              aria-describedby={titleError ? 'media-form-title-error' : undefined}
              onChange={(event) => setItemTitle(event.target.value)}
            />
            {titleError && (
              <p id="media-form-title-error" className="auth-field-error">
                {titleError}
              </p>
            )}
          </div>

          <div className="auth-field">
            <label htmlFor="media-form-description">Описание</label>
            <textarea
              id="media-form-description"
              name="description"
              rows={3}
              maxLength={2000}
              value={description}
              disabled={isSubmitting}
              onChange={(event) => setDescription(event.target.value)}
            />
          </div>

          <div className="auth-field">
            <label htmlFor="media-form-status">Статус</label>
            <select
              id="media-form-status"
              name="status"
              value={status}
              disabled={isSubmitting}
              onChange={(event) => setStatus(event.target.value)}
            >
              {MEDIA_STATUS_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
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
              {isSubmitting ? 'Сохранение…' : submitLabel}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
