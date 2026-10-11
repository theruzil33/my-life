import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import FinancesPage from '../../src/pages/FinancesPage'
import { ApiError } from '../../src/api/client'

vi.mock('../../src/auth/AuthContext', () => ({
  useAuth: () => ({ accessToken: 'token' }),
}))

vi.mock('../../src/api/financeApi', () => ({
  financeApi: {
    getBalance: vi.fn(),
    updateBalance: vi.fn(),
    listCategories: vi.fn(),
    createCategory: vi.fn(),
    updateCategory: vi.fn(),
    removeCategory: vi.fn(),
    listOperationsByDate: vi.fn(),
    listOperationsByRange: vi.fn(),
    saveOperation: vi.fn(),
    removeOperation: vi.fn(),
  },
}))

import { financeApi } from '../../src/api/financeApi'

const categories = [
  { id: 11, type: 'INCOME', name: 'Зарплата', group: 'Работа' },
  { id: 12, type: 'EXPENSE', name: 'Продукты', group: null },
]

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

function renderPage() {
  return render(
    <MemoryRouter>
      <FinancesPage />
    </MemoryRouter>,
  )
}

async function openCategories(user) {
  renderPage()
  await screen.findByText('1300.00')
  await user.click(screen.getByRole('tab', { name: 'Категории' }))
  return screen.getByRole('tabpanel', { name: 'Категории' })
}

beforeEach(() => {
  vi.resetAllMocks()
  financeApi.getBalance.mockResolvedValue({ startingBalance: 1000, amount: 1300 })
  financeApi.updateBalance.mockResolvedValue({ startingBalance: 2000, amount: 2300 })
  financeApi.listCategories.mockResolvedValue(categories)
  financeApi.createCategory.mockResolvedValue({ id: 13 })
  financeApi.updateCategory.mockResolvedValue({ id: 11 })
  financeApi.removeCategory.mockResolvedValue(null)
  financeApi.listOperationsByDate.mockResolvedValue([])
  financeApi.listOperationsByRange.mockResolvedValue([])
  financeApi.saveOperation.mockResolvedValue({ id: 1 })
  financeApi.removeOperation.mockResolvedValue(null)
})

