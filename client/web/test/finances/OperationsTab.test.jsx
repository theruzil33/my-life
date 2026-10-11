import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import OperationsTab from '../../src/finances/OperationsTab'
import { todayIso } from '../../src/finances/dates'

vi.mock('../../src/auth/AuthContext', () => ({
  useAuth: () => ({ accessToken: 'token' }),
}))

vi.mock('../../src/api/financeApi', () => ({
  financeApi: {
    listCategories: vi.fn(),
    listOperationsByDate: vi.fn(),
    saveOperation: vi.fn(),
    removeOperation: vi.fn(),
  },
}))

import { financeApi } from '../../src/api/financeApi'

const categories = [
  { id: 11, type: 'INCOME', name: 'Зарплата', group: null },
  { id: 12, type: 'EXPENSE', name: 'Продукты', group: null },
]

const existing = [{ id: 101, categoryId: 12, date: todayIso(), amount: 150.5 }]

beforeEach(() => {
  vi.resetAllMocks()
  financeApi.listCategories.mockResolvedValue(categories)
  financeApi.listOperationsByDate.mockResolvedValue(existing)
  financeApi.saveOperation.mockImplementation((token, operation) =>
    Promise.resolve({ id: 200, ...operation }),
  )
  financeApi.removeOperation.mockResolvedValue(null)
})

