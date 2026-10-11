import { useEffect, useState } from 'react'
import { financeApi } from '../api/financeApi'
import { useAuth } from '../auth/AuthContext'
import { todayIso } from './dates'

const TYPE_ORDER = { EXPENSE: 0, INCOME: 1 }

function orderCategories(categories) {
  return [...categories].sort(
    (first, second) => (TYPE_ORDER[first.type] ?? 2) - (TYPE_ORDER[second.type] ?? 2),
  )
}

function buildBlocks(categories) {
  const blocks = []
  const groupIndex = new Map()
  for (const category of orderCategories(categories)) {
    if (category.group) {
      const key = `${category.type}-${category.group}`
      let block = groupIndex.get(key)
      if (!block) {
        block = { key, type: category.type, group: category.group, categories: [] }
        groupIndex.set(key, block)
        blocks.push(block)
      }
      block.categories.push(category)
    } else {
      blocks.push({ key: `category-${category.id}`, type: category.type, group: null, categories: [category] })
    }
  }
  return blocks
}

function formatAmount(value) {
  return Number(value).toFixed(2)
}

function buildAmounts(categories, operations) {
  const amounts = {}
  for (const category of categories) {
    amounts[category.id] = ''
  }
  for (const operation of operations) {
    if (amounts[operation.categoryId] !== undefined) {
      amounts[operation.categoryId] = formatAmount(operation.amount)
    }
  }
  return amounts
}

function validateAmount(raw) {
  const normalized = raw.trim().replace(',', '.')
  if (normalized === '') {
    return { error: 'Укажите сумму' }
  }
  if (!/^\d+(\.\d*)?$/.test(normalized)) {
    return { error: 'Сумма должна быть числом' }
  }
  const value = Number(normalized)
  if (!(value > 0)) {
    return { error: 'Сумма должна быть больше нуля' }
  }
  const decimals = (normalized.split('.')[1] ?? '').length
  if (decimals > 2) {
    return { error: 'Не более двух знаков после запятой' }
  }
  return { value }
}

