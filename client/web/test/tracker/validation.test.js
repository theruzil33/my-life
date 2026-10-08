import { describe, expect, it } from 'vitest'
import { FREQUENCY } from '../../src/tracker/habitOptions'
import { hasValidationErrors, validateHabitForm } from '../../src/tracker/validation'
import { addDays, todayIso } from '../../src/tracker/dates'

const today = todayIso()

function form(overrides = {}) {
  return {
    name: 'Зарядка',
    startDate: today,
    frequency: FREQUENCY.DAILY,
    weekdays: [],
    ...overrides,
  }
}

describe('validateHabitForm', () => {
  it('не находит ошибок для корректной ежедневной привычки', () => {
    const errors = validateHabitForm(form())
    expect(errors).toEqual({})
    expect(hasValidationErrors(errors)).toBe(false)
  })

  it('не находит ошибок для привычки по дням недели с выбранным днём', () => {
    const errors = validateHabitForm(
      form({ frequency: FREQUENCY.SPECIFIC_WEEKDAYS, weekdays: ['MONDAY'] }),
    )
    expect(errors).toEqual({})
  })

  it('требует название', () => {
    expect(validateHabitForm(form({ name: '' })).name).toBe('Укажите название привычки')
    expect(validateHabitForm(form({ name: '   ' })).name).toBe('Укажите название привычки')
  })

  it('отклоняет дату начала в будущем', () => {
    const errors = validateHabitForm(form({ startDate: addDays(today, 1) }))
    expect(errors.startDate).toBe('Дата начала не может быть позже сегодняшней')
  })

  it('принимает сегодняшнюю и прошлую дату начала', () => {
    expect(validateHabitForm(form({ startDate: today })).startDate).toBeUndefined()
    expect(validateHabitForm(form({ startDate: addDays(today, -3) })).startDate).toBeUndefined()
  })

  it('требует дату начала', () => {
    expect(validateHabitForm(form({ startDate: '' })).startDate).toBe('Укажите дату начала')
  })

  it('требует периодичность', () => {
    expect(validateHabitForm(form({ frequency: null })).frequency).toBe('Выберите периодичность')
  })

  it('требует хотя бы один день недели для периодичности по дням', () => {
    const errors = validateHabitForm(
      form({ frequency: FREQUENCY.SPECIFIC_WEEKDAYS, weekdays: [] }),
    )
    expect(errors.frequency).toBe('Выберите хотя бы один день недели')
  })

  it('не требует дни недели для ежедневной периодичности', () => {
    expect(validateHabitForm(form({ frequency: FREQUENCY.DAILY, weekdays: [] })).frequency).toBeUndefined()
  })

  it('сообщает о наличии ошибок', () => {
    expect(hasValidationErrors({ name: 'ошибка' })).toBe(true)
    expect(hasValidationErrors({})).toBe(false)
  })
})
