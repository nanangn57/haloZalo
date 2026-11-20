import type { Message, MessageMedia } from "@core/types/message";
import "./ChatMessages.css";

interface ChatMessageProps {
    messages: Message[];
    currentUserId: string;
}

function formatSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function MediaContent({ media }: { media: MessageMedia }) {
    if (media.kind === "IMAGE") {
        return (
            <a href={media.url} target="_blank" rel="noreferrer">
                <img
                    className="message-image"
                    src={media.url}
                    alt={media.fileName}
                    width={media.width}
                    height={media.height}
                    loading="lazy"
                />
            </a>
        );
    }

    if (media.kind === "VIDEO") {
        return (
            <video
                className="message-video"
                src={media.url}
                controls
                preload="metadata"
            />
        );
    }

    return (
        <a
            className="message-document d-flex align-items-center gap-2 text-reset text-decoration-none"
            href={media.url}
            download={media.fileName}
        >
            <i className="bi bi-file-earmark-text fs-3" />
            <span className="d-flex flex-column min-w-0">
                <span className="text-truncate fw-semibold">{media.fileName}</span>
                <small className="opacity-75">{formatSize(media.size)}</small>
            </span>
        </a>
    );
}

function ChatMessages({
    messages,
    currentUserId
}: ChatMessageProps) {
    return (
        <div className="chat-messages flex-grow-1 overflow-auto p-3">
            {
                messages.map((message) => {
                    const isMine = message.senderId === currentUserId;
                    return (
                        <div
                            key={message.id}
                            className={`d-flex mb-3 ${
                                isMine ? 'justify-content-end' : 'justify-content-start'
                            }`}
                        >
                            <div className="message-item">
                                <div
                                    className={`message-bubble ${
                                    isMine ? 'message-mine' : 'message-other'
                                    } ${message.status === 'sending' ? 'message-sending' : ''}`}
                                >
                                    {message.media && <MediaContent media={message.media} />}
                                    {message.content && (
                                        <div className={message.media ? 'mt-1' : undefined}>
                                            {message.content}
                                        </div>
                                    )}
                                </div>

                                <div
                                    className={`small mt-1 ${
                                    message.status === 'failed' ? 'text-danger' : 'text-secondary'
                                    } ${isMine ? 'text-end' : 'text-start'}`}
                                >
                                    {message.status === 'failed'
                                        ? 'Not sent'
                                        : new Date(message.sentAt * 1000).toLocaleTimeString(
                                            [],
                                            { hour: '2-digit', minute: '2-digit' },
                                        )}
                                </div>
                            </div>

                        </div>
                    );
                })
            }
        </div>
    );
}
export default ChatMessages
