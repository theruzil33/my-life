import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import MediaTab from '../../src/media/MediaTab'
import { ApiError } from '../../src/api/client'

vi.mock('../../src/auth/AuthContext', () => ({
  useAuth: () => ({ accessToken: 'token' }),
}))

vi.mock('../../src/api/mediaApi', () => ({
  mediaApi: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    remove: vi.fn(),
  },
}))

import { mediaApi } from '../../src/api/mediaApi'

const TAB = {
  type: 'FILM',
  addLabel: 'Добавить фильм или сериал',
  emptyMessage: 'Здесь пока нет фильмов и сериалов.',
  modalTitle: 'Новый фильм или сериал',
  editTitle: 'Редактировать фильм или сериал',
}

const films = [
  {
    id: 1,
    type: 'FILM',
    title: 'Дюна',
    description: 'Экранизация романа',
    status: 'IN_PROGRESS',
  },
  { id: 2, type: 'FILM', title: 'Прибытие', description: null, status: 'NOT_STARTED' },
]

function renderTab() {
  return render(<MediaTab {...TAB} />)
}

beforeEach(() => {
  vi.resetAllMocks()
  mediaApi.list.mockResolvedValue(films)
  mediaApi.create.mockResolvedValue({
    id: 3,
    type: 'FILM',
    title: 'Новый',
    description: null,
    status: 'NOT_STARTED',
  })
  mediaApi.update.mockImplementation((token, id, values) => Promise.resolve({ id, type: 'FILM', ...values }))
  mediaApi.remove.mockResolvedValue(null)
})

