import { useState } from 'react'
import type { Message } from '@core/types/message'
import { resolveMediaUrl } from '@core/mediaService'
import './ChatMessages.css'

interface ChatMessageProps {
  messages: Message[]
  currentUserId: string
}

const EMOTION_LABEL: Record<string, string> = {
  like: '👍',
  love: '❤️',
  haha: '😄',
  wow: '😮',
  sad: '😢',
  angry: '😠',
}

function MediaFallback({
  label,
  url,
  isMine,
  icon = 'bi-image',
}: {
  label: string
  url: string
  isMine: boolean
  icon?: string
}) {
  const className = `chat-media-fallback ${isMine ? 'is-mine' : ''}`
  const body = (
    <>
      <i className={`bi ${icon}`} aria-hidden="true" />
      <span>{label}</span>
    </>
  )
  if (!url) {
    return <div className={className}>{body}</div>
  }
  return (
    <a href={url} target="_blank" rel="noreferrer" className={className}>
      {body}
    </a>
  )
}

function MessageBody({
  message,
  isMine,
}: {
  message: Message
  isMine: boolean
}) {
  const [imageFailed, setImageFailed] = useState(false)

  if (message.type === 'EMOTION') {
    return (
      <span className="chat-emotion" title={message.content}>
        {EMOTION_LABEL[message.content] ?? message.content}
      </span>
    )
  }

  if (message.type === 'IMAGE') {
    const url = resolveMediaUrl(message.content)
    if (!url || imageFailed) {
      return (
        <MediaFallback
          label={imageFailed ? 'Image unavailable' : 'Open image'}
          url={imageFailed ? '' : url}
          isMine={isMine}
        />
      )
    }
    return (
      <a href={url} target="_blank" rel="noreferrer" className="chat-media-link">
        <img
          src={url}
          alt="Shared image"
          className="chat-media-image"
          loading="lazy"
          onError={() => setImageFailed(true)}
        />
      </a>
    )
  }

  if (message.type === 'VIDEO') {
    const url = resolveMediaUrl(message.content)
    if (!url || imageFailed) {
      return (
        <MediaFallback
          label={imageFailed ? 'Video unavailable' : 'Open video'}
          url={url}
          isMine={isMine}
          icon="bi-camera-video"
        />
      )
    }
    return (
      <video
        src={url}
        controls
        className="chat-media-video"
        preload="metadata"
        onError={() => setImageFailed(true)}
      />
    )
  }

  if (message.type === 'DOCUMENT') {
    const url = resolveMediaUrl(message.content)
    return (
      <a
        href={url || undefined}
        target="_blank"
        rel="noreferrer"
        className={`chat-doc-link ${isMine ? 'is-mine' : ''}`}
      >
        <i className="bi bi-file-earmark-text" aria-hidden="true" />
        <span>Open file</span>
      </a>
    )
  }

  return <>{message.content}</>
}

function ChatMessages({ messages, currentUserId }: ChatMessageProps) {
  if (messages.length === 0) {
    return (
      <div className="chat-messages chat-messages-empty">
        <p>No messages yet. Say hello.</p>
      </div>
    )
  }

  return (
    <div className="chat-messages">
      {messages.map((message) => {
        const isMine = message.senderId === currentUserId
        const isMedia =
          message.type === 'IMAGE' ||
          message.type === 'VIDEO' ||
          message.type === 'EMOTION'

        return (
          <div
            key={message.id}
            className={`chat-row ${isMine ? 'is-mine' : 'is-other'}`}
          >
            <div className="message-item">
              <div
                className={`message-bubble ${
                  isMine ? 'message-mine' : 'message-other'
                } ${isMedia ? 'is-media' : ''} ${
                  message.type === 'EMOTION' ? 'is-emotion' : ''
                }`}
              >
                <MessageBody message={message} isMine={isMine} />
              </div>

              <div className={`message-time ${isMine ? 'is-mine' : ''}`}>
                {new Date(message.sentAt * 1000).toLocaleTimeString([], {
                  hour: '2-digit',
                  minute: '2-digit',
                })}
              </div>
            </div>
          </div>
        )
      })}
    </div>
  )
}

export default ChatMessages
