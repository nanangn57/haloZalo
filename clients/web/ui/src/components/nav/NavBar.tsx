import { useEffect, useState } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'

import type { User } from '../../types/user'
import type { AuthUser } from '@core/types/auth'
import { getMyProfileApi, updateProfileApi } from '@core/userService'
import { isPlatformAdmin, logout, updateStoredUser } from '@core/authSession'
import { socketClient } from '@core/socketClient'
import Avatar from '../common/Avatar'

interface UserItemProps {
  currentUser: User
}

function NavBar({ currentUser }: UserItemProps) {
  const [showProfile, setShowProfile] = useState(false)
  const [profile, setProfile] = useState<AuthUser | null>(null)
  const [loadingProfile, setLoadingProfile] = useState(false)
  const [profileError, setProfileError] = useState<string | null>(null)
  const [editName, setEditName] = useState('')
  const [editAvatar, setEditAvatar] = useState('')
  const [savingProfile, setSavingProfile] = useState(false)
  const [saveMessage, setSaveMessage] = useState<string | null>(null)

  const navigate = useNavigate()

  const [loggingOut, setLoggingOut] = useState(false)

  // Gọi server xoá session rồi mới xoá token local.
  const handleLogout = async () => {
    setLoggingOut(true)
    try {
      socketClient.disconnect()
      await logout()
    } finally {
      setLoggingOut(false)
      navigate('/login', { replace: true })
    }
  }

  useEffect(() => {
    if (!showProfile) return

    let isMounted = true

    async function loadProfile() {
      try {
        setLoadingProfile(true)
        setProfileError(null)

        const data = await getMyProfileApi()

        if (isMounted) {
          setProfile(data)
          setEditName(data.displayName || '')
          setEditAvatar(data.avatar || '')
          setSaveMessage(null)
        }
      } catch (error) {
        if (isMounted) {
          setProfileError(
            error instanceof Error
              ? error.message
              : 'Cannot load profile.',
          )
        }
      } finally {
        if (isMounted) {
          setLoadingProfile(false)
        }
      }
    }

    void loadProfile()

    return () => {
      isMounted = false
    }
  }, [showProfile])

  const displayName =
    profile?.displayName ||
    profile?.username ||
    currentUser.name ||
    'User'

  const username = profile?.username || '—'
  const email = profile?.email || '—'
  const userId = profile?.userId || '—'

  return (
    <>
      <nav className="app-nav-rail" aria-label="Main">
        <div className="app-nav-rail-top">
          <button
            type="button"
            className="app-nav-profile-button"
            onClick={() => setShowProfile(true)}
            title="My Profile"
            aria-label="Open profile"
          >
            <Avatar
              name={profile?.displayName || currentUser.name}
              src={profile?.avatar || currentUser.avatar}
              size={40}
              className="app-nav-avatar"
            />
          </button>
        </div>

        <div className="app-nav-rail-mid">
          <NavLink
            to="/chat"
            className={({ isActive }) =>
              `app-icon-button ${isActive ? 'active' : ''}`
            }
            title="Chat"
          >
            <i className="bi bi-chat-dots" aria-hidden="true" />
            <span className="visually-hidden">Chat</span>
          </NavLink>

          <NavLink
            to="/contacts/friends"
            className={({ isActive }) =>
              `app-icon-button ${
                isActive ||
                window.location.pathname.startsWith('/contacts')
                  ? 'active'
                  : ''
              }`
            }
            title="Contacts"
          >
            <i className="bi bi-people" aria-hidden="true" />
            <span className="visually-hidden">Contacts</span>
          </NavLink>
        </div>

        <div className="app-nav-rail-bottom">
          <NavLink
            to="/dashboard"
            className={({ isActive }) =>
              `app-icon-button ${isActive ? 'active' : ''}`
            }
            title="Your activity"
          >
            <i className="bi bi-person-lines-fill" aria-hidden="true" />
            <span className="visually-hidden">Your activity</span>
          </NavLink>

          {isPlatformAdmin() ? (
            <NavLink
              to="/admin"
              className={({ isActive }) =>
                `app-icon-button ${isActive ? 'active' : ''}`
              }
              title="Platform stats"
            >
              <i className="bi bi-bar-chart" aria-hidden="true" />
              <span className="visually-hidden">Platform stats</span>
            </NavLink>
          ) : null}

          <NavLink
            to="/settings"
            className={({ isActive }) =>
              `app-icon-button ${isActive ? 'active' : ''}`
            }
            title="Settings"
          >
            <i className="bi bi-gear" aria-hidden="true" />
            <span className="visually-hidden">Settings</span>
          </NavLink>

          <button
            type="button"
            className="app-icon-button"
            title="Logout"
            aria-label="Log out"
            disabled={loggingOut}
            onClick={() => void handleLogout()}
          >
            <i className="bi bi-box-arrow-right" aria-hidden="true" />
          </button>
        </div>
      </nav>

      {showProfile ? (
        <div
          className="app-modal-backdrop"
          onClick={() => setShowProfile(false)}
          role="presentation"
        >
          <div
            className="app-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="profile-dialog-title"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="app-modal-header">
              <h2 id="profile-dialog-title">My Profile</h2>
              <button
                type="button"
                className="app-icon-button"
                aria-label="Close"
                onClick={() => setShowProfile(false)}
              >
                <i className="bi bi-x-lg" aria-hidden="true" />
              </button>
            </div>

            <div className="d-flex flex-column align-items-center mb-4">
              <Avatar
                name={displayName}
                src={editAvatar.trim() || profile?.avatar || currentUser.avatar}
                size={88}
                className="mb-3"
              />
              <h3 className="mb-1" style={{ fontSize: 18 }}>
                {displayName}
              </h3>
              <span className="app-text-muted" style={{ fontSize: 13 }}>
                @{username}
              </span>
            </div>

            {loadingProfile ? (
              <div className="app-loading" style={{ minHeight: 80 }}>
                <div className="app-spinner" />
                <p>Loading profile…</p>
              </div>
            ) : null}

            {profileError ? (
              <div className="auth-alert auth-alert-error" role="alert">
                {profileError}
              </div>
            ) : null}

            {!loadingProfile && !profileError && profile ? (
              <div className="d-flex flex-column gap-3">
                <div>
                  <label className="app-form-label" htmlFor="edit-display-name">
                    Display name
                  </label>
                  <input
                    id="edit-display-name"
                    className="app-input app-input-profile"
                    value={editName}
                    onChange={(event) => setEditName(event.target.value)}
                    maxLength={64}
                  />
                </div>

                <div>
                  <label className="app-form-label" htmlFor="edit-avatar">
                    Avatar URL
                  </label>
                  <input
                    id="edit-avatar"
                    className="app-input app-input-profile"
                    value={editAvatar}
                    onChange={(event) => setEditAvatar(event.target.value)}
                    placeholder="https://… (để trống = chữ cái viết tắt)"
                  />
                </div>

                <div>
                  <span className="app-form-label">Username</span>
                  <div className="app-readonly-field">{username}</div>
                </div>

                <div>
                  <span className="app-form-label">Email</span>
                  <div className="app-readonly-field">{email}</div>
                </div>

                <div>
                  <span className="app-form-label">User ID</span>
                  <div
                    className="app-readonly-field"
                    style={{ fontSize: '0.8rem' }}
                  >
                    {userId}
                  </div>
                </div>

                {saveMessage ? (
                  <div className="auth-alert auth-alert-success" role="status">
                    {saveMessage}
                  </div>
                ) : null}
              </div>
            ) : null}

            <div className="d-flex justify-content-end gap-2 mt-4">
              <button
                type="button"
                className="app-button"
                onClick={() => setShowProfile(false)}
              >
                Close
              </button>
              <button
                type="button"
                className="app-button app-button-primary"
                disabled={savingProfile || loadingProfile || !profile}
                onClick={() => {
                  void (async () => {
                    setSavingProfile(true)
                    setProfileError(null)
                    setSaveMessage(null)
                    try {
                      const updated = await updateProfileApi({
                        displayName: editName.trim() || null,
                        avatar: editAvatar.trim() || null,
                      })
                      setProfile(updated)
                      updateStoredUser(updated)
                      setSaveMessage('Profile saved')
                    } catch (error) {
                      setProfileError(
                        error instanceof Error
                          ? error.message
                          : 'Cannot save profile',
                      )
                    } finally {
                      setSavingProfile(false)
                    }
                  })()
                }}
              >
                {savingProfile ? 'Saving…' : 'Save'}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </>
  )
}

export default NavBar
