import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'

import { getAccessToken, logout } from '@core/authSession'
import { socketClient } from '@core/socketClient'
import {
  changePasswordApi,
  listSessionsApi,
  revokeSessionApi,
} from '@core/sessionService'
import type { SessionInfo } from '@core/types/auth'
import {
  DEFAULT_NOTIFICATION_PREFS,
  getNotificationPrefsApi,
  updateNotificationPrefsApi,
  type NotificationPrefs,
} from '@core/notificationService'

function formatDate(value?: string): string {
  if (!value) return '—'
  const time = Date.parse(value)
  return Number.isFinite(time) ? new Date(time).toLocaleString() : value
}

function SettingsPage() {
  const navigate = useNavigate()
  const currentToken = getAccessToken()

  const [sessions, setSessions] = useState<SessionInfo[]>([])
  const [loadingSessions, setLoadingSessions] = useState(true)
  const [sessionsError, setSessionsError] = useState('')
  const [revokingId, setRevokingId] = useState<string | null>(null)

  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [revokeOthers, setRevokeOthers] = useState(true)
  const [pwBusy, setPwBusy] = useState(false)
  const [pwError, setPwError] = useState('')
  const [pwMessage, setPwMessage] = useState('')

  const [notifyPrefs, setNotifyPrefs] = useState<NotificationPrefs>(
    DEFAULT_NOTIFICATION_PREFS,
  )
  const [notifyBusy, setNotifyBusy] = useState(false)
  const [notifyError, setNotifyError] = useState('')
  // true khi server chưa có /me/notifications: lựa chọn chỉ giữ trong phiên này.
  const [notifyLocalOnly, setNotifyLocalOnly] = useState(false)

  const loadSessions = useCallback(async () => {
    try {
      setSessions(await listSessionsApi())
      setSessionsError('')
    } catch (err) {
      setSessionsError(
        err instanceof Error ? err.message : 'Cannot load sessions',
      )
    } finally {
      setLoadingSessions(false)
    }
  }, [])

  function refreshSessions() {
    setLoadingSessions(true)
    void loadSessions()
  }

  useEffect(() => {
    let cancelled = false
    listSessionsApi()
      .then((items) => {
        if (!cancelled) setSessions(items)
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setSessionsError(
            err instanceof Error ? err.message : 'Cannot load sessions',
          )
        }
      })
      .finally(() => {
        if (!cancelled) setLoadingSessions(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    let cancelled = false
    getNotificationPrefsApi()
      .then((prefs) => {
        if (cancelled) return
        if (prefs) {
          setNotifyPrefs(prefs)
        } else {
          setNotifyLocalOnly(true)
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setNotifyError(
            err instanceof Error
              ? err.message
              : 'Cannot load notification settings',
          )
        }
      })
    return () => {
      cancelled = true
    }
  }, [])

  async function handleRevoke(session: SessionInfo) {
    const isCurrent = session.sessionId === currentToken
    if (
      !window.confirm(
        isCurrent
          ? 'Sign out of this device?'
          : 'Sign out this device remotely?',
      )
    ) {
      return
    }

    setRevokingId(session.sessionId)
    setSessionsError('')
    try {
      await revokeSessionApi(session.sessionId)
      if (isCurrent) {
        socketClient.disconnect()
        await logout()
        navigate('/login', { replace: true })
        return
      }
      setSessions((previous) =>
        previous.filter((item) => item.sessionId !== session.sessionId),
      )
    } catch (err) {
      setSessionsError(
        err instanceof Error ? err.message : 'Cannot sign out that session',
      )
    } finally {
      setRevokingId(null)
    }
  }

  async function handleChangePassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setPwError('')
    setPwMessage('')

    if (newPassword.length < 8 || newPassword.length > 72) {
      setPwError('New password must be between 8 and 72 characters.')
      return
    }
    if (newPassword !== confirmPassword) {
      setPwError('Passwords do not match.')
      return
    }

    setPwBusy(true)
    try {
      await changePasswordApi({
        currentPassword,
        newPassword,
        revokeOtherSessions: revokeOthers,
      })
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
      setPwMessage('Password changed.')
      if (revokeOthers) refreshSessions()
    } catch (err) {
      setPwError(
        err instanceof Error ? err.message : 'Cannot change password',
      )
    } finally {
      setPwBusy(false)
    }
  }

  async function toggleNotification(key: keyof NotificationPrefs, value: boolean) {
    const previous = notifyPrefs
    setNotifyPrefs({ ...previous, [key]: value })
    setNotifyBusy(true)
    setNotifyError('')
    try {
      const saved = await updateNotificationPrefsApi({ [key]: value })
      if (saved) {
        setNotifyPrefs(saved)
        setNotifyLocalOnly(false)
      } else {
        setNotifyLocalOnly(true)
      }
    } catch (err) {
      setNotifyPrefs(previous)
      setNotifyError(
        err instanceof Error ? err.message : 'Cannot save notification settings',
      )
    } finally {
      setNotifyBusy(false)
    }
  }

  return (
    <main className="activity-page" style={{ overflowY: 'auto' }}>
      <div className="activity-header">
        <div>
          <h1>Settings</h1>
          <p>Devices, password and notifications.</p>
        </div>
      </div>

      <section className="app-card mb-4">
        <div className="app-card-body">
          <div className="d-flex justify-content-between align-items-center mb-3">
            <h2 style={{ fontSize: 16, margin: 0 }}>Active sessions</h2>
            <button
              type="button"
              className="app-button"
              onClick={refreshSessions}
              disabled={loadingSessions}
            >
              Refresh
            </button>
          </div>

          {sessionsError ? (
            <div className="auth-alert auth-alert-error mb-3" role="alert">
              {sessionsError}
            </div>
          ) : null}

          {loadingSessions ? (
            <p className="app-text-muted mb-0">Loading sessions…</p>
          ) : sessions.length === 0 ? (
            <p className="app-text-muted mb-0">No active sessions.</p>
          ) : (
            <ul className="list-group list-group-flush">
              {sessions.map((session) => {
                const isCurrent = session.sessionId === currentToken
                return (
                  <li
                    key={session.sessionId}
                    className="list-group-item d-flex align-items-center gap-3 px-0"
                  >
                    <i className="bi bi-laptop fs-4" aria-hidden="true" />
                    <div className="flex-grow-1 min-w-0">
                      <div className="fw-semibold text-truncate">
                        {session.deviceLabel || session.userAgent || 'Unknown device'}
                        {isCurrent ? (
                          <span className="badge text-bg-primary ms-2">This device</span>
                        ) : null}
                      </div>
                      <div className="app-text-muted small">
                        {session.deviceId ? `Device ${session.deviceId} · ` : ''}
                        Signed in {formatDate(session.createdAt)}
                        {session.lastSeenAt
                          ? ` · last active ${formatDate(session.lastSeenAt)}`
                          : ''}
                        {` · expires ${formatDate(session.expiresAt)}`}
                      </div>
                    </div>
                    <button
                      type="button"
                      className="btn btn-outline-danger btn-sm"
                      disabled={revokingId !== null}
                      onClick={() => void handleRevoke(session)}
                    >
                      {revokingId === session.sessionId
                        ? '…'
                        : isCurrent
                          ? 'Sign out'
                          : 'Sign out device'}
                    </button>
                  </li>
                )
              })}
            </ul>
          )}
        </div>
      </section>

      <section className="app-card mb-4">
        <div className="app-card-body">
          <h2 style={{ fontSize: 16, marginBottom: 12 }}>Change password</h2>
          <form
            className="d-flex flex-column gap-3"
            style={{ maxWidth: 420 }}
            onSubmit={(event) => void handleChangePassword(event)}
          >
            <div>
              <label htmlFor="current-password" className="app-form-label">
                Current password
              </label>
              <input
                id="current-password"
                type="password"
                className="app-input"
                value={currentPassword}
                onChange={(event) => setCurrentPassword(event.target.value)}
                autoComplete="current-password"
                required
              />
            </div>
            <div>
              <label htmlFor="settings-new-password" className="app-form-label">
                New password
              </label>
              <input
                id="settings-new-password"
                type="password"
                className="app-input"
                value={newPassword}
                onChange={(event) => setNewPassword(event.target.value)}
                minLength={8}
                maxLength={72}
                autoComplete="new-password"
                required
              />
            </div>
            <div>
              <label htmlFor="settings-confirm-password" className="app-form-label">
                Confirm new password
              </label>
              <input
                id="settings-confirm-password"
                type="password"
                className="app-input"
                value={confirmPassword}
                onChange={(event) => setConfirmPassword(event.target.value)}
                autoComplete="new-password"
                required
              />
            </div>
            <div className="form-check">
              <input
                id="revoke-others"
                type="checkbox"
                className="form-check-input"
                checked={revokeOthers}
                onChange={(event) => setRevokeOthers(event.target.checked)}
              />
              <label htmlFor="revoke-others" className="form-check-label">
                Sign out my other devices
              </label>
            </div>

            {pwError ? (
              <div className="auth-alert auth-alert-error" role="alert">
                {pwError}
              </div>
            ) : null}
            {pwMessage ? (
              <div className="auth-alert auth-alert-success" role="status">
                {pwMessage}
              </div>
            ) : null}

            <button
              type="submit"
              className="app-button app-button-primary align-self-start"
              disabled={pwBusy}
            >
              {pwBusy ? 'Saving…' : 'Change password'}
            </button>
          </form>
        </div>
      </section>

      <section className="app-card mb-4">
        <div className="app-card-body">
          <h2 style={{ fontSize: 16, marginBottom: 12 }}>Notifications</h2>
          <div className="form-check form-switch">
            <input
              id="notify-push"
              type="checkbox"
              role="switch"
              className="form-check-input"
              checked={notifyPrefs.pushEnabled}
              disabled={notifyBusy}
              onChange={(event) =>
                void toggleNotification('pushEnabled', event.target.checked)
              }
            />
            <label htmlFor="notify-push" className="form-check-label">
              Push notifications
            </label>
          </div>
          <div className="form-check form-switch mt-2">
            <input
              id="notify-preview"
              type="checkbox"
              role="switch"
              className="form-check-input"
              checked={notifyPrefs.previewEnabled}
              disabled={notifyBusy || !notifyPrefs.pushEnabled}
              onChange={(event) =>
                void toggleNotification('previewEnabled', event.target.checked)
              }
            />
            <label htmlFor="notify-preview" className="form-check-label">
              Show message preview
            </label>
          </div>
          {notifyError ? (
            <p className="text-danger small mt-2 mb-0" role="alert">
              {notifyError}
            </p>
          ) : null}
          {notifyLocalOnly ? (
            <p className="app-text-muted small mt-2 mb-0">
              Server does not support notification settings yet. Changes apply
              to this session only.
            </p>
          ) : null}
        </div>
      </section>
    </main>
  )
}

export default SettingsPage
