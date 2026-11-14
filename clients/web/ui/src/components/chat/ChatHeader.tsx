
interface ChatHeaderProps {
  title: string
  subtitle?: string
  avatar?: string
  type?: 'GROUP' | 'DIRECT'
  onInfoClick: () => void
}

function ChatHeader({
  title,
  avatar,
  type = 'DIRECT',
  onInfoClick,
}: ChatHeaderProps) {
  return (
    <div className="d-flex align-items-center w-100">
      {avatar ? (
        <img
          src={avatar}
          alt={title}
          className="rounded-circle me-2"
          width="48"
          height="48"
        />
      ) : (
        <div
          className="rounded-circle me-2 d-flex align-items-center justify-content-center bg-secondary-subtle"
          style={{ width: '48px', height: '48px', flexShrink: 0 }}
        >
          <i
            className={`bi ${
              type === 'GROUP' ? 'bi-people' : 'bi-person'
            } fs-4`}
          />
        </div>
      )}

      <div className="fw-semibold text-truncate">
        {title}
      </div>

      <div className="ms-auto d-flex align-items-center gap-3">
        <button
          type="button"
          className="btn app-button flex-shrink-0"
          aria-label="Search messages"
        >
          <i className="bi bi-search fs-4" />
        </button>

        <button
          type="button"
          className="btn app-button flex-shrink-0"
          onClick={onInfoClick}
          aria-label="Show conversation information"
        >
          <i className="bi bi-layout-sidebar fs-4" />
        </button>
      </div>
    </div>
  )
}

export default ChatHeader
