import type { Conversation } from '@core/types/conversation'

interface ConversationItemProps {
  conversation: Conversation
  title?: string
  avatar?: string
  // Shown in the circle when there is no avatar.
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

  return (
    <button
      type="button"
      className={`list-group-item list-group-item-action d-flex align-items-center ${
        active ? 'active' : ''
      }`}
      onClick={() => onClick(conversation)}
    >
      {/* Avatar */}
      {avatar ? (
        <img
          src={avatar}
          alt={displayTitle}
          className="rounded-circle me-2"
          width="48"
          height="48"
        />
      ) : (
        <div
          className="rounded-circle me-2 d-flex align-items-center justify-content-center bg-secondary-subtle"
          style={{ width: 48, height: 48, flexShrink: 0 }}
        >
          {initials ? (
            <span className="fw-semibold fs-5">{initials}</span>
          ) : (
            <i
              className={
                conversation.type === 'GROUP'
                  ? 'bi bi-people fs-4'
                  : 'bi bi-person fs-4'
              }
            />
          )}
        </div>
      )}

      {/* Conversation information */}
      <div className="flex-grow-1 text-start overflow-hidden">
        <div className="fw-semibold text-truncate">
          {displayTitle}
        </div>

        <div className="small text-truncate">
          {conversation.type === 'GROUP'
            ? `${conversation.memberIds.length} members`
            : 'Direct message'}
        </div>
      </div>

      {/* Unread count */}
      <div style={{ minWidth: 24, textAlign: 'center' }}>
        {unreadCount > 0 && (
          <span className="badge text-bg-primary rounded-pill">
            {unreadCount}
          </span>
        )}
      </div>
    </button>
  )
}

export default ConversationItem