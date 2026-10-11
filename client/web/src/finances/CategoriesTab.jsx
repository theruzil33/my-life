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
          {blocks.map((block, index) => (
            <section
              key={block.key}
              className={`category-group ${categoryTypeClass(block.type)}${
                block.group ? ' category-group-framed' : ''
              }${index === firstExpenseBlockIndex && firstExpenseBlockIndex > 0 ? ' category-group-start' : ''}`}
            >
              {block.group && <h3 className="category-group-title">{block.group}</h3>}
              <ul className="categories-list">
                {block.categories.map((category) => (
                  <li key={category.id} className={`category-item ${categoryTypeClass(category.type)}`}>
                    <span className="category-name">{category.name}</span>
                    <span className="category-type">{CATEGORY_TYPE_LABELS[category.type]}</span>
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
                  </li>
                ))}
              </ul>
            </section>
          ))}
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
