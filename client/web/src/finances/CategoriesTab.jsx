import { useEffect, useState } from 'react'
import { financeApi } from '../api/financeApi'
import { useAuth } from '../auth/AuthContext'
import CategoryFormModal from './CategoryFormModal'
import EditCategoryModal from './EditCategoryModal'
import { CATEGORY_TYPE, CATEGORY_TYPE_LABELS, CATEGORY_TYPE_ORDER, categoryTypeClass } from './categoryOptions'

function buildBlocks(categories) {
  const blocks = []
  for (const type of CATEGORY_TYPE_ORDER) {
    const groups = new Map()
    for (const category of categories) {
      if (category.type !== type) {
        continue
      }
      if (category.group) {
        let block = groups.get(category.group)
        if (!block) {
          block = { key: `group-${type}-${category.group}`, group: category.group, type, categories: [] }
          groups.set(category.group, block)
          blocks.push(block)
        }
        block.categories.push(category)
      } else {
        blocks.push({ key: `category-${category.id}`, group: null, type, categories: [category] })
      }
    }
  }
  return blocks
}

function formatPlan(plannedAmount) {
  if (plannedAmount === null || plannedAmount === undefined) {
    return null
  }
  return Number(plannedAmount).toFixed(2)
}

function collectGroupNames(categories) {
  const result = { [CATEGORY_TYPE.INCOME]: [], [CATEGORY_TYPE.EXPENSE]: [] }
  if (!categories) {
    return result
  }
  for (const category of categories) {
    const names = result[category.type]
    if (category.group && names && !names.includes(category.group)) {
      names.push(category.group)
    }
  }
  return result
}

function applyOrder(categories, type, orderedIds) {
  const byId = new Map(categories.map((category) => [category.id, category]))
  const ordered = orderedIds.map((id) => byId.get(id)).filter(Boolean)
  const result = []
  let inserted = false
  for (const category of categories) {
    if (category.type === type) {
      if (!inserted) {
        result.push(...ordered)
        inserted = true
      }
    } else {
      result.push(category)
    }
  }
  if (!inserted) {
    result.push(...ordered)
  }
  return result
}

function blockLabel(block) {
  return block.group ?? block.categories[0].name
}

