export const FREQUENCY = {
  DAILY: 'DAILY',
  SPECIFIC_WEEKDAYS: 'SPECIFIC_WEEKDAYS',
}

export const FREQUENCY_OPTIONS = [
  { value: FREQUENCY.DAILY, label: 'Ежедневно' },
  { value: FREQUENCY.SPECIFIC_WEEKDAYS, label: 'По дням недели' },
]

export const WEEKDAY_OPTIONS = [
  { value: 'MONDAY', label: 'Пн', fullLabel: 'Понедельник', isoDay: 1 },
  { value: 'TUESDAY', label: 'Вт', fullLabel: 'Вторник', isoDay: 2 },
  { value: 'WEDNESDAY', label: 'Ср', fullLabel: 'Среда', isoDay: 3 },
  { value: 'THURSDAY', label: 'Чт', fullLabel: 'Четверг', isoDay: 4 },
  { value: 'FRIDAY', label: 'Пт', fullLabel: 'Пятница', isoDay: 5 },
  { value: 'SATURDAY', label: 'Сб', fullLabel: 'Суббота', isoDay: 6 },
  { value: 'SUNDAY', label: 'Вс', fullLabel: 'Воскресенье', isoDay: 7 },
]
