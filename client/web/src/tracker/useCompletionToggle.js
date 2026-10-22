import { useState } from 'react'
import { trackerApi } from '../api/trackerApi'

function pendingKey(id, slot) {
  return `${id}:${slot ?? 'ANY'}`
}

export function completionFor(activity, slot) {
  if (!slot) {
    return activity.completed
  }
  return activity.completions?.find((entry) => entry.timeOfDay === slot)?.completed ?? false
}

export function useCompletionToggle({ accessToken, date, setActivities }) {
  const [actionError, setActionError] = useState(null)
  const [pendingKeys, setPendingKeys] = useState([])

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

  return { actionError, setActionError, completionFor, isPending, toggleCompleted }
}
