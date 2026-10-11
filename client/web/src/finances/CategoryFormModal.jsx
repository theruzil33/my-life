import { useEffect, useState } from 'react'
import { CATEGORY_TYPE_LABELS, CATEGORY_TYPE_ORDER } from './categoryOptions'

const ERROR_REQUIRED_TYPE = 'Выберите тип категории'
const ERROR_REQUIRED_NAME = 'Укажите название категории'

function validateCategoryForm({ type, name }) {
  const errors = {}
  if (type === '') {
    errors.type = ERROR_REQUIRED_TYPE
  }
  if (name.trim() === '') {
    errors.name = ERROR_REQUIRED_NAME
  }
  return errors
}

export default function CategoryFormModal({ onClose, onSubmit, groupNamesByType = {} }) {
  const [type, setType] = useState('')
  const [name, setName] = useState('')
  const [group, setGroup] = useState('')
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

    const errors = validateCategoryForm({ type, name })
    setFieldErrors(errors)
    setFormError(null)
    if (Object.keys(errors).length > 0) {
      return
    }

    setIsSubmitting(true)
    try {
      await onSubmit({ type, name: name.trim(), group: group.trim() })
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
        aria-labelledby="category-modal-title"
        onClick={(event) => event.stopPropagation()}
      >
        <h2 id="category-modal-title" className="modal-title">
          Новая категория
        </h2>

        <form className="auth-form" onSubmit={handleSubmit} noValidate>
          <div className="auth-field">
            <label htmlFor="category-type">Тип</label>
            <select
              id="category-type"
              name="type"
              value={type}
              disabled={isSubmitting}
              aria-invalid={Boolean(fieldErrors.type)}
              aria-describedby={fieldErrors.type ? 'category-type-error' : undefined}
              onChange={(event) => setType(event.target.value)}
            >
              <option value="">Выберите тип</option>
              {CATEGORY_TYPE_ORDER.map((option) => (
                <option key={option} value={option}>
                  {CATEGORY_TYPE_LABELS[option]}
                </option>
              ))}
            </select>
            {fieldErrors.type && (
              <p id="category-type-error" className="auth-field-error">
                {fieldErrors.type}
              </p>
            )}
          </div>

          <div className="auth-field">
            <label htmlFor="category-name">Название</label>
            <input
              id="category-name"
              name="name"
              type="text"
              value={name}
              disabled={isSubmitting}
              aria-invalid={Boolean(fieldErrors.name)}
              aria-describedby={fieldErrors.name ? 'category-name-error' : undefined}
              onChange={(event) => setName(event.target.value)}
            />
            {fieldErrors.name && (
              <p id="category-name-error" className="auth-field-error">
                {fieldErrors.name}
              </p>
            )}
          </div>

          <div className="auth-field">
            <label htmlFor="category-group">Группа</label>
            <input
              id="category-group"
              name="group"
              type="text"
              list="category-group-options"
              value={group}
              disabled={isSubmitting}
              onChange={(event) => setGroup(event.target.value)}
            />
            <datalist id="category-group-options">
              {(groupNamesByType[type] ?? []).map((option) => (
                <option key={option} value={option} />
              ))}
            </datalist>
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
              {isSubmitting ? 'Создание…' : 'Создать'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
