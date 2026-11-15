import { describe, expect, it } from 'vitest'
import {
  DEFAULT_MEDIA_STATUS,
  DEFAULT_MEDIA_TYPE,
  MEDIA_STATUS,
  MEDIA_STATUS_LABELS,
  MEDIA_TABS,
  MEDIA_TYPE,
  mediaTabFor,
  statusLabel,
} from '../../src/media/mediaOptions'

describe('mediaOptions', () => {
  it('перечисляет подвкладки в порядке «Фильмы и сериалы», «Книги», «Игры»', () => {
    expect(MEDIA_TABS.map((tab) => tab.label)).toEqual(['Фильмы и сериалы', 'Книги', 'Игры'])
    expect(MEDIA_TABS.map((tab) => tab.type)).toEqual([
      MEDIA_TYPE.FILM,
      MEDIA_TYPE.BOOK,
      MEDIA_TYPE.GAME,
    ])
  })

  it('открывает по умолчанию подвкладку фильмов и сериалов', () => {
    expect(DEFAULT_MEDIA_TYPE).toBe(MEDIA_TYPE.FILM)
  })

  it('по умолчанию использует статус «Не начал»', () => {
    expect(DEFAULT_MEDIA_STATUS).toBe(MEDIA_STATUS.NOT_STARTED)
  })

  it('сопоставляет статусы подписям', () => {
    expect(MEDIA_STATUS_LABELS).toEqual({
      NOT_STARTED: 'Не начал',
      IN_PROGRESS: 'В процессе',
      COMPLETED: 'Завершил',
    })
    expect(statusLabel('IN_PROGRESS')).toBe('В процессе')
  })

  it('возвращает подпись статуса как есть для неизвестного значения', () => {
    expect(statusLabel('DROPPED')).toBe('DROPPED')
  })

  it('находит конфигурацию подвкладки по виду записи', () => {
    expect(mediaTabFor(MEDIA_TYPE.BOOK).addLabel).toBe('Добавить книгу')
    expect(mediaTabFor('UNKNOWN').type).toBe(MEDIA_TYPE.FILM)
  })
})
