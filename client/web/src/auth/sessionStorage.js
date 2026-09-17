export const AUTH_STORAGE_KEY = 'my-life.auth'

export function readStoredSession(storage = window.localStorage) {
  const raw = storage.getItem(AUTH_STORAGE_KEY)
  if (!raw) {
    return null
  }
  try {
    const parsed = JSON.parse(raw)
    if (parsed && typeof parsed.accessToken === 'string' && parsed.accessToken !== '') {
      return {
        accessToken: parsed.accessToken,
        email: typeof parsed.email === 'string' ? parsed.email : null,
      }
    }
  } catch {
    return null
  }
  return null
}

export function writeStoredSession(session, storage = window.localStorage) {
  storage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session))
}

export function clearStoredSession(storage = window.localStorage) {
  storage.removeItem(AUTH_STORAGE_KEY)
}
