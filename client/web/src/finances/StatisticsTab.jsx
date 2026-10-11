import { Fragment, useEffect, useState } from 'react'
import { financeApi } from '../api/financeApi'
import { useAuth } from '../auth/AuthContext'
import { currentMonthIso, eachIsoDate, formatDayLabel, monthRange } from './dates'

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

function hasPlan(plannedAmount) {
  return plannedAmount !== null && plannedAmount !== undefined
}

export default function StatisticsTab({ isActive = true, refreshToken = 0 }) {
  const { accessToken } = useAuth()
  const initialMonth = currentMonthIso()
  const [applied, setApplied] = useState(initialMonth)
  const [draft, setDraft] = useState(initialMonth)
  const [categories, setCategories] = useState(null)
  const [operations, setOperations] = useState(null)
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState(null)
  const [monthError, setMonthError] = useState(null)

  useEffect(() => {
    if (!isActive) {
      return
    }
    const { from, to } = monthRange(applied)
    let cancelled = false
    Promise.resolve()
      .then(() => {
        if (!cancelled) {
          setIsLoading(true)
        }
      })
      .then(() => Promise.all([financeApi.listCategories(accessToken), financeApi.listOperationsByRange(accessToken, from, to)]))
      .then(([categoryList, operationList]) => {
        if (!cancelled) {
          setCategories(categoryList)
          setOperations(operationList)
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
    // перечитывание при открытии вкладки (isActive), смене месяца (applied) и после импорта (refreshToken)
  }, [accessToken, isActive, applied, refreshToken])

  function handleApply(event) {
    event.preventDefault()
    setMonthError(null)
    if (draft === '') {
      setMonthError('Выберите месяц и год')
      return
    }
    setLoadError(null)
    setApplied(draft)
  }

  const { from, to } = monthRange(applied)
  const columns = eachIsoDate(from, to)
  const blocks = categories ? buildBlocks(categories) : []
  const operationByCell = new Map()
  const typeByCategoryId = new Map()
  const spentByCategoryId = new Map()
  for (const category of categories ?? []) {
    typeByCategoryId.set(category.id, category.type)
  }
  let incomeTotal = 0
  let expenseTotal = 0
  for (const operation of operations ?? []) {
    operationByCell.set(`${operation.categoryId}|${operation.date}`, operation)
    spentByCategoryId.set(
      operation.categoryId,
      (spentByCategoryId.get(operation.categoryId) ?? 0) + Number(operation.amount),
    )
    const type = typeByCategoryId.get(operation.categoryId)
    if (type === 'INCOME') {
      incomeTotal += Number(operation.amount)
    } else if (type === 'EXPENSE') {
      expenseTotal += Number(operation.amount)
    }
  }

  return (
    <div className="finances-tab">
      <form className="stats-controls" onSubmit={handleApply} noValidate>
        <div className="auth-field">
          <label htmlFor="finance-stats-month">Месяц и год</label>
          <input
            id="finance-stats-month"
            name="month"
            type="month"
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
          />
        </div>
        <button type="submit" className="auth-submit">
          Применить
        </button>
      </form>

      {monthError && (
        <p className="tracker-error" role="alert">
          {monthError}
        </p>
      )}

      {loadError && (
        <p className="tracker-error" role="alert">
          {loadError}
        </p>
      )}

      {isLoading && <p className="tracker-status">Загрузка…</p>}

      {!isLoading && blocks.length > 0 && (
        <div className="stats-totals">
          <span className="stats-total stats-total-income">Приход: {formatAmount(incomeTotal)}</span>
          <span className="stats-total stats-total-expense">Расход: {formatAmount(expenseTotal)}</span>
        </div>
      )}

      {!isLoading && categories && categories.length === 0 && (
        <p className="tracker-empty">У вас пока нет категорий.</p>
      )}

      {!isLoading && blocks.length > 0 && (
        <div className="stats-scroll">
          <table className="stats-matrix finance-stats-matrix">
            <thead>
              <tr>
                <th scope="col" colSpan={2} className="stats-habit-name">
                  Категория
                </th>
                <th scope="col" className="stats-plan-column">
                  План
                </th>
                <th scope="col" className="stats-delta-column">
                  Дельта
                </th>
                {columns.map((column) => (
                  <th key={column} scope="col" className="stats-day-label" title={column}>
                    {formatDayLabel(column)}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {blocks.map((block) => (
                <Fragment key={block.key}>
                  {block.categories.map((category, index) => {
                    const planned = hasPlan(category.plannedAmount) ? formatAmount(category.plannedAmount) : ''
                    const delta = hasPlan(category.plannedAmount)
                      ? formatAmount(Number(category.plannedAmount) - (spentByCategoryId.get(category.id) ?? 0))
                      : ''
                    return (
                      <tr key={category.id} className={`operation-row-${category.type.toLowerCase()}`}>
                        {block.group && index === 0 && (
                          <th scope="rowgroup" rowSpan={block.categories.length} className="finance-stats-group">
                            {block.group}
                          </th>
                        )}
                        <th scope="row" colSpan={block.group ? 1 : 2} className="stats-habit-name">
                          {category.name}
                        </th>
                        <td className="stats-cell finance-stats-plan">{planned}</td>
                        <td className="stats-cell finance-stats-delta">{delta}</td>
                        {columns.map((column) => {
                          const operation = operationByCell.get(`${category.id}|${column}`)
                          return (
                            <td key={column} className="stats-cell finance-stats-cell">
                              {operation ? formatAmount(operation.amount) : ''}
                            </td>
                          )
                        })}
                      </tr>
                    )
                  })}
                </Fragment>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
