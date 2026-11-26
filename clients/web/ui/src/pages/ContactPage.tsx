import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'

import Avatar from '../components/common/Avatar'
import ContactHeader from '../components/contact/ContactHeader'
import ContactFriendlist from '../components/contact/ContactFriendList'

import type { ContactHeaderConfig } from '../types/contact'
import type { User } from '../types/user'
import type { AuthUser } from '@core/types/auth'
import type { Friend } from '@core/friendService'

import { findUserApi } from '@core/userService'
import {
  blockUserApi,
  listBlocksApi,
  listFriendsApi,
  sendFriendRequestApi,
  unblockUserApi,
  unfriendApi,
} from '@core/friendService'
import { createDirectConversationApi } from '@core/conversationService'
import { getPresenceApi } from '@core/presenceService'

const PRESENCE_POLL_MS = 30_000

function toContactUser(found: AuthUser | Friend): User {
  const username = found.username
  return {
    id: found.userId,
    name: found.displayName || username,
    // Không có URL avatar: UI hiện chữ cái viết tắt (Avatar component).
    avatar: 'avatar' in found && found.avatar ? found.avatar : '',
    // Presence nạp riêng qua GET /presence (xem ContactPage), mặc định offline.
    online: false,
    lastSeen: 0,
    unread: 0,
  }
}

