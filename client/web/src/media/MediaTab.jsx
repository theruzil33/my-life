import { useEffect, useState } from 'react'
import { mediaApi } from '../api/mediaApi'
import { useAuth } from '../auth/AuthContext'
import MediaFormModal from './MediaFormModal'
import { MEDIA_STATUS_OPTIONS } from './mediaOptions'

export default function MediaTab({ type, addLabel, emptyMessage, modalTitle, editTitle }) {
  const { accessToken } = useAuth()
  const [items, setItems] = useState(null)
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState(null)
  const [actionError, setActionError] = useState(null)
  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [editTarget, setEditTarget] = useState(null)
  const [deleteTarget, setDeleteTarget] = useState(null)
  const [isDeleting, setIsDeleting] = useState(false)
  const [pendingStatusId, setPendingStatusId] = useState(null)
  const [reloadCounter, setReloadCounter] = useState(0)

  const idPrefix = `media-${type.toLowerCase()}`

  useEffect(() => {
    let cancelled = false
    mediaApi
      .list(accessToken, type)
      .then((data) => {
        if (!cancelled) {
          setItems(data)
        }
      })
      .catch((error) => {
        if (!cancelled) {
          setLoadError(error.message)
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
  }, [accessToken, type, reloadCounter])

  function reload() {
    setActionError(null)
    setLoadError(null)
    setIsLoading(true)
    setReloadCounter((counter) => counter + 1)
  }

  async function handleCreate(values) {
    const created = await mediaApi.create(accessToken, { ...values, type })
    setItems((list) => [...(list ?? []), created])
    setIsCreateOpen(false)
  }

  async function handleEdit(values) {
    const target = editTarget
    const updated = await mediaApi.update(accessToken, target.id, values)
    setItems((list) => (list ?? []).map((item) => (item.id === target.id ? updated : item)))
    setEditTarget(null)
  }

  async function handleStatusChange(item, nextStatus) {
    if (nextStatus === item.status) {
      return
    }
    const previousStatus = item.status
    setActionError(null)
    setPendingStatusId(item.id)
    setItems((list) =>
      (list ?? []).map((entry) => (entry.id === item.id ? { ...entry, status: nextStatus } : entry)),
    )
    try {
      const updated = await mediaApi.update(accessToken, item.id, {
        title: item.title,
        description: item.description ?? '',
        status: nextStatus,
      })
      setItems((list) => (list ?? []).map((entry) => (entry.id === item.id ? updated : entry)))
    } catch (error) {
      setActionError(error.message)
      setItems((list) =>
        (list ?? []).map((entry) =>
          entry.id === item.id ? { ...entry, status: previousStatus } : entry,
        ),
      )
    } finally {
      setPendingStatusId(null)
    }
  }

  async function handleDeleteConfirm() {
    const target = deleteTarget
    setActionError(null)
    setIsDeleting(true)
    try {
      await mediaApi.remove(accessToken, target.id)
      setItems((list) => (list ?? []).filter((item) => item.id !== target.id))
      setDeleteTarget(null)
    } catch (error) {
      setActionError(error.message)
      setDeleteTarget(null)
    } finally {
      setIsDeleting(false)
    }
  }

  return (
    <div className="media-tab">
      <div className="media-toolbar">
        <button type="button" className="auth-submit" onClick={() => setIsCreateOpen(true)}>
          {addLabel}
        </button>
      </div>

      {actionError && (
        <p className="tracker-error" role="alert">
          {actionError}
        </p>
      )}

      {isLoading && <p className="tracker-status">Загрузка…</p>}

      {!isLoading && loadError && (
        <div className="media-load-error">
          <p className="tracker-error" role="alert">
            {loadError}
          </p>
          <button type="button" className="habit-action" onClick={reload}>
            Повторить
          </button>
        </div>
      )}

      {!isLoading && !loadError && items && items.length === 0 && (
        <p className="tracker-empty">{emptyMessage}</p>
      )}

      {!isLoading && !loadError && items && items.length > 0 && (
        <ul className="media-list">
          {items.map((item) => (
            <li key={item.id} className="media-item">
              <div className="media-item-main">
                <span className="media-title">{item.title}</span>
                <div className="media-actions">
                  <button
                    type="button"
                    className="habit-action"
                    aria-label={`Редактировать ${item.title}`}
                    disabled={isDeleting && deleteTarget?.id === item.id}
                    onClick={() => {
                      setActionError(null)
                      setEditTarget(item)
                    }}
                  >
                    Редактировать
                  </button>
                  <button
                    type="button"
                    className="habit-action"
                    aria-label={`Удалить ${item.title}`}
                    disabled={isDeleting && deleteTarget?.id === item.id}
                    onClick={() => {
                      setActionError(null)
                      setDeleteTarget(item)
                    }}
                  >
                    Удалить
                  </button>
                </div>
              </div>

              {item.description && <p className="media-description">{item.description}</p>}

              <div className="media-status">
                <span className="media-status-label">Статус</span>
                <select
                  className="media-status-select"
                  aria-label={`Статус ${item.title}`}
                  value={item.status}
                  disabled={pendingStatusId === item.id}
                  onChange={(event) => handleStatusChange(item, event.target.value)}
                >
                  {MEDIA_STATUS_OPTIONS.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </div>
            </li>
          ))}
        </ul>
      )}

      {isCreateOpen && (
        <MediaFormModal
          title={modalTitle}
          submitLabel="Добавить"
          onClose={() => setIsCreateOpen(false)}
          onSubmit={handleCreate}
        />
      )}

      {editTarget && (
        <MediaFormModal
          title={editTitle}
          submitLabel="Сохранить"
          initialValues={editTarget}
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
            aria-labelledby={`${idPrefix}-delete-modal-title`}
            onClick={(event) => event.stopPropagation()}
          >
            <h2 id={`${idPrefix}-delete-modal-title`} className="modal-title">
              Удалить «{deleteTarget.title}»?
            </h2>
            <p>Запись будет удалена безвозвратно.</p>
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
