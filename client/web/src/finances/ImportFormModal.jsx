import { useEffect, useState } from 'react'
import { financeApi } from '../api/financeApi'
import { useAuth } from '../auth/AuthContext'
import { currentMonthIso } from './dates'

const ERROR_NO_FILE = 'Выберите файл для импорта'
const ERROR_NO_MONTH = 'Выберите месяц и год'

export default function ImportFormModal({ onClose, onImported }) {
  const { accessToken } = useAuth()
  const [month, setMonth] = useState(currentMonthIso())
  const [file, setFile] = useState(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [result, setResult] = useState(null)
  const [formError, setFormError] = useState(null)

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
    setFormError(null)
    if (!file) {
      setFormError(ERROR_NO_FILE)
      return
    }
    if (month === '') {
      setFormError(ERROR_NO_MONTH)
      return
    }
    const [year, monthNumber] = month.split('-').map(Number)
    setIsSubmitting(true)
    try {
      const imported = await financeApi.importFile(accessToken, { year, month: monthNumber, file })
      setResult(imported)
      onImported(imported)
    } catch (submitError) {
      setFormError(submitError.message)
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="import-modal-title"
        onClick={(event) => event.stopPropagation()}
      >
        <h2 id="import-modal-title" className="modal-title">
          Импорт операций из файла
        </h2>

        <form className="auth-form" onSubmit={handleSubmit} noValidate>
          <div className="auth-field">
            <label htmlFor="import-month">Месяц и год</label>
            <input
              id="import-month"
              name="month"
              type="month"
              value={month}
              disabled={isSubmitting}
              onChange={(event) => setMonth(event.target.value)}
            />
          </div>

          <div className="auth-field">
            <label htmlFor="import-file">Файл CSV</label>
            <input
              id="import-file"
              name="file"
              type="file"
              accept=".csv,text/csv"
              disabled={isSubmitting}
              onChange={(event) => setFile(event.target.files?.[0] ?? null)}
            />
          </div>

          {formError && (
            <p className="auth-form-error" role="alert">
              {formError}
            </p>
          )}

          {result && (
            <p className="auth-form-success" role="status">
              Создано категорий: {result.createdCategories}. Записано операций: {result.savedOperations}.
            </p>
          )}

          <div className="modal-actions">
            <button type="button" className="modal-cancel" onClick={onClose} disabled={isSubmitting}>
              Отмена
            </button>
            <button type="submit" className="auth-submit" disabled={isSubmitting}>
              {isSubmitting ? 'Импорт…' : 'Импортировать'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
