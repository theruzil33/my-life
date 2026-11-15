import { useState } from 'react'
import { Link } from 'react-router-dom'
import MediaTab from '../media/MediaTab'
import { DEFAULT_MEDIA_TYPE, MEDIA_TABS } from '../media/mediaOptions'

export default function MediaPage() {
  const [activeTab, setActiveTab] = useState(DEFAULT_MEDIA_TYPE)
  const [openedTabs, setOpenedTabs] = useState(() =>
    Object.fromEntries(MEDIA_TABS.map((tab) => [tab.type, tab.type === DEFAULT_MEDIA_TYPE])),
  )

  function selectTab(type) {
    setActiveTab(type)
    setOpenedTabs((tabs) => (tabs[type] ? tabs : { ...tabs, [type]: true }))
  }

  return (
    <section className="tracker-page">
      <div className="tracker-header">
        <Link className="tracker-back" to="/">
          ← На главную
        </Link>
        <div className="tracker-header-row">
          <h1 className="tracker-title">Медиатека</h1>
        </div>
      </div>

      <div className="tracker-tabs" role="tablist" aria-label="Разделы медиатеки">
        {MEDIA_TABS.map((tab) => (
          <button
            key={tab.type}
            type="button"
            role="tab"
            aria-selected={activeTab === tab.type}
            className={activeTab === tab.type ? 'tracker-tab tracker-tab-active' : 'tracker-tab'}
            onClick={() => selectTab(tab.type)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {MEDIA_TABS.map((tab) =>
        openedTabs[tab.type] ? (
          <div
            key={tab.type}
            role="tabpanel"
            aria-label={tab.label}
            hidden={activeTab !== tab.type}
          >
            <MediaTab
              type={tab.type}
              addLabel={tab.addLabel}
              emptyMessage={tab.emptyMessage}
              modalTitle={tab.modalTitle}
              editTitle={tab.editTitle}
            />
          </div>
        ) : null,
      )}
    </section>
  )
}
