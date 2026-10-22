import { useEffect, useState } from 'react'
import { trackerApi } from '../api/trackerApi'
import { useAuth } from '../auth/AuthContext'
import { isFuture, todayIso } from './dates'
import HabitFormModal from './HabitFormModal'
import RenameActivityModal from './RenameActivityModal'
import TimeOfDayModal from './TimeOfDayModal'
import { TIME_OF_DAY_LABELS } from './habitOptions'

function pendingKey(id, slot) {
  return `${id}:${slot ?? 'ANY'}`
}

export default function ActivityListTab({
  type,
  idPrefix,
  addLabel,
  emptyMessage,
  modalTitle,
  entityLabel,
  renameTitle,
  refreshToken = 0,
}) {
  const { accessToken } = useAuth()
  const [date, setDate] = useState(todayIso())
  const [activities, setActivities] = useState(null)
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState(null)
  const [actionError, setActionError] = useState(null)
  const [pendingKeys, setPendingKeys] = useState([])
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [renameTarget, setRenameTarget] = useState(null)
  const [timeOfDayTarget, setTimeOfDayTarget] = useState(null)
  const [deleteTarget, setDeleteTarget] = useState(null)
  const [isDeleting, setIsDeleting] = useState(false)
  const [reloadCounter, setReloadCounter] = useState(0)

  const today = todayIso()

  useEffect(() => {
    let cancelled = false
    trackerApi
      .listOnDate(accessToken, date, type)
      .then((data) => {
        if (!cancelled) {
          setActivities(data)
        }
      })
      .catch((error) => {
        if (!cancelled) {
          setLoadError(error.message)
          if (error.status !== 400) {
            setActivities(null)
          }
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
  }, [accessToken, date, type, reloadCounter, refreshToken])

  function handleDateChange(event) {
    const nextDate = event.target.value
    if (nextDate === '' || isFuture(nextDate)) {
      return
    }
    setActionError(null)
    setLoadError(null)
    setIsLoading(true)
    setDate(nextDate)
  }

  function completionFor(activity, slot) {
    if (!slot) {
      return activity.completed
    }
    return activity.completions?.find((entry) => entry.timeOfDay === slot)?.completed ?? false
  }

  function isPending(activity, slot) {
    return pendingKeys.includes(pendingKey(activity.id, slot))
  }

  async function toggleCompleted(activity, slot) {
    const key = pendingKey(activity.id, slot)
    if (pendingKeys.includes(key)) {
      return
    }
    setActionError(null)
    setPendingKeys((keys) => [...keys, key])
    try {
      const updated = slot
        ? activity.completions?.find((entry) => entry.timeOfDay === slot)?.completed
          ? await trackerApi.unmarkCompleted(accessToken, activity.id, date, slot)
          : await trackerApi.markCompleted(accessToken, activity.id, date, slot)
        : activity.completed
          ? await trackerApi.unmarkCompleted(accessToken, activity.id, date)
          : await trackerApi.markCompleted(accessToken, activity.id, date)
      setActivities((list) =>
        list.map((item) =>
          item.id === activity.id
            ? {
                ...item,
                completed: updated.completed,
                completions: updated.completions ?? item.completions,
              }
            : item,
        ),
      )
    } catch (error) {
      setActionError(error.message)
    } finally {
      setPendingKeys((keys) => keys.filter((item) => item !== key))
    }
  }

  async function handleCreate(values) {
    await trackerApi.create(accessToken, { ...values, type })
    setIsModalOpen(false)
    setLoadError(null)
    setIsLoading(true)
    setReloadCounter((counter) => counter + 1)
  }

  async function handleRename(newName) {
    const target = renameTarget
    await trackerApi.update(accessToken, target.id, {
      name: newName,
      startDate: target.startDate,
      frequency: target.frequency,
      weekdays: target.weekdays ?? [],
      timeOfDay: target.timeOfDay ?? [],
      type: target.type ?? type,
    })
    setActivities((list) => list.map((item) => (item.id === target.id ? { ...item, name: newName } : item)))
    setRenameTarget(null)
  }

  async function handleTimeOfDayChange(slots) {
    const target = timeOfDayTarget
    await trackerApi.update(accessToken, target.id, {
      name: target.name,
      startDate: target.startDate,
      frequency: target.frequency,
      weekdays: target.weekdays ?? [],
      timeOfDay: slots,
      type: target.type ?? type,
    })
    setActivities((list) =>
      list.map((item) => {
        if (item.id !== target.id) {
          return item
        }
        const previous = new Map((item.completions ?? []).map((entry) => [entry.timeOfDay, entry.completed]))
        const completions = slots.map((slot) => ({
          timeOfDay: slot,
          completed: previous.get(slot) ?? false,
        }))
        return {
          ...item,
          timeOfDay: slots,
          completions,
          completed: slots.length > 0 && completions.every((entry) => entry.completed),
        }
      }),
    )
    setTimeOfDayTarget(null)
  }

  async function handleDeleteConfirm() {
    const target = deleteTarget
    setActionError(null)
    setIsDeleting(true)
    try {
      await trackerApi.remove(accessToken, target.id)
      setActivities((list) => list.filter((item) => item.id !== target.id))
      setDeleteTarget(null)
    } catch (error) {
      setActionError(error.message)
      setDeleteTarget(null)
    } finally {
      setIsDeleting(false)
    }
  }

  return (
    <div className="habits-tab">
      <div className="habits-toolbar">
        <div className="auth-field">
          <label htmlFor={`${idPrefix}-date`}>Дата</label>
          <input
            id={`${idPrefix}-date`}
            name="date"
            type="date"
            value={date}
            max={today}
            onChange={handleDateChange}
          />
        </div>
        <button type="button" className="auth-submit" onClick={() => setIsModalOpen(true)}>
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
        <p className="tracker-error" role="alert">
          {loadError}
        </p>
      )}

      {!isLoading && activities && activities.length === 0 && (
        <p className="tracker-empty">{emptyMessage}</p>
      )}

      {!isLoading && activities && activities.length > 0 && (
        <ul className="habits-list">
          {activities.map((activity) => {
            const slots = activity.timeOfDay ?? []
            return (
              <li key={activity.id} className="habit-item">
                {slots.length > 0 ? (
                  <div className="habit-time-of-day">
                    <span className="habit-name">{activity.name}</span>
                    <div className="weekday-options">
                      {slots.map((slot) => {
                        const label = TIME_OF_DAY_LABELS[slot] ?? slot
                        return (
                          <label key={slot} className="weekday-option">
                            <input
                              type="checkbox"
                              className="habit-checkbox"
                              aria-label={`${activity.name} — ${label}`}
                              checked={completionFor(activity, slot)}
                              disabled={isPending(activity, slot)}
                              onChange={() => toggleCompleted(activity, slot)}
                            />
                            <span>{label}</span>
                          </label>
                        )
                      })}
                    </div>
                  </div>
                ) : (
                  <>
                    <input
                      id={`${idPrefix}-completed-${activity.id}`}
                      type="checkbox"
                      className="habit-checkbox"
                      checked={activity.completed}
                      disabled={isPending(activity, null)}
                      onChange={() => toggleCompleted(activity, null)}
                    />
                    <label htmlFor={`${idPrefix}-completed-${activity.id}`} className="habit-name">
                      {activity.name}
                    </label>
                  </>
                )}
                <div className="habit-actions">
                  <button
                    type="button"
                    className="habit-action"
                    aria-label={`Время суток ${activity.name}`}
                    onClick={() => {
                      setActionError(null)
                      setTimeOfDayTarget(activity)
                    }}
                  >
                    Время суток
                  </button>
                  <button
                    type="button"
                    className="habit-action"
                    aria-label={`Переименовать ${activity.name}`}
                    disabled={isDeleting && deleteTarget?.id === activity.id}
                    onClick={() => {
                      setActionError(null)
                      setRenameTarget(activity)
                    }}
                  >
                    Переименовать
                  </button>
                  <button
                    type="button"
                    className="habit-action"
                    aria-label={`Удалить ${activity.name}`}
                    disabled={isDeleting && deleteTarget?.id === activity.id}
                    onClick={() => {
                      setActionError(null)
                      setDeleteTarget(activity)
                    }}
                  >
                    Удалить
                  </button>
                </div>
              </li>
            )
          })}
        </ul>
      )}

      {isModalOpen && (
        <HabitFormModal
          type={type}
          title={modalTitle}
          entityLabel={entityLabel}
          onClose={() => setIsModalOpen(false)}
          onSubmit={handleCreate}
        />
      )}

      {renameTarget && (
        <RenameActivityModal
          initialName={renameTarget.name}
          entityLabel={entityLabel}
          title={renameTitle}
          onClose={() => setRenameTarget(null)}
          onSubmit={handleRename}
        />
      )}

      {timeOfDayTarget && (
        <TimeOfDayModal
          initialTimeOfDay={timeOfDayTarget.timeOfDay ?? []}
          onClose={() => setTimeOfDayTarget(null)}
          onSubmit={handleTimeOfDayChange}
        />
      )}

      {deleteTarget && (
        <div className="modal-overlay" onClick={() => !isDeleting && setDeleteTarget(null)}>
          <div
            className="modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="delete-modal-title"
            onClick={(event) => event.stopPropagation()}
          >
            <h2 id="delete-modal-title" className="modal-title">
              Удалить «{deleteTarget.name}»?
            </h2>
            <p>Активность и вся история её отметок будут удалены безвозвратно.</p>
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
