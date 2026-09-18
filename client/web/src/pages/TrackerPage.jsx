import { Link } from 'react-router-dom'
import { useState } from 'react'
import HabitsTab from '../tracker/HabitsTab'
import StatisticsTab from '../tracker/StatisticsTab'

const HABITS_TAB = 'habits'
const STATISTICS_TAB = 'statistics'

export default function TrackerPage() {
  const [activeTab, setActiveTab] = useState(HABITS_TAB)
  const [statisticsOpened, setStatisticsOpened] = useState(false)

  function selectTab(tab) {
    setActiveTab(tab)
    if (tab === STATISTICS_TAB) {
      setStatisticsOpened(true)
    }
  }

  return (
    <section className="tracker-page">
      <div className="tracker-header">
        <Link className="tracker-back" to="/">
          ← На главную
        </Link>
        <h1 className="tracker-title">Трекер привычек</h1>
      </div>

      <div className="tracker-tabs" role="tablist" aria-label="Разделы трекера">
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === HABITS_TAB}
          className={activeTab === HABITS_TAB ? 'tracker-tab tracker-tab-active' : 'tracker-tab'}
          onClick={() => selectTab(HABITS_TAB)}
        >
          Привычки
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === STATISTICS_TAB}
          className={activeTab === STATISTICS_TAB ? 'tracker-tab tracker-tab-active' : 'tracker-tab'}
          onClick={() => selectTab(STATISTICS_TAB)}
        >
          Статистика
        </button>
      </div>

      <div role="tabpanel" aria-label="Привычки" hidden={activeTab !== HABITS_TAB}>
        <HabitsTab />
      </div>

      {statisticsOpened && (
        <div role="tabpanel" aria-label="Статистика" hidden={activeTab !== STATISTICS_TAB}>
          <StatisticsTab />
        </div>
      )}
    </section>
  )
}
