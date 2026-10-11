function formatAmount(value) {
  return Number(value).toFixed(2)
}

export default function BalanceHeader({ balance, isLoading, error, onEdit }) {
  return (
    <div className="balance-header">
      <span className="balance-label">Баланс</span>
      {isLoading && <span className="tracker-status">Загрузка…</span>}
      {!isLoading && error && (
        <span className="tracker-error" role="alert">
          {error}
        </span>
      )}
      {!isLoading && !error && balance && (
        <span className="balance-amount">{formatAmount(balance.amount)}</span>
      )}
      <button type="button" className="balance-edit" onClick={onEdit}>
        Изменить начальный баланс
      </button>
    </div>
  )
}
