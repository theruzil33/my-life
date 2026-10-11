export const CATEGORY_TYPE = {
  INCOME: 'INCOME',
  EXPENSE: 'EXPENSE',
}

export const CATEGORY_TYPE_LABELS = {
  [CATEGORY_TYPE.INCOME]: 'Приход',
  [CATEGORY_TYPE.EXPENSE]: 'Уход',
}

export const CATEGORY_TYPE_ORDER = [CATEGORY_TYPE.INCOME, CATEGORY_TYPE.EXPENSE]

export function categoryTypeClass(type) {
  return type === CATEGORY_TYPE.INCOME ? 'category-item-income' : 'category-item-expense'
}
