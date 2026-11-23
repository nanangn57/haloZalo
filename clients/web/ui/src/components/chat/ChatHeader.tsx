import { getInitials } from '@core/avatar'

interface ChatHeaderProps {
  title: string
  subtitle?: string
  avatar?: string
  initials?: string
  type?: 'GROUP' | 'DIRECT'
  onInfoClick?: () => void
  // Mở danh sách media của hội thoại.
  onMediaClick?: () => void
}

function ChatHeader({
  title,
  subtitle,
  avatar,
  initials,
  type = 'DIRECT',
  onMediaClick,
}: ChatHeaderProps) {
  const mark = (
    initials || (title.trim() ? getInitials(title) : type === 'GROUP' ? 'G' : '?')
  )
    .slice(0, 2)
    .toUpperCase()

  return (
    <div className="chat-header-bar">
      {avatar ? (
        <img
          src={avatar}
          alt=""
          className="conversation-avatar-img"
          width={44}
          height={44}
        />
      ) : (
        <span
          className={`conversation-avatar ${
            type === 'GROUP' ? 'is-group' : 'is-direct'
          }`}
          aria-hidden="true"
        >
          {mark}
        </span>
      )}

      <div className="chat-header-copy">
        <div className="chat-header-title">{title}</div>
        {subtitle ? (
          <div className="chat-header-subtitle">{subtitle}</div>
        ) : null}
      </div>

      {onMediaClick ? (
        <button
          type="button"
          className="app-icon-button ms-auto"
          onClick={onMediaClick}
          aria-label="Shared media"
          title="Media"
        >
          <i className="bi bi-images" aria-hidden="true" />
        </button>
      ) : null}
    </div>
  )
}

export default ChatHeader