describe('FinancesPage', () => {
  it('показывает баланс в шапке', async () => {
    renderPage()

    expect(await screen.findByText('1300.00')).toBeInTheDocument()
    expect(screen.getByText('Баланс')).toBeInTheDocument()
  })

  it('показывает индикацию загрузки баланса до ответа', async () => {
    const pending = deferred()
    financeApi.getBalance.mockReturnValue(pending.promise)

    renderPage()

    expect(document.querySelector('.balance-header')).toHaveTextContent('Загрузка…')
    pending.resolve({ startingBalance: 1000, amount: 1300 })
    expect(await screen.findByText('1300.00')).toBeInTheDocument()
  })

  it('показывает сообщение об ошибке загрузки баланса', async () => {
    financeApi.getBalance.mockRejectedValue(new ApiError('Сервис временно недоступен. Попробуйте позже.', 500))

    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Сервис временно недоступен. Попробуйте позже.',
    )
  })

  it('показывает вкладки и открывает «Операции» по умолчанию', async () => {
    renderPage()

    await screen.findByText('1300.00')
    expect(screen.queryByRole('tab', { name: 'Счета' })).not.toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Операции' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tab', { name: 'Категории' })).toHaveAttribute('aria-selected', 'false')
    expect(screen.getByRole('tab', { name: 'Статистика' })).toBeInTheDocument()
  })

  it('показывает баланс на всех вкладках', async () => {
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('1300.00')

    await user.click(screen.getByRole('tab', { name: 'Категории' }))
    expect(screen.getByText('1300.00')).toBeInTheDocument()

    await user.click(screen.getByRole('tab', { name: 'Статистика' }))
    expect(screen.getByText('1300.00')).toBeInTheDocument()
  })

  it('изменяет начальный баланс через модальное окно', async () => {
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('1300.00')
    await user.click(screen.getByRole('button', { name: 'Изменить начальный баланс' }))

    const dialog = screen.getByRole('dialog')
    const input = within(dialog).getByLabelText('Начальный баланс')
    await user.clear(input)
    await user.type(input, '2000')
    await user.click(within(dialog).getByRole('button', { name: 'Сохранить' }))

    await waitFor(() => {
      expect(financeApi.updateBalance).toHaveBeenCalledWith('token', 2000)
    })
    await waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })
    expect(await screen.findByText('2300.00')).toBeInTheDocument()
  })

  it('позволяет задать отрицательный начальный баланс', async () => {
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('1300.00')
    await user.click(screen.getByRole('button', { name: 'Изменить начальный баланс' }))

    const dialog = screen.getByRole('dialog')
    const input = within(dialog).getByLabelText('Начальный баланс')
    await user.clear(input)
    await user.type(input, '-250')
    await user.click(within(dialog).getByRole('button', { name: 'Сохранить' }))

    await waitFor(() => {
      expect(financeApi.updateBalance).toHaveBeenCalledWith('token', -250)
    })
  })

  it('не отправляет пустой начальный баланс', async () => {
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('1300.00')
    await user.click(screen.getByRole('button', { name: 'Изменить начальный баланс' }))

    const dialog = screen.getByRole('dialog')
    await user.clear(within(dialog).getByLabelText('Начальный баланс'))
    await user.click(within(dialog).getByRole('button', { name: 'Сохранить' }))

    expect(financeApi.updateBalance).not.toHaveBeenCalled()
    expect(within(dialog).getByText('Укажите начальный баланс')).toBeInTheDocument()
  })

  it('обновляет баланс после сохранения операции', async () => {
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('1300.00')
    expect(financeApi.getBalance).toHaveBeenCalledTimes(1)

    const input = await screen.findByLabelText('Сумма Продукты')
    await user.type(input, '200')
    await user.tab()

    await waitFor(() => {
      expect(financeApi.saveOperation).toHaveBeenCalled()
    })
    await waitFor(() => {
      expect(financeApi.getBalance).toHaveBeenCalledTimes(2)
    })
  })

  it('перечитывает статистику и показывает добавленную категорию при возврате на вкладку', async () => {
    let allCategories = [...categories]
    financeApi.listCategories.mockImplementation(() => Promise.resolve(allCategories))

    const user = userEvent.setup()
    renderPage()
    await screen.findByText('1300.00')

    await user.click(screen.getByRole('tab', { name: 'Статистика' }))
    await within(screen.getByRole('tabpanel', { name: 'Статистика' })).findByText('Зарплата')
    expect(financeApi.listOperationsByRange).toHaveBeenCalledTimes(1)

    allCategories = [...categories, { id: 99, type: 'EXPENSE', name: 'Кафе', group: null }]
    await user.click(screen.getByRole('tab', { name: 'Категории' }))
    const catPanel = screen.getByRole('tabpanel', { name: 'Категории' })
    await within(catPanel).findByText('Зарплата')
    await user.click(within(catPanel).getByRole('button', { name: 'Добавить категорию' }))
    const dialog = screen.getByRole('dialog')
    await user.selectOptions(within(dialog).getByLabelText('Тип'), 'EXPENSE')
    await user.type(within(dialog).getByLabelText('Название'), 'Кафе')
    await user.click(within(dialog).getByRole('button', { name: 'Создать' }))
    await waitFor(() => expect(financeApi.createCategory).toHaveBeenCalledWith('token', expect.objectContaining({ name: 'Кафе' })))

    await user.click(screen.getByRole('tab', { name: 'Статистика' }))
    expect(
      await within(screen.getByRole('tabpanel', { name: 'Статистика' })).findByText('Кафе'),
    ).toBeInTheDocument()
    expect(financeApi.listOperationsByRange).toHaveBeenCalledTimes(2)
  })

  it('показывает блок прихода перед блоком ухода с цветовыми классами', async () => {
    const user = userEvent.setup()
    const panel = await openCategories(user)

    expect(await within(panel).findByText('Зарплата')).toBeInTheDocument()
    const blocks = panel.querySelectorAll('.category-group')
    expect(blocks).toHaveLength(2)
    expect(blocks[0]).toHaveTextContent('Зарплата')
    expect(blocks[1]).toHaveTextContent('Продукты')
    expect(blocks[0]).toHaveClass('category-item-income')
    expect(blocks[1]).toHaveClass('category-item-expense')
  })

  it('объединяет категории одной группы в блок с заголовком', async () => {
    financeApi.listCategories.mockResolvedValue([
      { id: 11, type: 'INCOME', name: 'Зарплата', group: 'Работа' },
      { id: 13, type: 'INCOME', name: 'Премия', group: 'Работа' },
    ])
    const user = userEvent.setup()
    const panel = await openCategories(user)

    expect(await within(panel).findByText('Зарплата')).toBeInTheDocument()
    const blocks = panel.querySelectorAll('.category-group')
    expect(blocks).toHaveLength(1)
    expect(blocks[0]).toHaveClass('category-group')
    expect(blocks[0]).toHaveClass('category-group-framed')
    expect(within(panel).getByRole('heading', { name: 'Работа' })).toBeInTheDocument()
    expect(within(blocks[0]).getByText('Зарплата')).toBeInTheDocument()
    expect(within(blocks[0]).getByText('Премия')).toBeInTheDocument()
  })

  it('показывает категории без группы отдельными блоками', async () => {
    financeApi.listCategories.mockResolvedValue([
      { id: 11, type: 'INCOME', name: 'Зарплата', group: null },
      { id: 13, type: 'INCOME', name: 'Премия', group: null },
    ])
    const user = userEvent.setup()
    const panel = await openCategories(user)

    expect(await within(panel).findByText('Зарплата')).toBeInTheDocument()
    const blocks = panel.querySelectorAll('.category-group')
    expect(blocks).toHaveLength(2)
    expect(blocks[0]).not.toHaveClass('category-group-framed')
    expect(blocks[1]).not.toHaveClass('category-group-framed')
    expect(within(panel).queryByRole('heading')).not.toBeInTheDocument()
  })

  it('показывает пустое состояние при отсутствии категорий', async () => {
    financeApi.listCategories.mockResolvedValue([])
    const user = userEvent.setup()
    const panel = await openCategories(user)

    expect(await within(panel).findByText('У вас пока нет категорий.')).toBeInTheDocument()
  })

  it('создаёт категорию с типом, названием и группой', async () => {
    const user = userEvent.setup()
    const panel = await openCategories(user)

    await within(panel).findByText('Зарплата')
    await user.click(within(panel).getByRole('button', { name: 'Добавить категорию' }))

    const dialog = screen.getByRole('dialog')
    await user.selectOptions(within(dialog).getByLabelText('Тип'), 'INCOME')
    await user.type(within(dialog).getByLabelText('Название'), 'Подарок')
    await user.type(within(dialog).getByLabelText('Группа'), 'Работа')
    await user.click(within(dialog).getByRole('button', { name: 'Создать' }))

    await waitFor(() => {
      expect(financeApi.createCategory).toHaveBeenCalledWith('token', {
        type: 'INCOME',
        name: 'Подарок',
        group: 'Работа',
      })
    })
    await waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })
  })

  it('не создаёт категорию без типа и названия', async () => {
    const user = userEvent.setup()
    const panel = await openCategories(user)

    await within(panel).findByText('Зарплата')
    await user.click(within(panel).getByRole('button', { name: 'Добавить категорию' }))

    const dialog = screen.getByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Создать' }))

    expect(financeApi.createCategory).not.toHaveBeenCalled()
    expect(within(dialog).getByText('Выберите тип категории')).toBeInTheDocument()
    expect(within(dialog).getByText('Укажите название категории')).toBeInTheDocument()
  })

  it('редактирует название и группу, не меняя тип', async () => {
    const user = userEvent.setup()
    const panel = await openCategories(user)

    await within(panel).findByText('Зарплата')
    await user.click(within(panel).getByRole('button', { name: 'Редактировать Зарплата' }))

    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByLabelText('Тип')).toBeDisabled()

    const nameInput = within(dialog).getByLabelText('Название')
    await user.clear(nameInput)
    await user.type(nameInput, 'Премия')

    const groupInput = within(dialog).getByLabelText('Группа')
    await user.clear(groupInput)
    await user.type(groupInput, 'Бонусы')

    await user.click(within(dialog).getByRole('button', { name: 'Сохранить' }))

    await waitFor(() => {
      expect(financeApi.updateCategory).toHaveBeenCalledWith('token', 11, { name: 'Премия', group: 'Бонусы' })
    })
  })

  it('не редактирует категорию с пустым названием', async () => {
    const user = userEvent.setup()
    const panel = await openCategories(user)

    await within(panel).findByText('Зарплата')
    await user.click(within(panel).getByRole('button', { name: 'Редактировать Зарплата' }))

    const dialog = screen.getByRole('dialog')
    await user.clear(within(dialog).getByLabelText('Название'))
    await user.click(within(dialog).getByRole('button', { name: 'Сохранить' }))

    expect(financeApi.updateCategory).not.toHaveBeenCalled()
    expect(within(dialog).getByText('Укажите название категории')).toBeInTheDocument()
  })

  it('удаляет категорию после подтверждения', async () => {
    const user = userEvent.setup()
    const panel = await openCategories(user)

    await within(panel).findByText('Зарплата')
    await user.click(within(panel).getByRole('button', { name: 'Удалить Зарплата' }))

    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByText('Удалить «Зарплата»?')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Удалить' }))

    await waitFor(() => {
      expect(financeApi.removeCategory).toHaveBeenCalledWith('token', 11)
    })
    await waitFor(() => {
      expect(within(panel).queryByText('Зарплата')).not.toBeInTheDocument()
    })
    expect(within(panel).getByText('Продукты')).toBeInTheDocument()
  })

  it('не удаляет категорию при отмене подтверждения', async () => {
    const user = userEvent.setup()
    const panel = await openCategories(user)

    await within(panel).findByText('Зарплата')
    await user.click(within(panel).getByRole('button', { name: 'Удалить Зарплата' }))

    const dialog = screen.getByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Отмена' }))

    expect(financeApi.removeCategory).not.toHaveBeenCalled()
    expect(within(panel).getByText('Зарплата')).toBeInTheDocument()
  })
})
