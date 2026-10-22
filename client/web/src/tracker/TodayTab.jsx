import { useEffect, useState } from 'react'
import { trackerApi } from '../api/trackerApi'
import { useAuth } from '../auth/AuthContext'
import { toIsoDate } from './dates'
import { ACTIVITY_TYPE, TIME_OF_DAY_LABELS } from './habitOptions'
import { ALL_DAY_GROUP, currentTimeOfDay, groupActivities } from './todayGroups'
import { useCompletionToggle } from './useCompletionToggle'

const GROUP_LABELS = {
  ...TIME_OF_DAY_LABELS,
  [ALL_DAY_GROUP]: 'Весь день',
}

const TYPE_LABELS = {
  [ACTIVITY_TYPE.HABIT]: 'Привычка',
  [ACTIVITY_TYPE.TASK]: 'Задача',
}

const NOW_UPDATE_INTERVAL = 60 * 1000

export default function TodayTab({ refreshToken = 0 }) {
  const { accessToken } = useAuth()
  const [now, setNow] = useState(() => new Date())
  const [result, setResult] = useState({ key: null, activities: null, error: null })
  const date = toIsoDate(now)
  const requestKey = `${accessToken ?? ''}|${date}|${refreshToken}`
  const { actionError, setActionError, completionFor, isPending, toggleCompleted } =
    useCompletionToggle({
      accessToken,
      date,
      setActivities: (update) =>
        setResult((current) => ({
          ...current,
          activities:
            typeof update === 'function' ? update(current.activities) : update,
        })),
    })

  const isLoading = result.key !== requestKey
  const activities = result.activities
  const loadError = result.error

  useEffect(() => {
    const interval = setInterval(() => setNow(new Date()), NOW_UPDATE_INTERVAL)
    return () => clearInterval(interval)
  }, [])

  useEffect(() => {
    let cancelled = false
    trackerApi
      .listOnDate(accessToken, date)
      .then((data) => {
        if (!cancelled) {
          setResult({ key: requestKey, activities: data, error: null })
        }
      })
      .catch((error) => {
        if (!cancelled) {
          setResult({ key: requestKey, activities: null, error: error.message })
        }
      })
      .finally(() => {
        if (!cancelled) {
          setActionError(null)
        }
      })
    return () => {
      cancelled = true
    }
  }, [accessToken, date, requestKey, setActionError])

  const groups = groupActivities(activities ?? [], now)
  const currentGroup = currentTimeOfDay(now)

  return (
    <div className="today-tab">
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

      {!isLoading && !loadError && activities && activities.length === 0 && (
        <p className="tracker-empty">На сегодня активностей нет.</p>
      )}

      {!isLoading && !loadError && activities && activities.length > 0 && (
        <div className="today-groups">
          {groups.map((group) => {
            const slot = group.key === ALL_DAY_GROUP ? null : group.key
            const isCurrent = group.key === currentGroup
            return (
              <section
                key={group.key}
                className={isCurrent ? 'today-group today-group-current' : 'today-group'}
                aria-labelledby={`today-group-${group.key}`}
              >
                <div className="today-group-header">
                  <h2 id={`today-group-${group.key}`} className="today-group-title">
                    {GROUP_LABELS[group.key] ?? group.key}
                  </h2>
                  {isCurrent && <span className="today-group-now">Сейчас</span>}
                </div>
                <ul className="habits-list">
                  {group.activities.map((activity) => {
                    const label = GROUP_LABELS[group.key] ?? group.key
                    return (
                      <li key={activity.id} className="habit-item">
                        <label className="today-activity">
                          <input
                            type="checkbox"
                            className="habit-checkbox"
                            aria-label={slot ? `${activity.name} — ${label}` : activity.name}
                            checked={completionFor(activity, slot)}
                            disabled={isPending(activity, slot)}
                            onChange={() => toggleCompleted(activity, slot)}
                          />
                          <span className="habit-name">{activity.name}</span>
                        </label>
                        <span className="today-type">{TYPE_LABELS[activity.type] ?? ''}</span>
                      </li>
                    )
                  })}
                </ul>
              </section>
            )
          })}
        </div>
      )}
    </div>
  )
}
