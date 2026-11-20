import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'

import ContactHeader from '../components/contact/ContactHeader'
import ContactFriendlist from '../components/contact/ContactFriendList'

import type { ContactHeaderConfig } from '../types/contact'
import type { User } from '../types/user'
import type { AuthUser } from '@core/types/auth'
import type { Friend } from '@core/friendService'

import { findUserApi } from '@core/userService'
import { listFriendsApi } from '@core/friendService'
import { createDirectConversationApi } from '@core/conversationService'

function toContactUser(found: AuthUser | Friend): User {
  const username = 'username' in found ? found.username : found.userId
  return {
    id: found.userId,
    name: found.displayName || username,
    avatar: `https://api.dicebear.com/9.x/initials/svg?seed=${encodeURIComponent(username)}`,
    online: false,
    lastSeen: Math.floor(Date.now() / 1000),
    unread: 0,
  }
}

function ContactPage() {
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<User[]>([])
  const [friends, setFriends] = useState<User[]>([])
  const [searching, setSearching] = useState(false)
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
    return () => {
      mounted = false
    }
  }, [])

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

  const shown = results.length > 0 ? results : friends

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
            onChange={(event) => setQuery(event.target.value)}
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
      </form>

      <ul className="list-group flex-grow-1 overflow-auto">
        {shown.length === 0 ? (
          <li className="list-group-item text-muted border-0">
            No friends yet. Use Friend request to invite someone.
          </li>
        ) : (
          shown.map((user) => (
            <ContactFriendlist
              key={user.id}
              user={user}
              onSelect={() => void openDirect(user)}
              disabled={openingId === user.id}
            />
          ))
        )}
      </ul>
    </main>
  )
}

export default ContactPage
