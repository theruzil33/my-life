import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import StatisticsTab from '../../src/finances/StatisticsTab'
import { firstDayOfMonthIso, lastDayOfMonthIso, todayIso } from '../../src/finances/dates'

vi.mock('../../src/auth/AuthContext', () => ({
  useAuth: () => ({ accessToken: 'token' }),
}))

vi.mock('../../src/api/financeApi', () => ({
  financeApi: {
    listCategories: vi.fn(),
    listOperationsByRange: vi.fn(),
  },
}))

import { financeApi } from '../../src/api/financeApi'

const categories = [
  { id: 11, type: 'INCOME', name: 'Зарплата', group: null },
  { id: 12, type: 'EXPENSE', name: 'Продукты', group: null },
]

const operations = [{ id: 101, categoryId: 12, date: todayIso(), amount: 150.5 }]

beforeEach(() => {
  vi.resetAllMocks()
  financeApi.listCategories.mockResolvedValue(categories)
  financeApi.listOperationsByRange.mockResolvedValue(operations)
})

describe('StatisticsTab', () => {
  it('строит таблицу категорий и дат с суммами в порядке «расход → приход»', async () => {
    render(<StatisticsTab />)

    expect(await screen.findByText('Продукты')).toBeInTheDocument()
    expect(screen.getByText('Зарплата')).toBeInTheDocument()
    expect(screen.getByText('150.50')).toBeInTheDocument()

    const rows = document.querySelectorAll('.finance-stats-matrix tbody tr')
    expect(rows).toHaveLength(2)
    expect(rows[0]).toHaveTextContent('Продукты')
    expect(rows[1]).toHaveTextContent('Зарплата')
  })

  it('показывает итоги прихода и ухода за выбранный период между выбором диапазона и таблицей', async () => {
    financeApi.listCategories.mockResolvedValue([
      { id: 11, type: 'INCOME', name: 'Зарплата', group: null },
      { id: 12, type: 'EXPENSE', name: 'Продукты', group: null },
    ])
    financeApi.listOperationsByRange.mockResolvedValue([
      { id: 101, categoryId: 11, date: todayIso(), amount: 1000 },
      { id: 102, categoryId: 12, date: todayIso(), amount: 40.5 },
    ])

    render(<StatisticsTab />)

    expect(await screen.findByText('Приход: 1000.00')).toBeInTheDocument()
    expect(screen.getByText('Расход: 40.50')).toBeInTheDocument()

    const form = document.querySelector('.stats-controls')
    const totals = document.querySelector('.stats-totals')
    const table = document.querySelector('.finance-stats-matrix')
    expect(totals).not.toBeNull()
    expect(form.compareDocumentPosition(totals) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(totals.compareDocumentPosition(table) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  })

  it('показывает нулевые итоги, когда за период нет операций', async () => {
    financeApi.listOperationsByRange.mockResolvedValue([])

    render(<StatisticsTab />)

    expect(await screen.findByText('Приход: 0.00')).toBeInTheDocument()
    expect(screen.getByText('Расход: 0.00')).toBeInTheDocument()
  })

  it('обновляет итоги при смене диапазона', async () => {
    financeApi.listOperationsByRange.mockResolvedValueOnce([
      { id: 101, categoryId: 12, date: todayIso(), amount: 40.5 },
    ])
    financeApi.listOperationsByRange.mockResolvedValueOnce([
      { id: 101, categoryId: 12, date: todayIso(), amount: 99 },
    ])

    render(<StatisticsTab />)
    expect(await screen.findByText('Расход: 40.50')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('С'), { target: { value: '2026-09-01' } })
    fireEvent.change(screen.getByLabelText('По'), { target: { value: '2026-09-30' } })
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Применить' }))

    expect(await screen.findByText('Расход: 99.00')).toBeInTheDocument()
  })

  it('показывает пустое состояние при отсутствии категорий', async () => {
    financeApi.listCategories.mockResolvedValue([])

    render(<StatisticsTab />)

    expect(await screen.findByText('У вас пока нет категорий.')).toBeInTheDocument()
  })

  it('не отправляет запрос при недопустимом диапазоне', async () => {
    render(<StatisticsTab />)
    await screen.findByText('Продукты')
    expect(financeApi.listOperationsByRange).toHaveBeenCalledTimes(1)

    fireEvent.change(screen.getByLabelText('С'), { target: { value: '2026-10-31' } })
    fireEvent.change(screen.getByLabelText('По'), { target: { value: '2026-10-01' } })
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Применить' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Начало диапазона не может быть позже конца')
    expect(financeApi.listOperationsByRange).toHaveBeenCalledTimes(1)
  })

  it('показывает ошибку загрузки', async () => {
    financeApi.listOperationsByRange.mockRejectedValue(new Error('Сервис недоступен'))

    render(<StatisticsTab />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Сервис недоступен')
    expect(screen.queryByText(/Приход:/)).not.toBeInTheDocument()
    expect(screen.queryByText(/Расход:/)).not.toBeInTheDocument()
  })

  it('по умолчанию показывает весь текущий календарный месяц', async () => {
    render(<StatisticsTab />)

    await screen.findByText('Продукты')

    expect(financeApi.listOperationsByRange).toHaveBeenCalledWith(
      'token',
      firstDayOfMonthIso(),
      lastDayOfMonthIso(),
    )
  })

  it('группирует категории, показывает группу слева и растягивает одиночную категорию', async () => {
    financeApi.listCategories.mockResolvedValue([
      { id: 11, type: 'INCOME', name: 'Зарплата', group: 'Работа' },
      { id: 14, type: 'INCOME', name: 'Премия', group: 'Работа' },
      { id: 12, type: 'EXPENSE', name: 'Продукты', group: 'Еда' },
      { id: 13, type: 'EXPENSE', name: 'Кафе', group: 'Еда' },
      { id: 15, type: 'EXPENSE', name: 'Транспорт', group: null },
    ])
    financeApi.listOperationsByRange.mockResolvedValue([])

    render(<StatisticsTab />)

    await screen.findByText('Транспорт')

    const groupCells = Array.from(document.querySelectorAll('.finance-stats-group'))
    expect(groupCells).toHaveLength(2)
    expect(groupCells.map((cell) => cell.textContent)).toEqual(['Еда', 'Работа'])
    expect(groupCells[0]).toHaveAttribute('rowspan', '2')

    const rows = Array.from(document.querySelectorAll('.finance-stats-matrix tbody tr'))
    expect(rows).toHaveLength(5)
    expect(rows[0]).toHaveClass('operation-row-expense')
    expect(rows[4]).toHaveClass('operation-row-income')

    const transportRow = rows.find((row) => row.textContent.includes('Транспорт'))
    const nameCells = transportRow.querySelectorAll('th')
    expect(nameCells).toHaveLength(1)
    expect(nameCells[0]).toHaveAttribute('colspan', '2')
  })

  it('перечитывает данные при повторном открытии вкладки', async () => {
    financeApi.listCategories.mockResolvedValueOnce([{ id: 11, type: 'EXPENSE', name: 'Продукты', group: null }])

    const { rerender } = render(<StatisticsTab isActive={false} />)
    expect(financeApi.listOperationsByRange).not.toHaveBeenCalled()

    rerender(<StatisticsTab isActive={true} />)
    expect(await screen.findByText('Продукты')).toBeInTheDocument()
    expect(financeApi.listOperationsByRange).toHaveBeenCalledTimes(1)

    financeApi.listCategories.mockResolvedValueOnce([
      { id: 11, type: 'EXPENSE', name: 'Продукты', group: null },
      { id: 99, type: 'EXPENSE', name: 'Кафе', group: null },
    ])
    rerender(<StatisticsTab isActive={false} />)
    rerender(<StatisticsTab isActive={true} />)

    expect(await screen.findByText('Кафе')).toBeInTheDocument()
    await waitFor(() => expect(financeApi.listOperationsByRange).toHaveBeenCalledTimes(2))
  })
})
