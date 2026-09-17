import { useAuth } from '../auth/AuthContext'

export default function HomePage() {
  const { email, logout } = useAuth()

  return (
    <section className="auth-page">
      <h1 className="auth-title">Вы вошли в систему</h1>
      {email && <p className="auth-user">Вы вошли как {email}</p>}
      <button type="button" className="auth-submit" onClick={logout}>
        Выйти
      </button>
    </section>
  )
}