describe('OperationsTab', () => {
  it('показывает категории ухода перед приходом и сумму существующей операции', async () => {
    render(<OperationsTab />)

    await screen.findByLabelText('Сумма Продукты')
    const rows = document.querySelectorAll('.operation-row')
    expect(rows).toHaveLength(2)
    expect(rows[0]).toHaveTextContent('Продукты')
    expect(rows[1]).toHaveTextContent('Зарплата')
    expect(screen.getByLabelText('Сумма Продукты')).toHaveValue('150.50')
    expect(screen.getByLabelText('Сумма Зарплата')).toHaveValue('')
  })

  it('сохраняет корректную сумму', async () => {
    const user = userEvent.setup()
    render(<OperationsTab />)

    const input = await screen.findByLabelText('Сумма Продукты')
    await user.clear(input)
    await user.type(input, '200')
    await user.tab()

    await waitFor(() => {
      expect(financeApi.saveOperation).toHaveBeenCalledWith(
        'token',
        expect.objectContaining({ categoryId: 12, amount: 200 }),
      )
    })
  })

  it('не сохраняет неположительную сумму', async () => {
    const user = userEvent.setup()
    render(<OperationsTab />)

    const input = await screen.findByLabelText('Сумма Продукты')
    await user.clear(input)
    await user.type(input, '0')
    await user.tab()

    expect(financeApi.saveOperation).not.toHaveBeenCalled()
    expect(await screen.findByRole('alert')).toHaveTextContent('Сумма должна быть больше нуля')
  })

  it('не сохраняет сумму с более чем двумя знаками после запятой', async () => {
    const user = userEvent.setup()
    render(<OperationsTab />)

    const input = await screen.findByLabelText('Сумма Продукты')
    await user.clear(input)
    await user.type(input, '10.123')
    await user.tab()

    expect(financeApi.saveOperation).not.toHaveBeenCalled()
    expect(await screen.findByRole('alert')).toHaveTextContent('Не более двух знаков после запятой')
  })

  it('удаляет операцию при очистке поля после подтверждения', async () => {
    const user = userEvent.setup()
    render(<OperationsTab />)

    const input = await screen.findByLabelText('Сумма Продукты')
    await user.clear(input)
    await user.tab()

    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveTextContent('Удалить операцию «Продукты»?')
    await user.click(within(dialog).getByRole('button', { name: 'Удалить' }))

    await waitFor(() => {
      expect(financeApi.removeOperation).toHaveBeenCalledWith('token', 101)
    })
    await waitFor(() => {
      expect(screen.getByLabelText('Сумма Продукты')).toHaveValue('')
    })
  })

  it('восстанавливает сумму при отмене удаления', async () => {
    const user = userEvent.setup()
    render(<OperationsTab />)

    const input = await screen.findByLabelText('Сумма Продукты')
    await user.clear(input)
    await user.tab()

    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Отмена' }))

    expect(financeApi.removeOperation).not.toHaveBeenCalled()
    expect(screen.getByLabelText('Сумма Продукты')).toHaveValue('150.50')
  })

  it('показывает ошибку API при сохранении', async () => {
    financeApi.saveOperation.mockRejectedValue(new Error('Сервис недоступен'))
    const user = userEvent.setup()
    render(<OperationsTab />)

    const input = await screen.findByLabelText('Сумма Продукты')
    await user.clear(input)
    await user.type(input, '200')
    await user.tab()

    expect(await screen.findByRole('alert')).toHaveTextContent('Сервис недоступен')
  })

  it('объединяет категории одной группы в блок с заголовком и не смешивает типы', async () => {
    financeApi.listCategories.mockResolvedValue([
      { id: 11, type: 'INCOME', name: 'Зарплата', group: 'Работа' },
      { id: 12, type: 'EXPENSE', name: 'Продукты', group: 'Еда' },
      { id: 13, type: 'EXPENSE', name: 'Кафе', group: 'Еда' },
      { id: 14, type: 'INCOME', name: 'Премия', group: 'Работа' },
      { id: 15, type: 'EXPENSE', name: 'Транспорт', group: null },
    ])
    financeApi.listOperationsByDate.mockResolvedValue([])

    render(<OperationsTab />)

    await screen.findByLabelText('Сумма Продукты')

    const blocks = Array.from(document.querySelectorAll('.operation-group'))
    expect(blocks).toHaveLength(3)

    const [meal, transport, work] = blocks

    expect(meal).toHaveClass('category-group-framed')
    expect(within(meal).getByRole('heading', { name: 'Еда' })).toBeInTheDocument()
    expect(meal.querySelectorAll('.operation-row')).toHaveLength(2)
    expect(meal.querySelectorAll('.operation-row-expense')).toHaveLength(2)
    expect(meal.querySelectorAll('.operation-row-income')).toHaveLength(0)
    expect(within(meal).getByLabelText('Сумма Продукты')).toBeInTheDocument()
    expect(within(meal).getByLabelText('Сумма Кафе')).toBeInTheDocument()

    expect(transport).not.toHaveClass('category-group-framed')
    expect(within(transport).queryByRole('heading')).not.toBeInTheDocument()
    expect(transport.querySelectorAll('.operation-row')).toHaveLength(1)
    expect(within(transport).getByText('Транспорт')).toBeInTheDocument()

    expect(work).toHaveClass('category-group-framed')
    expect(within(work).getByRole('heading', { name: 'Работа' })).toBeInTheDocument()
    expect(work.querySelectorAll('.operation-row-income')).toHaveLength(2)
    expect(work.querySelectorAll('.operation-row-expense')).toHaveLength(0)
    expect(within(work).getByLabelText('Сумма Зарплата')).toBeInTheDocument()
    expect(within(work).getByLabelText('Сумма Премия')).toBeInTheDocument()
  })

  it('перечитывает данные при повторном открытии вкладки', async () => {
    const { rerender } = render(<OperationsTab isActive={false} />)
    expect(financeApi.listOperationsByDate).not.toHaveBeenCalled()

    rerender(<OperationsTab isActive={true} />)
    await screen.findByLabelText('Сумма Продукты')
    expect(financeApi.listOperationsByDate).toHaveBeenCalledTimes(1)

    rerender(<OperationsTab isActive={false} />)
    rerender(<OperationsTab isActive={true} />)

    await waitFor(() => expect(financeApi.listOperationsByDate).toHaveBeenCalledTimes(2))
  })

  it('вызывает onOperationSaved после сохранения и удаления операции', async () => {
    const onOperationSaved = vi.fn()
    const user = userEvent.setup()
    render(<OperationsTab onOperationSaved={onOperationSaved} />)

    const input = await screen.findByLabelText('Сумма Продукты')
    await user.clear(input)
    await user.type(input, '200')
    await user.tab()

    await waitFor(() => expect(onOperationSaved).toHaveBeenCalledTimes(1))

    await user.clear(input)
    await user.tab()
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Удалить' }))

    await waitFor(() => expect(onOperationSaved).toHaveBeenCalledTimes(2))
  })
})
