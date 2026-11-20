interface ChatHeaderProps {
  title: string
  subtitle?: string
  avatar?: string
  initials?: string
  type?: 'GROUP' | 'DIRECT'
  onInfoClick?: () => void
}

function ChatHeader({
  title,
  subtitle,
  avatar,
  initials,
  type = 'DIRECT',
}: ChatHeaderProps) {
  const mark =
    (initials || title.trim().charAt(0) || (type === 'GROUP' ? 'G' : '?'))
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
    </div>
  )
}

export default ChatHeader
