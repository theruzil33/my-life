import { isAfter, todayIso } from './dates'
import { FREQUENCY } from './habitOptions'

export function validateHabitForm({ name, startDate, frequency, weekdays, entityLabel = 'привычки' }) {
  const errors = {}

  if (!name || name.trim() === '') {
    errors.name = `Укажите название ${entityLabel}`
  }

  if (!startDate) {
    errors.startDate = 'Укажите дату начала'
  } else if (isAfter(startDate, todayIso())) {
    errors.startDate = 'Дата начала не может быть позже сегодняшней'
  }

  if (!frequency) {
    errors.frequency = 'Выберите периодичность'
  } else if (frequency === FREQUENCY.SPECIFIC_WEEKDAYS && (!weekdays || weekdays.length === 0)) {
    errors.frequency = 'Выберите хотя бы один день недели'
  }

  return errors
}

export function validateActivityName(name, entityLabel = 'привычки') {
  const errors = {}

  if (!name || name.trim() === '') {
    errors.name = `Укажите название ${entityLabel}`
  }

  return errors
}

export function hasValidationErrors(errors) {
  return Object.keys(errors).length > 0
}