export default function OperationsTab({ isActive = true, onOperationSaved }) {
  const { accessToken } = useAuth()
  const [date, setDate] = useState(todayIso())
  const [categories, setCategories] = useState(null)
  const [operations, setOperations] = useState(null)
  const [amounts, setAmounts] = useState({})
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState(null)
  const [actionError, setActionError] = useState(null)
  const [savingIds, setSavingIds] = useState([])
  const [deleteTarget, setDeleteTarget] = useState(null)
  const [isDeleting, setIsDeleting] = useState(false)

  useEffect(() => {
    if (!isActive) {
      return
    }
    let cancelled = false
    Promise.resolve()
      .then(() => {
        if (!cancelled) {
          setIsLoading(true)
        }
      })
      .then(() =>
        Promise.all([
          financeApi.listCategories(accessToken),
          financeApi.listOperationsByDate(accessToken, date),
        ]),
      )
      .then(([categoryList, operationList]) => {
        if (!cancelled) {
          setCategories(categoryList)
          setOperations(operationList)
          setAmounts(buildAmounts(categoryList, operationList))
          setLoadError(null)
        }
      })
      .catch((error) => {
        if (!cancelled) {
          setLoadError(error.message)
          setCategories(null)
          setOperations(null)
        }
      })
      .finally(() => {
        if (!cancelled) {
          setIsLoading(false)
        }
      })
    return () => {
      cancelled = true
    }
    // перечитывание при открытии вкладки (isActive) и при смене даты (date)
  }, [accessToken, isActive, date])

  function currentOperation(categoryId) {
    return (operations ?? []).find((operation) => operation.categoryId === categoryId) ?? null
  }

  async function saveAmount(category, value) {
    setActionError(null)
    setSavingIds((ids) => [...ids, category.id])
    try {
      const saved = await financeApi.saveOperation(accessToken, {
        categoryId: category.id,
        date,
        amount: value,
      })
      setOperations((list) => [
        ...(list ?? []).filter((operation) => operation.categoryId !== category.id),
        saved,
      ])
      setAmounts((current) => ({ ...current, [category.id]: formatAmount(saved.amount) }))
      onOperationSaved?.()
    } catch (error) {
      setActionError(error.message)
      const existing = currentOperation(category.id)
      setAmounts((current) => ({
        ...current,
        [category.id]: existing ? formatAmount(existing.amount) : '',
      }))
    } finally {
      setSavingIds((ids) => ids.filter((id) => id !== category.id))
    }
  }

  function commit(category) {
    const raw = amounts[category.id] ?? ''
    const normalized = raw.trim().replace(',', '.')
    const existing = currentOperation(category.id)

    if (normalized === '') {
      if (existing) {
        setActionError(null)
        setDeleteTarget({ category, operation: existing })
      }
      return
    }

    const validation = validateAmount(raw)
    if (validation.error) {
      setActionError(validation.error)
      return
    }

    if (existing && Number(existing.amount) === validation.value) {
      setAmounts((current) => ({ ...current, [category.id]: formatAmount(validation.value) }))
      return
    }

    saveAmount(category, validation.value)
  }

  function cancelDelete() {
    const target = deleteTarget
    setDeleteTarget(null)
    setAmounts((current) => ({ ...current, [target.category.id]: formatAmount(target.operation.amount) }))
  }

  async function confirmDelete() {
    const target = deleteTarget
    setActionError(null)
    setIsDeleting(true)
    try {
      await financeApi.removeOperation(accessToken, target.operation.id)
      setOperations((list) => (list ?? []).filter((operation) => operation.id !== target.operation.id))
      setAmounts((current) => ({ ...current, [target.category.id]: '' }))
      setDeleteTarget(null)
      onOperationSaved?.()
    } catch (error) {
      setActionError(error.message)
      setAmounts((current) => ({ ...current, [target.category.id]: formatAmount(target.operation.amount) }))
      setDeleteTarget(null)
    } finally {
      setIsDeleting(false)
    }
  }

  const blocks = categories ? buildBlocks(categories) : []

  function renderRow(category) {
    return (
      <li key={category.id} className={`operation-row operation-row-${category.type.toLowerCase()}`}>
        <span className="operation-name">{category.name}</span>
        <input
          type="text"
          inputMode="decimal"
          className="operation-amount"
          aria-label={`Сумма ${category.name}`}
          value={amounts[category.id] ?? ''}
          disabled={savingIds.includes(category.id)}
          onChange={(event) => setAmounts((current) => ({ ...current, [category.id]: event.target.value }))}
          onBlur={() => commit(category)}
          onKeyDown={(event) => {
            if (event.key === 'Enter') {
              event.preventDefault()
              commit(category)
            }
          }}
        />
      </li>
    )
  }

  return (
    <div className="finances-tab">
      <div className="auth-field operations-date">
        <label htmlFor="operations-date">Дата</label>
        <input
          id="operations-date"
          name="date"
          type="date"
          value={date}
          onChange={(event) => setDate(event.target.value)}
        />
      </div>

      {actionError && (
        <p className="tracker-error" role="alert">
          {actionError}
        </p>
      )}

      {isLoading && <p className="tracker-status">Загрузка…</p>}

      {!isLoading && loadError && (
        <p className="tracker-error" role="alert">
          {loadError}
        </p>
      )}

      {!isLoading && categories && categories.length === 0 && (
        <p className="tracker-empty">У вас пока нет категорий.</p>
      )}

      {!isLoading && blocks.length > 0 && (
        <div className="operations-blocks">
          {blocks.map((block) => (
            <section
              key={block.key}
              className={`category-group operation-group${block.group ? ' category-group-framed' : ''}`}
            >
              {block.group && <h3 className="category-group-title">{block.group}</h3>}
              <ul className="operations-list">{block.categories.map((category) => renderRow(category))}</ul>
            </section>
          ))}
        </div>
      )}

      {deleteTarget && (
        <div className="modal-overlay" onClick={() => !isDeleting && cancelDelete()}>
          <div
            className="modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="delete-operation-modal-title"
            onClick={(event) => event.stopPropagation()}
          >
            <h2 id="delete-operation-modal-title" className="modal-title">
              Удалить операцию «{deleteTarget.category.name}»?
            </h2>
            <p>Операция будет удалена безвозвратно.</p>
            <div className="modal-actions">
              <button type="button" className="modal-cancel" onClick={cancelDelete} disabled={isDeleting}>
                Отмена
              </button>
              <button type="button" className="auth-submit" onClick={confirmDelete} disabled={isDeleting}>
                {isDeleting ? 'Удаление…' : 'Удалить'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
