                            
interface MesToolbarProps {
  onEmojiClick?: () => void
  onImageClick?: () => void
  onFileClick?: () => void
}

function MesToolbar({
  onEmojiClick,
  onImageClick,
  onFileClick,
}: MesToolbarProps) {
  return (
    <div className="d-flex align-items-center gap-1 border-bottom flex-shrink-0">
      {onEmojiClick && (
        <button
          type="button"
          className="btn app-button"
          onClick={onEmojiClick}
          aria-label="Open emoji picker"
        >
          <i className="bi bi-emoji-smile fs-4" />
        </button>
      )}

      {onImageClick && (
        <button
          type="button"
          className="btn app-button"
          onClick={onImageClick}
          aria-label="Attach image"
        >
          <i className="bi bi-image fs-4" />
        </button>
      )}

      {onFileClick && (
        <button
          type="button"
          className="btn app-button"
          onClick={onFileClick}
          aria-label="Attach file"
        >
          <i className="bi bi-paperclip fs-4" />
        </button>
      )}
    </div>
  )
}

export default MesToolbar
