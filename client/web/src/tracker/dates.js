function pad(value) {
  return String(value).padStart(2, '0')
}

export function toIsoDate(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

export function parseIsoDate(iso) {
  const [year, month, day] = iso.split('-').map(Number)
  return new Date(year, month - 1, day)
}

export function todayIso() {
  return toIsoDate(new Date())
}

export function currentMonthIso(reference = new Date()) {
  return `${reference.getFullYear()}-${pad(reference.getMonth() + 1)}`
}

export function firstDayOfMonthIso(reference = new Date()) {
  return toIsoDate(new Date(reference.getFullYear(), reference.getMonth(), 1))
}

export function addDays(iso, days) {
  const date = parseIsoDate(iso)
  date.setDate(date.getDate() + days)
  return toIsoDate(date)
}

export function isAfter(iso, other) {
  return iso > other
}

export function isFuture(iso) {
  return iso > todayIso()
}

export function eachIsoDate(from, to) {
  const dates = []
  if (from === '' || to === '' || isAfter(from, to)) {
    return dates
  }
  let cursor = from
  while (!isAfter(cursor, to)) {
    dates.push(cursor)
    cursor = addDays(cursor, 1)
  }
  return dates
}

export function formatDayLabel(iso) {
  const date = parseIsoDate(iso)
  return `${date.getDate()}.${pad(date.getMonth() + 1)}`
}
