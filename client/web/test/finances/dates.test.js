import { describe, expect, it } from 'vitest'
import { currentMonthIso, monthRange } from '../../src/finances/dates'

describe('finances dates', () => {
  it('возвращает диапазон месяца по значению YYYY-MM', () => {
    expect(monthRange('2026-02')).toEqual({ from: '2026-02-01', to: '2026-02-28' })
    expect(monthRange('2026-09')).toEqual({ from: '2026-09-01', to: '2026-09-30' })
  })

  it('возвращает пустой диапазон для пустого значения', () => {
    expect(monthRange('')).toEqual({ from: '', to: '' })
    expect(monthRange(undefined)).toEqual({ from: '', to: '' })
  })

  it('возвращает текущий месяц в формате YYYY-MM', () => {
    expect(currentMonthIso(new Date(2026, 4, 13))).toBe('2026-05')
  })
})
