interface MesToolbarProps {
    onEmojiClick?: () => void,
    onImageClick?: () => void,
    onFileClick?: () => void
}

function MesToolbar({
    onEmojiClick,
    onImageClick,
    onFileClick
}: MesToolbarProps) {
    return (
        <div className="d-flex align-items-center gap-1 border-bottom flex-shrink-0">
            <button 
                type="button"
                className="btn app-button"
                onClick={onEmojiClick}>
                    <i className="bi bi-emoji-smile fs-4"/>
            </button>

            <button 
                type="button"
                className="btn app-button"
                onClick={onImageClick}>
                    <i className="bi bi-image fs-4"/>
            </button>

            <button 
                type="button"
                className="btn app-button"
                onClick={onFileClick}>
                    <i className="bi bi-paperclip fs-4"/>
            </button>
        </div>
    )
}

export default MesToolbar