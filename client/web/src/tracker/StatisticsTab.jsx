import { useCallback, useEffect, useState } from 'react'
import { trackerApi } from '../api/trackerApi'
import { useAuth } from '../auth/AuthContext'
import {
  eachIsoDate,
  firstDayOfMonthIso,
  formatDayLabel,
  isAfter,
  isFuture,
  todayIso,
} from './dates'

const STATUS_LABELS = {
  completed: 'выполнено',
  scheduled: 'не выполнено',
  unscheduled: 'не запланировано',
}

function defaultRange() {
  return { from: firstDayOfMonthIso(), to: todayIso() }
}

export default function StatisticsTab() {
  const { accessToken } = useAuth()
  const [applied, setApplied] = useState(defaultRange)
  const [draft, setDraft] = useState(defaultRange)
  const [records, setRecords] = useState(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState(null)
  const [rangeError, setRangeError] = useState(null)

  const loadRange = useCallback(
    async (range, fallbackRange) => {
      try {
        const data = await trackerApi.history(accessToken, range.from, range.to)
        setRecords(data)
        setApplied(range)
        setDraft(range)
      } catch (loadError) {
        setError(loadError.message)
        if (loadError.status === 400 && fallbackRange) {
          setDraft(fallbackRange)
        } else if (loadError.status !== 400) {
          setRecords(null)
        }
      } finally {
        setIsLoading(false)
      }
    },
    [accessToken],
  )

  useEffect(() => {
    let cancelled = false
    const range = defaultRange()
    trackerApi
      .history(accessToken, range.from, range.to)
      .then((data) => {
        if (!cancelled) {
          setRecords(data)
          setApplied(range)
          setDraft(range)
        }
      })
      .catch((loadError) => {
        if (!cancelled) {
          setError(loadError.message)
          setRecords(null)
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
    // начальная загрузка диапазона по умолчанию выполняется один раз при открытии вкладки
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function handleApply(event) {
    event.preventDefault()
    setRangeError(null)
    if (draft.from === '' || draft.to === '') {
      setRangeError('Укажите начало и конец диапазона')
      return
    }
    if (isFuture(draft.from) || isFuture(draft.to)) {
      setRangeError('Дата диапазона не может быть позже сегодняшней')
      return
    }
    if (isAfter(draft.from, draft.to)) {
      setRangeError('Начало диапазона не может быть позже конца')
      return
    }
    setError(null)
    setIsLoading(true)
    loadRange(draft, applied)
  }

  const columns = eachIsoDate(applied.from, applied.to)

  return (
    <div className="stats-tab">
      <form className="stats-controls" onSubmit={handleApply} noValidate>
        <div className="auth-field">
          <label htmlFor="stats-from">С</label>
          <input
            id="stats-from"
            name="from"
            type="date"
            value={draft.from}
            max={todayIso()}
            onChange={(event) => setDraft((current) => ({ ...current, from: event.target.value }))}
          />
        </div>
        <div className="auth-field">
          <label htmlFor="stats-to">По</label>
          <input
            id="stats-to"
            name="to"
            type="date"
            value={draft.to}
            max={todayIso()}
            onChange={(event) => setDraft((current) => ({ ...current, to: event.target.value }))}
          />
        </div>
        <button type="submit" className="auth-submit">
          Применить
        </button>
      </form>

      {rangeError && (
        <p className="tracker-error" role="alert">
          {rangeError}
        </p>
      )}

      {error && (
        <p className="tracker-error" role="alert">
          {error}
        </p>
      )}

      {isLoading && <p className="tracker-status">Загрузка…</p>}

      {!isLoading && records && records.length === 0 && (
        <p className="tracker-empty">За выбранный период привычек нет.</p>
      )}

      {!isLoading && records && records.length > 0 && (
        <>
          <div className="stats-legend">
            <span className="stats-legend-item">
              <span className="stats-square stats-square-completed" aria-hidden="true" />
              выполнено
            </span>
            <span className="stats-legend-item">
              <span className="stats-square stats-square-scheduled" aria-hidden="true" />
              не выполнено
            </span>
            <span className="stats-legend-item">
              <span className="stats-square stats-square-unscheduled" aria-hidden="true" />
              не запланировано
            </span>
          </div>

          <div className="stats-scroll">
            <table className="stats-matrix">
              <thead>
                <tr>
                  <th scope="col" className="stats-habit-name">
                    Привычка
                  </th>
                  {columns.map((date) => (
                    <th key={date} scope="col" className="stats-day-label" title={date}>
                      {formatDayLabel(date)}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {records.map((record) => {
                  const scheduled = new Set(record.days.map((day) => day.date))
                  const completed = new Set(
                    record.days.filter((day) => day.completed).map((day) => day.date),
                  )
                  return (
                    <tr key={record.id}>
                      <th scope="row" className="stats-habit-name">
                        {record.name}
                      </th>
                      {columns.map((date) => {
                        const status = completed.has(date)
                          ? 'completed'
                          : scheduled.has(date)
                            ? 'scheduled'
                            : 'unscheduled'
                        return (
                          <td key={date} className="stats-cell">
                            <span
                              className={`stats-square stats-square-${status}`}
                              title={`${date}: ${STATUS_LABELS[status]}`}
                              aria-label={`${record.name}, ${date}: ${STATUS_LABELS[status]}`}
                              role="img"
                            />
                          </td>
                        )
                      })}
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  )
}
