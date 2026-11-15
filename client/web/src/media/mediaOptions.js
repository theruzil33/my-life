export const MEDIA_TYPE = {
  FILM: 'FILM',
  BOOK: 'BOOK',
  GAME: 'GAME',
}

export const MEDIA_STATUS = {
  NOT_STARTED: 'NOT_STARTED',
  IN_PROGRESS: 'IN_PROGRESS',
  COMPLETED: 'COMPLETED',
}

export const DEFAULT_MEDIA_TYPE = MEDIA_TYPE.FILM

export const DEFAULT_MEDIA_STATUS = MEDIA_STATUS.NOT_STARTED

export const MEDIA_STATUS_OPTIONS = [
  { value: MEDIA_STATUS.NOT_STARTED, label: 'Не начал' },
  { value: MEDIA_STATUS.IN_PROGRESS, label: 'В процессе' },
  { value: MEDIA_STATUS.COMPLETED, label: 'Завершил' },
]

export const MEDIA_STATUS_LABELS = Object.fromEntries(
  MEDIA_STATUS_OPTIONS.map((option) => [option.value, option.label]),
)

export function statusLabel(status) {
  return MEDIA_STATUS_LABELS[status] ?? status
}

export const MEDIA_TABS = [
  {
    type: MEDIA_TYPE.FILM,
    label: 'Фильмы и сериалы',
    addLabel: 'Добавить фильм или сериал',
    emptyMessage: 'Здесь пока нет фильмов и сериалов.',
    modalTitle: 'Новый фильм или сериал',
    editTitle: 'Редактировать фильм или сериал',
    entityLabel: 'фильма или сериала',
  },
  {
    type: MEDIA_TYPE.BOOK,
    label: 'Книги',
    addLabel: 'Добавить книгу',
    emptyMessage: 'Здесь пока нет книг.',
    modalTitle: 'Новая книга',
    editTitle: 'Редактировать книгу',
    entityLabel: 'книги',
  },
  {
    type: MEDIA_TYPE.GAME,
    label: 'Игры',
    addLabel: 'Добавить игру',
    emptyMessage: 'Здесь пока нет игр.',
    modalTitle: 'Новая игра',
    editTitle: 'Редактировать игру',
    entityLabel: 'игры',
  },
]

export function mediaTabFor(type) {
  return MEDIA_TABS.find((tab) => tab.type === type) ?? MEDIA_TABS[0]
}
