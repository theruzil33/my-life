import { TIME_OF_DAY } from './habitOptions'

export const ALL_DAY_GROUP = 'ALL_DAY'

const TIME_OF_DAY_ORDER = [TIME_OF_DAY.MORNING, TIME_OF_DAY.DAY, TIME_OF_DAY.EVENING]

export function currentTimeOfDay(now = new Date()) {
  const hour = now.getHours()
  if (hour >= 6 && hour < 12) {
    return TIME_OF_DAY.MORNING
  }
  if (hour >= 12 && hour < 18) {
    return TIME_OF_DAY.DAY
  }
  return TIME_OF_DAY.EVENING
}

export function timeOfDayGroupOrder(now = new Date()) {
  const start = TIME_OF_DAY_ORDER.indexOf(currentTimeOfDay(now))
  return [...TIME_OF_DAY_ORDER.slice(start), ...TIME_OF_DAY_ORDER.slice(0, start)]
}

export function groupActivities(activities, now = new Date()) {
  const groups = timeOfDayGroupOrder(now).map((slot) => ({
    key: slot,
    activities: activities.filter((activity) => (activity.timeOfDay ?? []).includes(slot)),
  }))

  groups.push({
    key: ALL_DAY_GROUP,
    activities: activities.filter((activity) => (activity.timeOfDay ?? []).length === 0),
  })

  return groups.filter((group) => group.activities.length > 0)
}
