import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import CategoriesTab from '../../src/finances/CategoriesTab'

vi.mock('../../src/auth/AuthContext', () => ({
  useAuth: () => ({ accessToken: 'token' }),
}))

vi.mock('../../src/api/financeApi', () => ({
  financeApi: {
    listCategories: vi.fn(),
    reorderCategories: vi.fn(),
  },
}))

import { financeApi } from '../../src/api/financeApi'

const twoPlain = [
  { id: 1, type: 'INCOME', name: 'Зарплата', group: null },
  { id: 2, type: 'INCOME', name: 'Премия', group: null },
  { id: 3, type: 'EXPENSE', name: 'Продукты', group: null },
]

const withGroup = [
  { id: 1, type: 'INCOME', name: 'Зарплата', group: 'Работа' },
  { id: 2, type: 'INCOME', name: 'Премия', group: 'Работа' },
  { id: 3, type: 'INCOME', name: 'Прочее', group: null },
]

function renderedNames() {
  return Array.from(document.querySelectorAll('.category-item .category-name')).map(
    (node) => node.textContent,
  )
}

beforeEach(() => {
  vi.resetAllMocks()
  financeApi.listCategories.mockResolvedValue(twoPlain)
  financeApi.reorderCategories.mockResolvedValue(null)
})

describe('CategoriesTab', () => {
  it('показывает кнопки перемещения у каждого блока', async () => {
    render(<CategoriesTab />)

    expect(await screen.findByLabelText('Переместить «Зарплата» выше')).toBeInTheDocument()
    expect(screen.getByLabelText('Переместить «Зарплата» ниже')).toBeInTheDocument()
    expect(screen.getByLabelText('Переместить «Премия» выше')).toBeInTheDocument()
    expect(screen.getByLabelText('Переместить «Продукты» выше')).toBeInTheDocument()
  })

  it('перемещает блок вниз и сохраняет новый порядок через API', async () => {
    const user = userEvent.setup()
    render(<CategoriesTab />)
    await screen.findByLabelText('Переместить «Зарплата» ниже')

    await user.click(screen.getByLabelText('Переместить «Зарплата» ниже'))

    await waitFor(() => {
      expect(financeApi.reorderCategories).toHaveBeenCalledWith('token', 'INCOME', [2, 1])
    })
    expect(renderedNames().slice(0, 2)).toEqual(['Премия', 'Зарплата'])
  })

  it('перемещает группу целиком, сохраняя порядок категорий внутри', async () => {
    financeApi.listCategories.mockResolvedValue(withGroup)
    const user = userEvent.setup()
    render(<CategoriesTab />)
    await screen.findByLabelText('Переместить «Работа» ниже')

    await user.click(screen.getByLabelText('Переместить «Работа» ниже'))

    await waitFor(() => {
      expect(financeApi.reorderCategories).toHaveBeenCalledWith('token', 'INCOME', [3, 1, 2])
    })
  })

  it('отключает недоступные на краях типа действия перемещения', async () => {
    render(<CategoriesTab />)

    expect(await screen.findByLabelText('Переместить «Зарплата» выше')).toBeDisabled()
    expect(screen.getByLabelText('Переместить «Зарплата» ниже')).toBeEnabled()
    expect(screen.getByLabelText('Переместить «Премия» выше')).toBeEnabled()
    expect(screen.getByLabelText('Переместить «Премия» ниже')).toBeDisabled()
    expect(screen.getByLabelText('Переместить «Продукты» выше')).toBeDisabled()
    expect(screen.getByLabelText('Переместить «Продукты» ниже')).toBeDisabled()
  })

  it('при ошибке API показывает сообщение и не меняет порядок', async () => {
    financeApi.reorderCategories.mockRejectedValue(new Error('Сервис недоступен'))
    const user = userEvent.setup()
    render(<CategoriesTab />)
    await screen.findByLabelText('Переместить «Зарплата» ниже')

    await user.click(screen.getByLabelText('Переместить «Зарплата» ниже'))

    expect(await screen.findByRole('alert')).toHaveTextContent('Сервис недоступен')
    expect(renderedNames().slice(0, 2)).toEqual(['Зарплата', 'Премия'])
  })
})
