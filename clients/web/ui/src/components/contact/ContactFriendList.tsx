import type { User } from '../../types/user'

interface ContactFriendProps {
  user: User
  onSelect?: () => void
  disabled?: boolean
}

function ContactFriendlist({
  user,
  onSelect,
  disabled,
}: ContactFriendProps) {
  return (
    <li className="list-group-item p-0 border-0">
      <div className="contact-row">
        <button
          type="button"
          className="btn app-button flex-grow-1 d-flex align-items-center text-start"
          onClick={onSelect}
          disabled={disabled}
        >
          <img
            src={user.avatar}
            alt={user.name}
            className="rounded-circle me-2"
            width="48"
            height="48"
          />

          <div className="fw-semibold">{user.name}</div>
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
      </div>
    </li>
  )
}

export default ContactFriendlist
