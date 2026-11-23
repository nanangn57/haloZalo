import { useState } from 'react'
import type { User } from '../../types/user'
import Avatar from '../common/Avatar'

interface ContactFriendProps {
  user: User
  onSelect?: () => void
  onUnfriend?: () => void
  onBlock?: () => void
  disabled?: boolean
}

function ContactFriendlist({
  user,
  onSelect,
  onUnfriend,
  onBlock,
  disabled,
}: ContactFriendProps) {
  const [menuOpen, setMenuOpen] = useState(false)

  return (
    <li className="list-group-item p-0 border-0">
      <div className="contact-row">
        <button
          type="button"
          className="btn app-button flex-grow-1 d-flex align-items-center text-start"
          onClick={onSelect}
          disabled={disabled}
        >
          <span className="position-relative me-2 flex-shrink-0">
            <Avatar name={user.name} src={user.avatar} size={48} />
            <span
              className={`position-absolute bottom-0 end-0 rounded-circle border border-2 border-white ${
                user.online ? 'bg-success' : 'bg-secondary'
              }`}
              style={{ width: 14, height: 14 }}
              role="img"
              aria-label={user.online ? 'Online' : 'Offline'}
            />
          </span>

          <div>
            <div className="fw-semibold">{user.name}</div>
            <div className={`small ${user.online ? 'text-success' : 'text-muted'}`}>
              {user.online ? 'Online' : 'Offline'}
            </div>
          </div>
        </button>

        <button
          type="button"
          className="btn app-button flex-shrink-0"
          onClick={onSelect}
          disabled={disabled}
          aria-label="Open chat"
        >
          <i className="bi bi-chat-dots fs-4"></i>
        </button>

        {onUnfriend || onBlock ? (
          <div className="position-relative flex-shrink-0">
            <button
              type="button"
              className="btn app-button"
              aria-expanded={menuOpen}
              onClick={() => setMenuOpen((open) => !open)}
              aria-label={`More actions for ${user.name}`}
              disabled={disabled}
            >
              <i className="bi bi-three-dots-vertical fs-5"></i>
            </button>
            <ul
              className={`dropdown-menu dropdown-menu-end ${menuOpen ? 'show' : ''}`}
              style={{ right: 0, left: 'auto' }}
            >
              {onUnfriend ? (
                <li>
                  <button
                    type="button"
                    className="dropdown-item"
                    onClick={() => {
                      setMenuOpen(false)
                      onUnfriend()
                    }}
                  >
                    Unfriend
                  </button>
                </li>
              ) : null}
              {onBlock ? (
                <li>
                  <button
                    type="button"
                    className="dropdown-item text-danger"
                    onClick={() => {
                      setMenuOpen(false)
                      onBlock()
                    }}
                  >
                    Block
                  </button>
                </li>
              ) : null}
            </ul>
          </div>
        ) : null}
      </div>
    </li>
  )
}

export default ContactFriendlist
