const ERROR_INVALID_PLAN = 'Плановая сумма должна быть положительным числом'
const ERROR_PLAN_DECIMALS = 'Не более двух знаков после запятой'

export function parsePlannedAmount(raw) {
  const normalized = raw.trim().replace(',', '.')
  if (normalized === '') {
    return { value: null }
  }
  if (!/^\d+(\.\d*)?$/.test(normalized)) {
    return { error: ERROR_INVALID_PLAN }
  }
  const decimals = (normalized.split('.')[1] ?? '').length
  if (decimals > 2) {
    return { error: ERROR_PLAN_DECIMALS }
  }
  const value = Number(normalized)
  if (!(value > 0)) {
    return { error: ERROR_INVALID_PLAN }
  }
  return { value }
}

export function initialPlannedAmount(plannedAmount) {
  if (plannedAmount === null || plannedAmount === undefined) {
    return ''
  }
  return Number(plannedAmount).toFixed(2)
}
