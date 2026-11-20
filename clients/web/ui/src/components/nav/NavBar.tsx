
import { useEffect, useState } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'

import type { User } from '../../types/user'
import type { AuthUser } from '@core/types/auth'
import { getMyProfileApi } from '@core/userService'
import { logout } from '@core/authSession'
interface UserItemProps {
  currentUser: User
}

function NavBar({ currentUser }: UserItemProps) {
  const [showProfile, setShowProfile] = useState(false)
  const [profile, setProfile] = useState<AuthUser | null>(null)
  const [loadingProfile, setLoadingProfile] = useState(false)
  const [profileError, setProfileError] = useState<string | null>(null)

  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }
  // Load profile when popup opens.
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
      <nav className="d-flex flex-column align-items-center vh-100 border-end p-2 flex-shrink-0">

        {/* Profile avatar */}
        <button
          type="button"
          className="btn p-0 border-0 bg-transparent mb-4"
          onClick={() => setShowProfile(true)}
          title="My Profile"
        >
          <img
            src={currentUser.avatar}
            alt={currentUser.name}
            className="rounded-circle"
            width="48"
            height="48"
            style={{ objectFit: 'cover' }}
          />
        </button>

        {/* Main navigation */}
        <div className="d-flex flex-column align-items-center gap-3">
          <NavLink
            to="/chat"
            className={({ isActive }) =>
              `btn app-button ${isActive ? 'active' : ''}`
            }
            title="Chat"
          >
            <i className="bi bi-chat fs-4"></i>
          </NavLink>

          <NavLink
            to="/contacts/friends"
            className={({ isActive }) =>
              `btn app-button ${
                isActive ||
                window.location.pathname.startsWith('/contacts')
                  ? 'active'
                  : ''
              }`
            }
            title="Contacts"
          >
            <i className="bi bi-people fs-4"></i>
          </NavLink>
        </div>

        {/* Bottom navigation */}
        <div className="d-flex flex-column align-items-center gap-3 mt-auto">
          <NavLink
            to="/dashboard"
            className={({ isActive }) =>
              `btn app-button ${isActive ? 'active' : ''}`
            }
            title="Dashboard / Statistics"
          >
            <i className="bi bi-bar-chart fs-4"></i>
          </NavLink>

          <button
            type="button"
            className="btn app-button"
            title="Logout"
            onClick={handleLogout}
          >
            <i className="bi bi-box-arrow-right fs-4"></i>
          </button>
        </div>
      </nav>

      {/* ====================================== */}
      {/* PROFILE POPUP */}
      {/* ====================================== */}

      {showProfile && (
        <div
          className="position-fixed top-0 start-0 w-100 h-100 d-flex align-items-center justify-content-center"
          style={{
            backgroundColor: 'rgba(0, 0, 0, 0.45)',
            zIndex: 2000,
          }}
          onClick={() => setShowProfile(false)}
        >
          <div
            className="bg-white rounded-4 shadow p-4"
            style={{
              width: 'min(420px, calc(100% - 32px))',
            }}
            onClick={(event) => event.stopPropagation()}
          >
            {/* Popup header */}
            <div className="d-flex justify-content-between align-items-center mb-4">
              <h5 className="mb-0 fw-semibold">
                My Profile
              </h5>

              <button
                type="button"
                className="btn-close"
                aria-label="Close"
                onClick={() => setShowProfile(false)}
              />
            </div>

            {/* Avatar and display name */}
            <div className="d-flex flex-column align-items-center mb-4">
              <img
                src={currentUser.avatar}
                alt="Profile avatar"
                className="rounded-circle mb-3"
                width="88"
                height="88"
                style={{ objectFit: 'cover' }}
              />

              <h5 className="mb-1">{displayName}</h5>

              <span className="text-muted small">
                @{username}
              </span>
            </div>

            {/* Loading */}
            {loadingProfile && (
              <div className="text-center text-muted py-3">
                <div
                  className="spinner-border spinner-border-sm me-2"
                  role="status"
                />
                Loading profile...
              </div>
            )}

            {/* Error */}
            {profileError && (
              <div className="alert alert-danger small">
                {profileError}
              </div>
            )}

            {/* Profile information */}
            {!loadingProfile && !profileError && profile && (
              <div className="d-flex flex-column gap-3">
                <div>
                  <label className="form-label text-muted small mb-1">
                    Display name
                  </label>
                  <div className="form-control bg-light">
                    {profile.displayName || 'Not set'}
                  </div>
                </div>

                <div>
                  <label className="form-label text-muted small mb-1">
                    Username
                  </label>
                  <div className="form-control bg-light">
                    {username}
                  </div>
                </div>

                <div>
                  <label className="form-label text-muted small mb-1">
                    Email
                  </label>
                  <div className="form-control bg-light">
                    {email}
                  </div>
                </div>

                <div>
                  <label className="form-label text-muted small mb-1">
                    User ID
                  </label>
                  <div
                    className="form-control bg-light text-muted"
                    style={{
                      fontSize: '0.8rem',
                      overflowWrap: 'anywhere',
                    }}
                  >
                    {userId}
                  </div>
                </div>
              </div>
            )}

            {/* Footer */}
            <div className="d-flex justify-content-end mt-4">
              <button
                type="button"
                className="btn btn-outline-secondary"
                onClick={() => setShowProfile(false)}
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}

export default NavBar
