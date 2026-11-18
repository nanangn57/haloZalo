
interface ChatHeaderProps {
  title: string
  subtitle?: string
  avatar?: string
  type?: 'GROUP' | 'DIRECT'
  showInfoButton?: boolean
  onInfoClick?: () => void
}

function ChatHeader({
  title,
  subtitle,
  avatar,
  type = 'DIRECT',
  showInfoButton = true,
  onInfoClick,
}: ChatHeaderProps) {
  return (
    <div className="d-flex align-items-center w-100 gap-2">
      {avatar ? (
        <img
          src={avatar}
          alt={title}
          className="rounded-circle flex-shrink-0"
          width="48"
          height="48"
        />
      ) : (
        <div
          className="rounded-circle d-flex align-items-center justify-content-center bg-secondary-subtle flex-shrink-0"
          style={{ width: 48, height: 48 }}
        >
          <i
            className={`bi ${
              type === 'GROUP' ? 'bi-people' : 'bi-person'
            } fs-4`}
          />
        </div>
      )}

      <div className="flex-grow-1 overflow-hidden">
        <div className="fw-semibold text-truncate">
          {title}
        </div>

        {subtitle && (
          <div className="small text-muted text-truncate">
            {subtitle}
          </div>
        )}
      </div>

      {/* <div className="ms-auto d-flex align-items-center gap-2">
        {showInfoButton && (
          <button
            type="button"
            className="btn app-button flex-shrink-0"
            onClick={onInfoClick}
            aria-label="Show conversation information"
          >
            <i className="bi bi-layout-sidebar fs-4" />
          </button>
        )}
      </div> */}
    </div>
  )
}

export default ChatHeader