export default function CategoriesTab() {
  const { accessToken } = useAuth()
  const [categories, setCategories] = useState(null)
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState(null)
  const [actionError, setActionError] = useState(null)
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [editTarget, setEditTarget] = useState(null)
  const [deleteTarget, setDeleteTarget] = useState(null)
  const [isDeleting, setIsDeleting] = useState(false)
  const [isReordering, setIsReordering] = useState(false)
  const [reloadCounter, setReloadCounter] = useState(0)

  useEffect(() => {
    let cancelled = false
    financeApi
      .listCategories(accessToken)
      .then((data) => {
        if (!cancelled) {
          setCategories(data)
          setLoadError(null)
        }
      })
      .catch((error) => {
        if (!cancelled) {
          setLoadError(error.message)
          setCategories(null)
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
  }, [accessToken, reloadCounter])

  async function handleCreate(values) {
    await financeApi.createCategory(accessToken, values)
    setIsModalOpen(false)
    setLoadError(null)
    setIsLoading(true)
    setReloadCounter((counter) => counter + 1)
  }

  async function handleEdit(values) {
    await financeApi.updateCategory(accessToken, editTarget.id, values)
    setEditTarget(null)
    setLoadError(null)
    setIsLoading(true)
    setReloadCounter((counter) => counter + 1)
  }

  async function handleDeleteConfirm() {
    const target = deleteTarget
    setActionError(null)
    setIsDeleting(true)
    try {
      await financeApi.removeCategory(accessToken, target.id)
      setCategories((list) => list.filter((item) => item.id !== target.id))
      setDeleteTarget(null)
    } catch (error) {
      setActionError(error.message)
      setDeleteTarget(null)
    } finally {
      setIsDeleting(false)
    }
  }

  const blocks = categories ? buildBlocks(categories) : []
  const groupNamesByType = collectGroupNames(categories)
  const firstExpenseBlockIndex = blocks.findIndex((block) => block.type === CATEGORY_TYPE.EXPENSE)

  async function moveBlock(block, direction) {
    const typeBlocks = blocks.filter((item) => item.type === block.type)
    const index = typeBlocks.findIndex((item) => item.key === block.key)
    const target = direction === 'up' ? index - 1 : index + 1
    if (target < 0 || target >= typeBlocks.length) {
      return
    }
    const reordered = [...typeBlocks]
    ;[reordered[index], reordered[target]] = [reordered[target], reordered[index]]
    const orderedIds = reordered.flatMap((item) => item.categories.map((category) => category.id))

    setActionError(null)
    setIsReordering(true)
    try {
      await financeApi.reorderCategories(accessToken, block.type, orderedIds)
      setCategories((current) => applyOrder(current, block.type, orderedIds))
    } catch (error) {
      setActionError(error.message)
    } finally {
      setIsReordering(false)
    }
  }

  return (
    <div className="finances-tab">
      <button type="button" className="auth-submit" onClick={() => setIsModalOpen(true)}>
        Добавить категорию
      </button>

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
        <div className="categories-groups">
          {blocks.map((block, index) => {
            const typeBlocks = blocks.filter((item) => item.type === block.type)
            const typeIndex = typeBlocks.findIndex((item) => item.key === block.key)
            const label = blockLabel(block)
            return (
              <section
                key={block.key}
                className={`category-group ${categoryTypeClass(block.type)}${
                  block.group ? ' category-group-framed' : ''
                }${index === firstExpenseBlockIndex && firstExpenseBlockIndex > 0 ? ' category-group-start' : ''}`}
              >
                <div className="category-group-header">
                  {block.group && <h3 className="category-group-title">{block.group}</h3>}
                  <div className="category-group-move">
                    <button
                      type="button"
                      className="habit-action category-move"
                      aria-label={`Переместить «${label}» выше`}
                      disabled={typeIndex <= 0 || isReordering}
                      onClick={() => moveBlock(block, 'up')}
                    >
                      ↑
                    </button>
                    <button
                      type="button"
                      className="habit-action category-move"
                      aria-label={`Переместить «${label}» ниже`}
                      disabled={typeIndex >= typeBlocks.length - 1 || isReordering}
                      onClick={() => moveBlock(block, 'down')}
                    >
                      ↓
                    </button>
                  </div>
                </div>
                <ul className="categories-list">
                  {block.categories.map((category) => {
                    const planned = formatPlan(category.plannedAmount)
                    return (
                      <li key={category.id} className={`category-item ${categoryTypeClass(category.type)}`}>
                        <span className="category-name">{category.name}</span>
                        <span className="category-type">{CATEGORY_TYPE_LABELS[category.type]}</span>
                        <div className="category-actions">
                          <button
                            type="button"
                            className="habit-action"
                            aria-label={`Редактировать ${category.name}`}
                            onClick={() => {
                              setActionError(null)
                              setEditTarget(category)
                            }}
                          >
                            Редактировать
                          </button>
                          <button
                            type="button"
                            className="habit-action"
                            aria-label={`Удалить ${category.name}`}
                            disabled={isDeleting && deleteTarget?.id === category.id}
                            onClick={() => {
                              setActionError(null)
                              setDeleteTarget(category)
                            }}
                          >
                            Удалить
                          </button>
                        </div>
                        {planned !== null && <span className="category-plan">{planned}</span>}
                      </li>
                    )
                  })}
                </ul>
              </section>
            )
          })}
        </div>
      )}

      {isModalOpen && (
        <CategoryFormModal
          groupNamesByType={groupNamesByType}
          onClose={() => setIsModalOpen(false)}
          onSubmit={handleCreate}
        />
      )}

      {editTarget && (
        <EditCategoryModal
          category={editTarget}
          groupNamesByType={groupNamesByType}
          onClose={() => setEditTarget(null)}
          onSubmit={handleEdit}
        />
      )}

      {deleteTarget && (
        <div className="modal-overlay" onClick={() => !isDeleting && setDeleteTarget(null)}>
          <div
            className="modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="delete-category-modal-title"
            onClick={(event) => event.stopPropagation()}
          >
            <h2 id="delete-category-modal-title" className="modal-title">
              Удалить «{deleteTarget.name}»?
            </h2>
            <p>Категория будет удалена безвозвратно.</p>
            <div className="modal-actions">
              <button
                type="button"
                className="modal-cancel"
                onClick={() => setDeleteTarget(null)}
                disabled={isDeleting}
              >
                Отмена
              </button>
              <button type="button" className="auth-submit" onClick={handleDeleteConfirm} disabled={isDeleting}>
                {isDeleting ? 'Удаление…' : 'Удалить'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
