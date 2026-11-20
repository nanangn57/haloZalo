import type { Conversation } from '@core/types/conversation'

interface ConversationItemProps {
  conversation: Conversation
  title?: string
  avatar?: string
  initials?: string
  active: boolean
  onClick: (conversation: Conversation) => void
}

function ConversationItem({
  conversation,
  title,
  avatar,
  initials,
  active,
  onClick,
}: ConversationItemProps) {
  const displayTitle =
    title ??
    (conversation.type === 'GROUP'
      ? 'Group conversation'
      : 'Direct conversation')

  const unreadCount = conversation.unreadCount ?? 0
  const mark =
    (initials || displayTitle.trim().charAt(0) || '?').slice(0, 2).toUpperCase()

  return (
    <button
      type="button"
      className={`conversation-item ${active ? 'is-active' : ''}`}
      onClick={() => onClick(conversation)}
    >
      {avatar ? (
        <img
          src={avatar}
          alt=""
          className="conversation-avatar-img"
          width={48}
          height={48}
        />
      ) : (
        <span
          className={`conversation-avatar ${
            conversation.type === 'GROUP' ? 'is-group' : 'is-direct'
          }`}
          aria-hidden="true"
        >
          {mark}
        </span>
      )}

      <span className="conversation-copy">
        <span className="conversation-title">{displayTitle}</span>
        <span className="conversation-meta">
          {conversation.type === 'GROUP'
            ? `${conversation.memberIds.length} member${
                conversation.memberIds.length === 1 ? '' : 's'
              }`
            : 'Direct message'}
        </span>
      </span>

      {unreadCount > 0 ? (
        <span className="conversation-unread">{unreadCount}</span>
      ) : null}
    </button>
  )
}

export default ConversationItem