function ContactPage() {
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<User[]>([])
  const [friends, setFriends] = useState<User[]>([])
  const [onlineById, setOnlineById] = useState<Record<string, boolean>>({})
  const [searching, setSearching] = useState(false)
  const [blocked, setBlocked] = useState<User[]>([])
  const [showBlocked, setShowBlocked] = useState(false)
  const [openingId, setOpeningId] = useState<string | null>(null)
  const [error, setError] = useState('')

  const headerSection: ContactHeaderConfig = {
    header: 'Friends',
    icon: 'bi bi-person fs-4',
  }

  useEffect(() => {
    let mounted = true
    void listFriendsApi()
      .then((items) => {
        if (mounted) {
          setFriends(items.map(toContactUser))
        }
      })
      .catch((err: unknown) => {
        if (mounted) {
          setError(err instanceof Error ? err.message : 'Cannot load friends')
        }
      })
    void listBlocksApi()
      .then((items) => {
        if (mounted) {
          setBlocked(items.map(toContactUser))
        }
      })
      .catch(() => undefined)
    return () => {
      mounted = false
    }
  }, [])

  // Presence của bạn bè: nạp khi có danh sách, rồi mỗi 30 giây và khi tab được focus lại.
  const friendIdsKey = friends.map((friend) => friend.id).join(',')
  useEffect(() => {
    if (!friendIdsKey) {
      return
    }
    const ids = friendIdsKey.split(',')
    let cancelled = false

    const refresh = () => {
      void getPresenceApi(ids)
        .then((result) => {
          if (cancelled) return
          const next: Record<string, boolean> = {}
          Object.values(result).forEach((item) => {
            next[item.userId] = item.online
          })
          setOnlineById(next)
        })
        .catch(() => undefined)
    }

    refresh()
    const timer = window.setInterval(refresh, PRESENCE_POLL_MS)
    window.addEventListener('focus', refresh)
    return () => {
      cancelled = true
      window.clearInterval(timer)
      window.removeEventListener('focus', refresh)
    }
  }, [friendIdsKey])

  async function handleUnfriend(user: User) {
    if (!window.confirm(`Unfriend ${user.name}?`)) return
    setError('')
    try {
      await unfriendApi(user.id)
      setFriends((previous) => previous.filter((item) => item.id !== user.id))
      setResults((previous) => previous.filter((item) => item.id !== user.id))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Cannot unfriend')
    }
  }

  async function handleAddFriend(user: User) {
    setError('')
    try {
      await sendFriendRequestApi(user.id)
      setError('')
      window.alert(`Sent friend request to ${user.name}`)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Cannot send friend request')
    }
  }

  async function handleBlock(user: User) {
    if (!window.confirm(`Block ${user.name}? They will not be able to message you.`)) return
    setError('')
    try {
      await blockUserApi(user.id)
      setFriends((previous) => previous.filter((item) => item.id !== user.id))
      setResults((previous) => previous.filter((item) => item.id !== user.id))
      setBlocked((previous) => [user, ...previous.filter((item) => item.id !== user.id)])
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Cannot block user')
    }
  }

  async function handleUnblock(user: User) {
    setError('')
    try {
      await unblockUserApi(user.id)
      setBlocked((previous) => previous.filter((item) => item.id !== user.id))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Cannot unblock user')
    }
  }

  async function handleSearch(event: React.FormEvent) {
    event.preventDefault()
    const trimmed = query.trim()
    if (!trimmed) return

    setSearching(true)
    setError('')
    try {
      const found = await findUserApi(trimmed)
      setResults([toContactUser(found)])
    } catch (err) {
      setResults([])
      setError(err instanceof Error ? err.message : 'Cannot find user')
    } finally {
      setSearching(false)
    }
  }

  async function openDirect(user: User) {
    setOpeningId(user.id)
    setError('')
    try {
      const conversation = await createDirectConversationApi({
        userId: user.id,
      })
      navigate('/chat', {
        state: {
          conversationId: conversation.conversationId,
          conversationType: 'DIRECT',
          title: user.name,
        },
      })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Cannot open chat')
    } finally {
      setOpeningId(null)
    }
  }

  const shown = useMemo(
    () =>
      (results.length > 0 ? results : friends).map((user) => ({
        ...user,
        online: onlineById[user.id] ?? false,
      })),
    [results, friends, onlineById],
  )

  return (
    <main
      className="d-flex flex-column h-100 overflow-hidden"
      style={{ minWidth: 0 }}
    >
      <ContactHeader contactScreen={headerSection} />

      <form className="p-3 border-bottom" onSubmit={handleSearch}>
        <div className="input-group">
          <input
            className="form-control"
            placeholder="Username or email"
            value={query}
            onChange={(event) => {
              const next = event.target.value
              setQuery(next)
              if (!next.trim()) {
                setResults([])
              }
            }}
          />
          <button
            className="btn btn-primary"
            type="submit"
            disabled={searching || !query.trim()}
          >
            {searching ? 'Searching…' : 'Search'}
          </button>
        </div>
        {error ? (
          <div className="text-danger small mt-2">{error}</div>
        ) : null}
        {results.length > 0 ? (
          <div className="text-muted small mt-2">Search result — clear the box to see your friend list.</div>
        ) : null}
      </form>

      <ul className="list-group flex-grow-1 overflow-auto">
        {shown.length === 0 ? (
          <li className="list-group-item text-muted border-0">
            No friends yet. Use Friend request to invite someone.
          </li>
        ) : (
          shown.map((user) => {
            const isFriend = friends.some((friend) => friend.id === user.id)
            return (
              <ContactFriendlist
                key={user.id}
                user={user}
                onSelect={() => void openDirect(user)}
                onUnfriend={
                  isFriend ? () => void handleUnfriend(user) : undefined
                }
                onAddFriend={
                  isFriend ? undefined : () => void handleAddFriend(user)
                }
                onBlock={() => void handleBlock(user)}
                disabled={openingId === user.id}
              />
            )
          })
        )}
      </ul>

      {blocked.length > 0 ? (
        <div className="border-top">
          <button
            type="button"
            className="btn btn-link text-decoration-none w-100 text-start px-3 py-2"
            onClick={() => setShowBlocked((open) => !open)}
            aria-expanded={showBlocked}
          >
            Blocked users ({blocked.length})
          </button>
          {showBlocked ? (
            <ul className="list-group list-group-flush">
              {blocked.map((user) => (
                <li
                  key={user.id}
                  className="list-group-item d-flex align-items-center gap-2"
                >
                  <Avatar name={user.name} src={user.avatar} size={36} />
                  <span className="flex-grow-1 text-truncate">{user.name}</span>
                  <button
                    type="button"
                    className="btn btn-outline-secondary btn-sm"
                    onClick={() => void handleUnblock(user)}
                  >
                    Unblock
                  </button>
                </li>
              ))}
            </ul>
          ) : null}
        </div>
      ) : null}
    </main>
  )
}

export default ContactPage
