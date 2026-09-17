import { Link } from 'react-router-dom'
import { APPS } from '../apps/apps'
import { useAuth } from '../auth/AuthContext'

export default function HomePage() {
  const { email, logout } = useAuth()

  return (
    <section className="hub-page">
      <header className="hub-header">
        <h1 className="hub-title">Мои приложения</h1>
        {email && <p className="auth-user">Вы вошли как {email}</p>}
        <button type="button" className="auth-submit" onClick={logout}>
          Выйти
        </button>
      </header>

      <nav className="app-grid" aria-label="Приложения">
        {APPS.map((app) =>
          app.enabled ? (
            <Link key={app.id} to={app.path} className="app-card">
              <span className="app-card-title">{app.title}</span>
              {app.description && <span className="app-card-description">{app.description}</span>}
            </Link>
          ) : (
            <div key={app.id} className="app-card app-card-disabled" aria-disabled="true">
              <span className="app-card-title">{app.title}</span>
              {app.description && <span className="app-card-description">{app.description}</span>}
            </div>
          ),
        )}
      </nav>
    </section>
  )
}
