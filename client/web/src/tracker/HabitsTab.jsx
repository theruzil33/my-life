import { useEffect, useState } from 'react'
import { trackerApi } from '../api/trackerApi'
import { useAuth } from '../auth/AuthContext'
import { isFuture, todayIso } from './dates'
import HabitFormModal from './HabitFormModal'

export default function HabitsTab() {
  const { accessToken } = useAuth()
  const [date, setDate] = useState(todayIso())
  const [habits, setHabits] = useState(null)
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState(null)
  const [actionError, setActionError] = useState(null)
  const [pendingIds, setPendingIds] = useState([])
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [reloadCounter, setReloadCounter] = useState(0)

  const today = todayIso()

  useEffect(() => {
    let cancelled = false
    trackerApi
      .listOnDate(accessToken, date)
      .then((data) => {
        if (!cancelled) {
          setHabits(data)
        }
      })
      .catch((error) => {
        if (!cancelled) {
          setLoadError(error.message)
          if (error.status !== 400) {
            setHabits(null)
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
  }, [accessToken, date, reloadCounter])

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

  async function toggleCompleted(habit) {
    if (pendingIds.includes(habit.id)) {
      return
    }
    setActionError(null)
    setPendingIds((ids) => [...ids, habit.id])
    try {
      const updated = habit.completed
        ? await trackerApi.unmarkCompleted(accessToken, habit.id, date)
        : await trackerApi.markCompleted(accessToken, habit.id, date)
      setHabits((list) =>
        list.map((item) => (item.id === habit.id ? { ...item, completed: updated.completed } : item)),
      )
    } catch (error) {
      setActionError(error.message)
    } finally {
      setPendingIds((ids) => ids.filter((id) => id !== habit.id))
    }
  }

  async function handleCreate(values) {
    await trackerApi.create(accessToken, values)
    setIsModalOpen(false)
    setLoadError(null)
    setIsLoading(true)
    setReloadCounter((counter) => counter + 1)
  }

  return (
    <div className="habits-tab">
      <div className="habits-toolbar">
        <div className="auth-field">
          <label htmlFor="habits-date">Дата</label>
          <input
            id="habits-date"
            name="date"
            type="date"
            value={date}
            max={today}
            onChange={handleDateChange}
          />
        </div>
        <button type="button" className="auth-submit" onClick={() => setIsModalOpen(true)}>
          Добавить привычку
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

      {!isLoading && habits && habits.length === 0 && (
        <p className="tracker-empty">На выбранную дату привычек нет.</p>
      )}

      {!isLoading && habits && habits.length > 0 && (
        <ul className="habits-list">
          {habits.map((habit) => (
            <li key={habit.id} className="habit-item">
              <input
                id={`habit-completed-${habit.id}`}
                type="checkbox"
                className="habit-checkbox"
                checked={habit.completed}
                disabled={pendingIds.includes(habit.id)}
                onChange={() => toggleCompleted(habit)}
              />
              <label htmlFor={`habit-completed-${habit.id}`} className="habit-name">
                {habit.name}
              </label>
            </li>
          ))}
        </ul>
      )}

      {isModalOpen && <HabitFormModal onClose={() => setIsModalOpen(false)} onSubmit={handleCreate} />}
    </div>
  )
}
