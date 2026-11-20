interface MesToolbarProps {
  disabled?: boolean
  emotionOpen?: boolean
  emojiOpen?: boolean
  onEmojiClick?: () => void
  onEmotionClick?: () => void
  onImageClick?: () => void
  onFileClick?: () => void
  onVideoClick?: () => void
}

function MesToolbar({
  disabled,
  emotionOpen,
  emojiOpen,
  onEmojiClick,
  onEmotionClick,
  onImageClick,
  onFileClick,
  onVideoClick,
}: MesToolbarProps) {
  return (
    <div className="chat-composer-toolbar" role="toolbar" aria-label="Message tools">
      {onEmojiClick ? (
        <button
          type="button"
          className={`app-icon-button ${emojiOpen ? 'active' : ''}`}
          onClick={onEmojiClick}
          disabled={disabled}
          aria-label="Insert emoji"
          aria-pressed={emojiOpen}
          title="Emoji"
        >
          <i className="bi bi-emoji-smile" aria-hidden="true" />
        </button>
      ) : null}

      {onEmotionClick ? (
        <button
          type="button"
          className={`app-icon-button ${emotionOpen ? 'active' : ''}`}
          onClick={onEmotionClick}
          disabled={disabled}
          aria-label="Send reaction"
          aria-pressed={emotionOpen}
          title="Reaction"
        >
          <i className="bi bi-heart" aria-hidden="true" />
        </button>
      ) : null}

      {onImageClick ? (
        <button
          type="button"
          className="app-icon-button"
          onClick={onImageClick}
          disabled={disabled}
          aria-label="Attach image"
          title="Image"
        >
          <i className="bi bi-image" aria-hidden="true" />
        </button>
      ) : null}

      {onFileClick ? (
        <button
          type="button"
          className="app-icon-button"
          onClick={onFileClick}
          disabled={disabled}
          aria-label="Attach file"
          title="File"
        >
          <i className="bi bi-paperclip" aria-hidden="true" />
        </button>
      ) : null}

      {onVideoClick ? (
        <button
          type="button"
          className="app-icon-button"
          onClick={onVideoClick}
          disabled={disabled}
          aria-label="Attach video"
          title="Video"
        >
          <i className="bi bi-camera-video" aria-hidden="true" />
        </button>
      ) : null}
    </div>
  )
}

export default MesToolbar
