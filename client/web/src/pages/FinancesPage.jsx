import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import CategoriesTab from '../finances/CategoriesTab'
import OperationsTab from '../finances/OperationsTab'
import StatisticsTab from '../finances/StatisticsTab'
import BalanceHeader from '../finances/BalanceHeader'
import BalanceFormModal from '../finances/BalanceFormModal'
import ImportFormModal from '../finances/ImportFormModal'
import { financeApi } from '../api/financeApi'
import { useAuth } from '../auth/AuthContext'

const CATEGORIES_TAB = 'categories'
const OPERATIONS_TAB = 'operations'
const STATISTICS_TAB = 'statistics'

const TAB_LABELS = {
  [CATEGORIES_TAB]: 'Категории',
  [OPERATIONS_TAB]: 'Операции',
  [STATISTICS_TAB]: 'Статистика',
}

const TABS = [CATEGORIES_TAB, OPERATIONS_TAB, STATISTICS_TAB]

export default function FinancesPage() {
  const { accessToken } = useAuth()
  const [activeTab, setActiveTab] = useState(OPERATIONS_TAB)
  const [openedTabs, setOpenedTabs] = useState({
    [CATEGORIES_TAB]: false,
    [OPERATIONS_TAB]: true,
    [STATISTICS_TAB]: false,
  })
  const [balance, setBalance] = useState(null)
  const [isBalanceLoading, setIsBalanceLoading] = useState(true)
  const [balanceError, setBalanceError] = useState(null)
  const [isBalanceModalOpen, setIsBalanceModalOpen] = useState(false)
  const [isImportModalOpen, setIsImportModalOpen] = useState(false)
  const [dataVersion, setDataVersion] = useState(0)

  const loadBalance = useCallback(() => {
    let cancelled = false
    Promise.resolve()
      .then(() => {
        if (!cancelled) {
          setIsBalanceLoading(true)
        }
      })
      .then(() => financeApi.getBalance(accessToken))
      .then((data) => {
        if (!cancelled) {
          setBalance(data)
          setBalanceError(null)
        }
      })
      .catch((error) => {
        if (!cancelled) {
          setBalanceError(error.message)
          setBalance(null)
        }
      })
      .finally(() => {
        if (!cancelled) {
          setIsBalanceLoading(false)
        }
      })
    return () => {
      cancelled = true
    }
  }, [accessToken])

  useEffect(() => loadBalance(), [loadBalance])

  function selectTab(tab) {
    setActiveTab(tab)
    setOpenedTabs((tabs) => (tabs[tab] ? tabs : { ...tabs, [tab]: true }))
  }

  async function handleBalanceSubmit(value) {
    const updated = await financeApi.updateBalance(accessToken, value)
    setBalance(updated)
    setBalanceError(null)
    setIsBalanceModalOpen(false)
  }

  function handleImported() {
    loadBalance()
    setDataVersion((version) => version + 1)
  }

  return (
    <section className="tracker-page">
      <div className="tracker-header">
        <Link className="tracker-back" to="/">
          ← На главную
        </Link>
        <h1 className="tracker-title">Финансы</h1>
        <BalanceHeader
          balance={balance}
          isLoading={isBalanceLoading}
          error={balanceError}
          onEdit={() => setIsBalanceModalOpen(true)}
          onImport={() => setIsImportModalOpen(true)}
        />
      </div>

      <div className="tracker-tabs" role="tablist" aria-label="Разделы финансов">
        {TABS.map((tab) => (
          <button
            key={tab}
            type="button"
            role="tab"
            aria-selected={activeTab === tab}
            className={activeTab === tab ? 'tracker-tab tracker-tab-active' : 'tracker-tab'}
            onClick={() => selectTab(tab)}
          >
            {TAB_LABELS[tab]}
          </button>
        ))}
      </div>

      {openedTabs[CATEGORIES_TAB] && (
        <div role="tabpanel" aria-label="Категории" hidden={activeTab !== CATEGORIES_TAB}>
          <CategoriesTab />
        </div>
      )}

      {openedTabs[OPERATIONS_TAB] && (
        <div role="tabpanel" aria-label="Операции" hidden={activeTab !== OPERATIONS_TAB}>
          <OperationsTab
            isActive={activeTab === OPERATIONS_TAB}
            onOperationSaved={loadBalance}
            refreshToken={dataVersion}
          />
        </div>
      )}

      {openedTabs[STATISTICS_TAB] && (
        <div role="tabpanel" aria-label="Статистика" hidden={activeTab !== STATISTICS_TAB}>
          <StatisticsTab isActive={activeTab === STATISTICS_TAB} refreshToken={dataVersion} />
        </div>
      )}

      {isBalanceModalOpen && (
        <BalanceFormModal
          startingBalance={balance ? balance.startingBalance : null}
          onClose={() => setIsBalanceModalOpen(false)}
          onSubmit={handleBalanceSubmit}
        />
      )}

      {isImportModalOpen && (
        <ImportFormModal
          onClose={() => setIsImportModalOpen(false)}
          onImported={handleImported}
        />
      )}
    </section>
  )
}
