import { useState } from 'react'

import ContactHeader from '../components/contact/ContactHeader'
import type { ContactHeaderConfig } from '../types/contact'
import type { AuthUser } from '@core/types/auth'
import { findUserApi } from '@core/userService'
import {
  acceptFriendRequestApi,
  sendFriendRequestApi,
} from '@core/friendService'

function FriendRequestPage() {
  const [query, setQuery] = useState('')
  const [found, setFound] = useState<AuthUser | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  const headerSection: ContactHeaderConfig = {
    header: 'Friend requests',
    icon: 'bi bi-person-plus fs-4',
  }

  async function handleSearch(event: React.FormEvent) {
    event.preventDefault()
    const trimmed = query.trim()
    if (!trimmed) return

    setBusy(true)
    setError('')
    setMessage('')
    try {
      const user = await findUserApi(trimmed)
      setFound(user)
    } catch (err) {
      setFound(null)
      setError(err instanceof Error ? err.message : 'Cannot find user')
    } finally {
      setBusy(false)
    }
  }

  async function sendRequest() {
    if (!found) return
    setBusy(true)
    setError('')
    setMessage('')
    try {
      await sendFriendRequestApi(found.userId)
      setMessage(`Sent friend request to ${found.displayName || found.username}`)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Cannot send request')
    } finally {
      setBusy(false)
    }
  }

  async function acceptRequest() {
    if (!found) return
    setBusy(true)
    setError('')
    setMessage('')
    try {
      const friend = await acceptFriendRequestApi(found.userId)
      setMessage(`You are now friends with ${friend.displayName || friend.username}`)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Cannot accept request')
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="d-flex flex-column h-100 overflow-hidden" style={{ minWidth: 0 }}>
      <ContactHeader contactScreen={headerSection} />

      <form className="p-3 border-bottom" onSubmit={handleSearch}>
        <div className="input-group">
          <input
            className="form-control"
            placeholder="Username or email"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
          <button className="btn btn-primary" type="submit" disabled={busy || !query.trim()}>
            {busy ? '…' : 'Search'}
          </button>
        </div>
      </form>

      <div className="p-3 flex-grow-1 overflow-auto">
        {error ? <div className="alert alert-danger py-2">{error}</div> : null}
        {message ? <div className="alert alert-success py-2">{message}</div> : null}

        {found ? (
          <div className="card shadow-sm">
            <div className="card-body d-flex align-items-center gap-3">
              <img
                src={`https://api.dicebear.com/9.x/initials/svg?seed=${encodeURIComponent(found.username)}`}
                alt={found.username}
                className="rounded-circle"
                width={56}
                height={56}
              />
              <div className="flex-grow-1">
                <div className="fw-semibold">{found.displayName || found.username}</div>
                <div className="text-muted small">@{found.username}</div>
              </div>
              <button
                type="button"
                className="btn btn-outline-primary"
                disabled={busy}
                onClick={() => void sendRequest()}
              >
                Send request
              </button>
              <button
                type="button"
                className="btn btn-primary"
                disabled={busy}
                onClick={() => void acceptRequest()}
              >
                Accept
              </button>
            </div>
          </div>
        ) : (
          <p className="text-muted">
            Search a user to send a friend request, or accept an incoming request from them.
          </p>
        )}
      </div>
    </main>
  )
}

export default FriendRequestPage
