import { Link } from 'react-router-dom'
import { useState } from 'react'
import TodayTab from '../tracker/TodayTab'
import HabitsTab from '../tracker/HabitsTab'
import TasksTab from '../tracker/TasksTab'
import StatisticsTab from '../tracker/StatisticsTab'
import ImportFormModal from '../tracker/ImportFormModal'

const TODAY_TAB = 'today'
const HABITS_TAB = 'habits'
const TASKS_TAB = 'tasks'
const STATISTICS_TAB = 'statistics'

const TAB_ORDER = [TODAY_TAB, HABITS_TAB, TASKS_TAB, STATISTICS_TAB]

const TAB_LABELS = {
  [TODAY_TAB]: 'Сегодня',
  [HABITS_TAB]: 'Привычки',
  [TASKS_TAB]: 'Задачи',
  [STATISTICS_TAB]: 'Статистика',
}

export default function TrackerPage() {
  const [activeTab, setActiveTab] = useState(TODAY_TAB)
  const [openedTabs, setOpenedTabs] = useState({
    [TODAY_TAB]: true,
    [HABITS_TAB]: false,
    [TASKS_TAB]: false,
    [STATISTICS_TAB]: false,
  })
  const [isImportModalOpen, setIsImportModalOpen] = useState(false)
  const [dataVersion, setDataVersion] = useState(0)

  function selectTab(tab) {
    setActiveTab(tab)
    setOpenedTabs((tabs) => (tabs[tab] ? tabs : { ...tabs, [tab]: true }))
  }

  function handleImported() {
    setDataVersion((version) => version + 1)
  }

  return (
    <section className="tracker-page">
      <div className="tracker-header">
        <Link className="tracker-back" to="/">
          ← На главную
        </Link>
        <div className="tracker-header-row">
          <h1 className="tracker-title">Трекер привычек</h1>
          <button type="button" className="balance-edit" onClick={() => setIsImportModalOpen(true)}>
            Импорт
          </button>
        </div>
      </div>

      <div className="tracker-tabs" role="tablist" aria-label="Разделы трекера">
        {TAB_ORDER.map((tab) => (
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

      <div role="tabpanel" aria-label="Сегодня" hidden={activeTab !== TODAY_TAB}>
        <TodayTab refreshToken={dataVersion} />
      </div>

      {openedTabs[HABITS_TAB] && (
        <div role="tabpanel" aria-label="Привычки" hidden={activeTab !== HABITS_TAB}>
          <HabitsTab refreshToken={dataVersion} />
        </div>
      )}

      {openedTabs[TASKS_TAB] && (
        <div role="tabpanel" aria-label="Задачи" hidden={activeTab !== TASKS_TAB}>
          <TasksTab refreshToken={dataVersion} />
        </div>
      )}

      {openedTabs[STATISTICS_TAB] && (
        <div role="tabpanel" aria-label="Статистика" hidden={activeTab !== STATISTICS_TAB}>
          <StatisticsTab refreshToken={dataVersion} />
        </div>
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
