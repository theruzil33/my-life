import { useEffect, useState } from 'react'
import { CATEGORY_TYPE_LABELS } from './categoryOptions'
import { initialPlannedAmount, parsePlannedAmount } from './plannedAmount'

const ERROR_REQUIRED_NAME = 'Укажите название категории'

export default function EditCategoryModal({ category, groupNamesByType = {}, onClose, onSubmit }) {
  const [name, setName] = useState(category.name)
  const [group, setGroup] = useState(category.group ?? '')
  const [plannedAmount, setPlannedAmount] = useState(initialPlannedAmount(category.plannedAmount))
  const [planError, setPlanError] = useState(null)
  const [fieldError, setFieldError] = useState(null)
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

    const plan = parsePlannedAmount(plannedAmount)
    if (name.trim() === '' || plan.error) {
      setFieldError(name.trim() === '' ? ERROR_REQUIRED_NAME : null)
      setPlanError(plan.error ?? null)
      setFormError(null)
      return
    }

    setFieldError(null)
    setPlanError(null)
    setFormError(null)
    setIsSubmitting(true)
    try {
      await onSubmit({ name: name.trim(), group: group.trim(), plannedAmount: plan.value })
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
        aria-labelledby="edit-category-modal-title"
        onClick={(event) => event.stopPropagation()}
      >
        <h2 id="edit-category-modal-title" className="modal-title">
          Редактировать категорию
        </h2>

        <form className="auth-form" onSubmit={handleSubmit} noValidate>
          <div className="auth-field">
            <label htmlFor="edit-category-type">Тип</label>
            <input id="edit-category-type" name="type" type="text" value={CATEGORY_TYPE_LABELS[category.type]} disabled />
          </div>

          <div className="auth-field">
            <label htmlFor="edit-category-name">Название</label>
            <input
              id="edit-category-name"
              name="name"
              type="text"
              value={name}
              disabled={isSubmitting}
              aria-invalid={Boolean(fieldError)}
              aria-describedby={fieldError ? 'edit-category-name-error' : undefined}
              onChange={(event) => setName(event.target.value)}
            />
            {fieldError && (
              <p id="edit-category-name-error" className="auth-field-error">
                {fieldError}
              </p>
            )}
          </div>

          <div className="auth-field">
            <label htmlFor="edit-category-group">Группа</label>
            <input
              id="edit-category-group"
              name="group"
              type="text"
              list="edit-category-group-options"
              value={group}
              disabled={isSubmitting}
              onChange={(event) => setGroup(event.target.value)}
            />
            <datalist id="edit-category-group-options">
              {(groupNamesByType[category.type] ?? []).map((option) => (
                <option key={option} value={option} />
              ))}
            </datalist>
          </div>

          <div className="auth-field">
            <label htmlFor="edit-category-planned">Плановая сумма в месяц</label>
            <input
              id="edit-category-planned"
              name="plannedAmount"
              type="text"
              inputMode="decimal"
              value={plannedAmount}
              disabled={isSubmitting}
              aria-invalid={Boolean(planError)}
              aria-describedby={planError ? 'edit-category-planned-error' : undefined}
              onChange={(event) => setPlannedAmount(event.target.value)}
            />
            {planError && (
              <p id="edit-category-planned-error" className="auth-field-error">
                {planError}
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