describe('MediaTab', () => {
  it('загружает и показывает записи своего вида', async () => {
    renderTab()

    expect(await screen.findByText('Дюна')).toBeInTheDocument()
    expect(screen.getByText('Экранизация романа')).toBeInTheDocument()
    expect(screen.getByLabelText('Статус Дюна')).toHaveValue('IN_PROGRESS')
    expect(mediaApi.list).toHaveBeenCalledWith('token', 'FILM')
  })

  it('не показывает блок описания у записи без описания', async () => {
    renderTab()

    expect(await screen.findByText('Прибытие')).toBeInTheDocument()
    expect(screen.queryByText('Экранизация романа')).toBeInTheDocument()
    expect(document.querySelectorAll('.media-description')).toHaveLength(1)
  })

  it('показывает сообщение об отсутствии записей', async () => {
    mediaApi.list.mockResolvedValue([])

    renderTab()

    expect(await screen.findByText('Здесь пока нет фильмов и сериалов.')).toBeInTheDocument()
  })

  it('показывает ошибку загрузки и повторяет запрос', async () => {
    const user = userEvent.setup()
    mediaApi.list.mockRejectedValueOnce(new ApiError('Ошибка загрузки', 500))

    renderTab()

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Ошибка загрузки')

    await user.click(screen.getByRole('button', { name: 'Повторить' }))

    expect(await screen.findByText('Дюна')).toBeInTheDocument()
    expect(mediaApi.list).toHaveBeenCalledTimes(2)
  })

  it('создаёт запись через форму и добавляет её в список', async () => {
    const user = userEvent.setup()
    renderTab()
    await screen.findByText('Дюна')

    await user.click(screen.getByRole('button', { name: 'Добавить фильм или сериал' }))
    const dialog = screen.getByRole('dialog')
    await user.type(within(dialog).getByLabelText('Название'), 'Интерстеллар')
    await user.click(within(dialog).getByRole('button', { name: 'Добавить' }))

    await waitFor(() => {
      expect(mediaApi.create).toHaveBeenCalledWith('token', {
        type: 'FILM',
        title: 'Интерстеллар',
        description: '',
        status: 'NOT_STARTED',
      })
    })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(await screen.findByText('Новый')).toBeInTheDocument()
  })

  it('не отправляет форму создания с пустым названием', async () => {
    const user = userEvent.setup()
    renderTab()
    await screen.findByText('Дюна')

    await user.click(screen.getByRole('button', { name: 'Добавить фильм или сериал' }))
    const dialog = screen.getByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Добавить' }))

    expect(within(dialog).getByText('Укажите название.')).toBeInTheDocument()
    expect(mediaApi.create).not.toHaveBeenCalled()
  })

  it('показывает ошибку сервера в форме создания без потери введённых значений', async () => {
    const user = userEvent.setup()
    mediaApi.create.mockRejectedValue(new ApiError('Такое название уже есть', 400))
    renderTab()
    await screen.findByText('Дюна')

    await user.click(screen.getByRole('button', { name: 'Добавить фильм или сериал' }))
    const dialog = screen.getByRole('dialog')
    await user.type(within(dialog).getByLabelText('Название'), 'Дюна')
    await user.click(within(dialog).getByRole('button', { name: 'Добавить' }))

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Такое название уже есть')
    expect(within(dialog).getByLabelText('Название')).toHaveValue('Дюна')
  })

  it('меняет статус записи прямо в списке', async () => {
    const user = userEvent.setup()
    renderTab()
    await screen.findByText('Дюна')

    await user.selectOptions(screen.getByLabelText('Статус Дюна'), 'COMPLETED')

    await waitFor(() => {
      expect(mediaApi.update).toHaveBeenCalledWith('token', 1, {
        title: 'Дюна',
        description: 'Экранизация романа',
        status: 'COMPLETED',
      })
    })
    expect(await screen.findByLabelText('Статус Дюна')).toHaveValue('COMPLETED')
  })

  it('возвращает прежний статус и показывает ошибку, если смена не удалась', async () => {
    const user = userEvent.setup()
    mediaApi.update.mockRejectedValue(new ApiError('Не удалось сохранить', 500))
    renderTab()
    await screen.findByText('Дюна')

    await user.selectOptions(screen.getByLabelText('Статус Дюна'), 'COMPLETED')

    expect(await screen.findByRole('alert')).toHaveTextContent('Не удалось сохранить')
    expect(screen.getByLabelText('Статус Дюна')).toHaveValue('IN_PROGRESS')
    expect(screen.getByText('Дюна')).toBeInTheDocument()
  })

  it('редактирует название, описание и статус записи', async () => {
    const user = userEvent.setup()
    renderTab()
    await screen.findByText('Дюна')

    await user.click(screen.getByRole('button', { name: 'Редактировать Дюна' }))
    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByLabelText('Название')).toHaveValue('Дюна')
    expect(within(dialog).getByLabelText('Описание')).toHaveValue('Экранизация романа')

    const titleInput = within(dialog).getByLabelText('Название')
    await user.clear(titleInput)
    await user.type(titleInput, 'Дюна: часть вторая')
    await user.selectOptions(within(dialog).getByLabelText('Статус'), 'COMPLETED')
    await user.click(within(dialog).getByRole('button', { name: 'Сохранить' }))

    await waitFor(() => {
      expect(mediaApi.update).toHaveBeenCalledWith('token', 1, {
        title: 'Дюна: часть вторая',
        description: 'Экранизация романа',
        status: 'COMPLETED',
      })
    })
    expect(await screen.findByText('Дюна: часть вторая')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('удаляет запись после подтверждения', async () => {
    const user = userEvent.setup()
    renderTab()
    await screen.findByText('Дюна')

    await user.click(screen.getByRole('button', { name: 'Удалить Дюна' }))
    const dialog = screen.getByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Удалить' }))

    await waitFor(() => expect(mediaApi.remove).toHaveBeenCalledWith('token', 1))
    await waitFor(() => expect(screen.queryByText('Дюна')).not.toBeInTheDocument())
    expect(screen.getByText('Прибытие')).toBeInTheDocument()
  })

  it('не удаляет запись при отказе от подтверждения', async () => {
    const user = userEvent.setup()
    renderTab()
    await screen.findByText('Дюна')

    await user.click(screen.getByRole('button', { name: 'Удалить Дюна' }))
    const dialog = screen.getByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Отмена' }))

    expect(mediaApi.remove).not.toHaveBeenCalled()
    expect(screen.getByText('Дюна')).toBeInTheDocument()
  })

  it('оставляет запись в списке и показывает ошибку, если удаление не удалось', async () => {
    const user = userEvent.setup()
    mediaApi.remove.mockRejectedValue(new ApiError('Не удалось удалить', 500))
    renderTab()
    await screen.findByText('Дюна')

    await user.click(screen.getByRole('button', { name: 'Удалить Дюна' }))
    const dialog = screen.getByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Удалить' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Не удалось удалить')
    expect(screen.getByText('Дюна')).toBeInTheDocument()
  })
})
