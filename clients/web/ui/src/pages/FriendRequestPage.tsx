import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'

import ContactHeader from '../components/contact/ContactHeader'
import Avatar from '../components/common/Avatar'
import type { ContactHeaderConfig } from '../types/contact'
import type { AuthUser } from '@core/types/auth'
import { findUserApi, lookupUsersApi, type UserSummary } from '@core/userService'
import {
  acceptFriendRequestApi,
  cancelFriendRequestApi,
  listFriendRequestsApi,
  rejectFriendRequestApi,
  sendFriendRequestApi,
  type Friend,
  type FriendRequestList,
} from '@core/friendService'
import { createDirectConversationApi } from '@core/conversationService'

type Tab = 'incoming' | 'outgoing'

function nameOf(user: UserSummary | undefined, fallbackId: string): string {
  return user?.displayName || user?.username || `User ${fallbackId.slice(0, 8)}`
}

function FriendRequestPage() {
  const navigate = useNavigate()
  const [tab, setTab] = useState<Tab>('incoming')
  const [requests, setRequests] = useState<FriendRequestList>({
    incoming: [],
    outgoing: [],
  })
  const [people, setPeople] = useState<Record<string, UserSummary>>({})
  const [loadingList, setLoadingList] = useState(true)
  // Bạn vừa accept trong phiên này: hiện nút mở chat.
  const [accepted, setAccepted] = useState<Friend[]>([])
  const [workingId, setWorkingId] = useState<string | null>(null)

  const [query, setQuery] = useState('')
  const [found, setFound] = useState<AuthUser | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  const headerSection: ContactHeaderConfig = {
    header: 'Friend requests',
    icon: 'bi bi-person-plus fs-4',
  }

  // Tải lời mời + tên người gửi/nhận. Không setState ở đây để effect gọi được.
  const fetchRequests = useCallback(async () => {
    const data = await listFriendRequestsApi()
    const ids = [
      ...data.incoming.map((request) => request.fromUserId),
      ...data.outgoing.map((request) => request.toUserId),
    ]
    const users = ids.length > 0 ? await lookupUsersApi(ids.slice(0, 100)) : []
    return { data, users }
  }, [])

  const applyRequests = useCallback(
    (result: { data: FriendRequestList; users: UserSummary[] }) => {
      setRequests(result.data)
      setPeople((previous) => {
        const next = { ...previous }
        result.users.forEach((user) => {
          next[user.userId] = user
        })
        return next
      })
    },
    [],
  )

  const loadRequests = useCallback(async () => {
    try {
      applyRequests(await fetchRequests())
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Cannot load friend requests')
    }
  }, [applyRequests, fetchRequests])

  useEffect(() => {
    let cancelled = false
    fetchRequests()
      .then((result) => {
        if (!cancelled) applyRequests(result)
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'Cannot load friend requests')
        }
      })
      .finally(() => {
        if (!cancelled) setLoadingList(false)
      })
    return () => {
      cancelled = true
    }
  }, [applyRequests, fetchRequests])

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
      void loadRequests()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Cannot send request')
    } finally {
      setBusy(false)
    }
  }

  async function runAction(userId: string, action: () => Promise<void>) {
    setWorkingId(userId)
    setError('')
    setMessage('')
    try {
      await action()
      await loadRequests()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Action failed')
    } finally {
      setWorkingId(null)
    }
  }

  function accept(userId: string) {
    return runAction(userId, async () => {
      const friend = await acceptFriendRequestApi(userId)
      setAccepted((previous) => [
        friend,
        ...previous.filter((item) => item.userId !== friend.userId),
      ])
      setMessage(`You are now friends with ${friend.displayName || friend.username}`)
    })
  }

  async function openChat(userId: string, title: string) {
    setWorkingId(userId)
    setError('')
    try {
      const conversation = await createDirectConversationApi({ userId })
      navigate('/chat', {
        state: {
          conversationId: conversation.conversationId,
          conversationType: 'DIRECT',
          title,
        },
      })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Cannot open chat')
    } finally {
      setWorkingId(null)
    }
  }

  const list = tab === 'incoming' ? requests.incoming : requests.outgoing

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
          <div className="card shadow-sm mb-3">
            <div className="card-body d-flex align-items-center gap-3">
              <Avatar name={found.displayName || found.username} src={found.avatar} size={56} />
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
            </div>
          </div>
        ) : null}

        {accepted.length > 0 ? (
          <div className="mb-3">
            {accepted.map((friend) => {
              const title = friend.displayName || friend.username
              return (
                <div
                  key={friend.userId}
                  className="card border-success mb-2"
                >
                  <div className="card-body d-flex align-items-center gap-3 py-2">
                    <Avatar name={title} size={40} />
                    <div className="flex-grow-1">
                      <div className="fw-semibold">{title}</div>
                      <div className="text-success small">Now your friend</div>
                    </div>
                    <button
                      type="button"
                      className="btn btn-primary btn-sm"
                      disabled={workingId === friend.userId}
                      onClick={() => void openChat(friend.userId, title)}
                    >
                      <i className="bi bi-chat-dots me-1" aria-hidden="true" />
                      Chat
                    </button>
                  </div>
                </div>
              )
            })}
          </div>
        ) : null}

        <ul className="nav nav-tabs mb-3" role="tablist">
          {(['incoming', 'outgoing'] as const).map((key) => (
            <li className="nav-item" key={key} role="presentation">
              <button
                type="button"
                role="tab"
                aria-selected={tab === key}
                className={`nav-link ${tab === key ? 'active' : ''}`}
                onClick={() => setTab(key)}
              >
                {key === 'incoming' ? 'Incoming' : 'Sent'}
                <span className="badge text-bg-secondary ms-2">
                  {requests[key].length}
                </span>
              </button>
            </li>
          ))}
        </ul>

        {loadingList ? (
          <p className="text-muted">Loading requests…</p>
        ) : list.length === 0 ? (
          <p className="text-muted">
            {tab === 'incoming'
              ? 'No incoming friend requests.'
              : 'You have not sent any pending requests.'}
          </p>
        ) : (
          <ul className="list-group">
            {list.map((request) => {
              const userId = tab === 'incoming' ? request.fromUserId : request.toUserId
              const name = nameOf(people[userId], userId)
              const working = workingId === userId
              return (
                <li
                  key={`${request.fromUserId}-${request.toUserId}`}
                  className="list-group-item d-flex align-items-center gap-3"
                >
                  <Avatar name={name} size={44} />
                  <div className="flex-grow-1 min-w-0">
                    <div className="fw-semibold text-truncate">{name}</div>
                    {people[userId]?.username ? (
                      <div className="text-muted small">@{people[userId].username}</div>
                    ) : null}
                  </div>
                  {tab === 'incoming' ? (
                    <>
                      <button
                        type="button"
                        className="btn btn-primary btn-sm"
                        disabled={working}
                        onClick={() => void accept(userId)}
                      >
                        Accept
                      </button>
                      <button
                        type="button"
                        className="btn btn-outline-secondary btn-sm"
                        disabled={working}
                        onClick={() =>
                          void runAction(userId, () => rejectFriendRequestApi(userId))
                        }
                      >
                        Reject
                      </button>
                    </>
                  ) : (
                    <button
                      type="button"
                      className="btn btn-outline-secondary btn-sm"
                      disabled={working}
                      onClick={() =>
                        void runAction(userId, () => cancelFriendRequestApi(userId))
                      }
                    >
                      Cancel
                    </button>
                  )}
                </li>
              )
            })}
          </ul>
        )}
      </div>
    </main>
  )
}

export default FriendRequestPage
