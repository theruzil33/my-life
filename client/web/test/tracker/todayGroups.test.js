import { describe, expect, it } from 'vitest'
import {
  ALL_DAY_GROUP,
  currentTimeOfDay,
  groupActivities,
  timeOfDayGroupOrder,
} from '../../src/tracker/todayGroups'

function at(hours, minutes = 0) {
  return new Date(2026, 8, 17, hours, minutes)
}

const morningHabit = {
  id: 1,
  name: 'Зарядка',
  type: 'HABIT',
  timeOfDay: ['MORNING', 'EVENING'],
}

const dayTask = {
  id: 2,
  name: 'Созвон',
  type: 'TASK',
  timeOfDay: ['DAY'],
}

const allDayTask = {
  id: 3,
  name: 'Купить хлеб',
  type: 'TASK',
  timeOfDay: [],
}

describe('currentTimeOfDay', () => {
  it('относит 05:59 к вечеру', () => {
    expect(currentTimeOfDay(at(5, 59))).toBe('EVENING')
  })

  it('относит 06:00 к утру', () => {
    expect(currentTimeOfDay(at(6, 0))).toBe('MORNING')
  })

  it('относит 11:59 к утру', () => {
    expect(currentTimeOfDay(at(11, 59))).toBe('MORNING')
  })

  it('относит 12:00 к дню', () => {
    expect(currentTimeOfDay(at(12, 0))).toBe('DAY')
  })

  it('относит 17:59 к дню', () => {
    expect(currentTimeOfDay(at(17, 59))).toBe('DAY')
  })

  it('относит 18:00 к вечеру', () => {
    expect(currentTimeOfDay(at(18, 0))).toBe('EVENING')
  })
})

describe('timeOfDayGroupOrder', () => {
  it('начинает утренний порядок с утра', () => {
    expect(timeOfDayGroupOrder(at(8))).toEqual(['MORNING', 'DAY', 'EVENING'])
  })

  it('начинает дневной порядок с дня', () => {
    expect(timeOfDayGroupOrder(at(13))).toEqual(['DAY', 'EVENING', 'MORNING'])
  })

  it('начинает вечерний порядок с вечера', () => {
    expect(timeOfDayGroupOrder(at(20))).toEqual(['EVENING', 'MORNING', 'DAY'])
  })
})

describe('groupActivities', () => {
  it('раскладывает активности по частям дня и сохраняет порядок API', () => {
    const groups = groupActivities([morningHabit, dayTask, allDayTask], at(8))

    expect(groups.map((group) => group.key)).toEqual([
      'MORNING',
      'DAY',
      'EVENING',
      ALL_DAY_GROUP,
    ])
    expect(groups[0].activities.map((activity) => activity.id)).toEqual([1])
    expect(groups[1].activities.map((activity) => activity.id)).toEqual([2])
    expect(groups[2].activities.map((activity) => activity.id)).toEqual([1])
    expect(groups[3].activities.map((activity) => activity.id)).toEqual([3])
  })

  it('ставит группу «Весь день» последней при любом текущем времени', () => {
    const groups = groupActivities([allDayTask, morningHabit], at(20))

    expect(groups[groups.length - 1].key).toBe(ALL_DAY_GROUP)
  })

  it('не возвращает пустые группы', () => {
    const groups = groupActivities([dayTask], at(8))

    expect(groups.map((group) => group.key)).toEqual(['DAY'])
  })

  it('возвращает пустой список при отсутствии активностей', () => {
    expect(groupActivities([], at(8))).toEqual([])
  })
})
