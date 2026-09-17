import { describe, expect, it } from 'vitest'
import {
  addDays,
  eachIsoDate,
  firstDayOfMonthIso,
  formatDayLabel,
  isAfter,
  isFuture,
  parseIsoDate,
  toIsoDate,
  todayIso,
} from '../../src/tracker/dates'

describe('dates', () => {
  it('формирует ISO-дату из локальных компонент без сдвига из-за UTC', () => {
    expect(toIsoDate(new Date(2026, 0, 5))).toBe('2026-01-05')
    expect(toIsoDate(new Date(2026, 0, 5, 0, 30))).toBe('2026-01-05')
    expect(toIsoDate(new Date(2026, 11, 31, 23, 45))).toBe('2026-12-31')
  })

  it('возвращает сегодняшнюю дату в формате yyyy-MM-dd', () => {
    expect(todayIso()).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(todayIso()).toBe(toIsoDate(new Date()))
  })

  it('находит первый день месяца', () => {
    expect(firstDayOfMonthIso(new Date(2026, 8, 17))).toBe('2026-09-01')
    expect(firstDayOfMonthIso(new Date(2026, 0, 1))).toBe('2026-01-01')
  })

  it('сдвигает дату на дни с переходом через границы месяца и года', () => {
    expect(addDays('2026-09-30', 1)).toBe('2026-10-01')
    expect(addDays('2026-01-01', -1)).toBe('2025-12-31')
    expect(addDays('2026-02-28', 1)).toBe('2026-03-01')
  })

  it('перечисляет даты диапазона включительно', () => {
    expect(eachIsoDate('2026-09-28', '2026-10-02')).toEqual([
      '2026-09-28',
      '2026-09-29',
      '2026-09-30',
      '2026-10-01',
      '2026-10-02',
    ])
    expect(eachIsoDate('2026-09-17', '2026-09-17')).toEqual(['2026-09-17'])
  })

  it('возвращает пустой список для обратного или незаполненного диапазона', () => {
    expect(eachIsoDate('2026-09-18', '2026-09-17')).toEqual([])
    expect(eachIsoDate('', '2026-09-17')).toEqual([])
  })

  it('сравнивает ISO-даты', () => {
    expect(isAfter('2026-09-10', '2026-09-09')).toBe(true)
    expect(isAfter('2026-09-09', '2026-09-09')).toBe(false)
    expect(isAfter('2026-09-08', '2026-09-09')).toBe(false)
  })

  it('определяет будущую дату относительно сегодня', () => {
    expect(isFuture(addDays(todayIso(), 1))).toBe(true)
    expect(isFuture(todayIso())).toBe(false)
    expect(isFuture(addDays(todayIso(), -1))).toBe(false)
  })

  it('разбирает ISO-дату без сдвига часового пояса', () => {
    const parsed = parseIsoDate('2026-09-05')
    expect(parsed.getFullYear()).toBe(2026)
    expect(parsed.getMonth()).toBe(8)
    expect(parsed.getDate()).toBe(5)
  })

  it('форматирует подпись дня', () => {
    expect(formatDayLabel('2026-09-05')).toBe('5.09')
    expect(formatDayLabel('2026-12-31')).toBe('31.12')
  })
})
