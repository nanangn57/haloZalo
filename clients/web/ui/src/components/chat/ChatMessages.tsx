import { useRef, useState } from 'react'
import type { Message } from '@core/types/message'
import type { MessageReaction, ReactionCode } from '@core/messageService'
import { resolveMediaUrl } from '@core/mediaService'
import './ChatMessages.css'

interface ChatMessageProps {
  messages: Message[]
  currentUserId: string
  // Reaction đang gắn theo messageId (reactions API + sự kiện WebSocket)
  reactions?: Record<string, MessageReaction[]>
  // Tên người gửi cho khối trả lời; mặc định "Bạn" / "Người dùng"
  getSenderName?: (userId: string) => string
  onReply?: (message: Message) => void
  onForward?: (message: Message) => void
  onRecall?: (message: Message) => void
  onToggleReaction?: (message: Message, code: ReactionCode) => void
}

const REACTION_CODES: ReactionCode[] = [
  'like',
  'love',
  'haha',
  'wow',
  'sad',
  'angry',
]

const LONG_PRESS_MS = 450

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

  if (message.deleted) {
    return <span className="chat-recalled">Message recalled</span>
  }

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

  if (message.type === 'AUDIO') {
    const url = resolveMediaUrl(message.content)
    if (!url || imageFailed) {
      return (
        <MediaFallback
          label={imageFailed ? 'Voice message unavailable' : 'Voice message'}
          url={imageFailed ? '' : url}
          isMine={isMine}
          icon="bi-mic"
        />
      )
    }
    return (
      <audio
        src={url}
        controls
        className="chat-media-audio"
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

function replySnippet(message: Message | undefined): string {
  if (!message) return 'Original message'
  if (message.deleted) return 'Message recalled'
  switch (message.type) {
    case 'IMAGE':
      return '🖼 Image'
    case 'VIDEO':
      return '🎬 Video'
    case 'DOCUMENT':
      return '📎 File'
    case 'AUDIO':
      return '🎤 Voice message'
    case 'EMOTION':
      return EMOTION_LABEL[message.content] ?? message.content
    default:
      return message.content.length > 80
        ? `${message.content.slice(0, 79)}…`
        : message.content
  }
}

function ChatMessages({
  messages,
  currentUserId,
  reactions = {},
  getSenderName,
  onReply,
  onForward,
  onRecall,
  onToggleReaction,
}: ChatMessageProps) {
  const [menuFor, setMenuFor] = useState<string | null>(null)
  const pressTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  const byMessageId = new Map<string, Message>()
  messages.forEach((message) => {
    if (message.messageId) byMessageId.set(message.messageId, message)
  })

  function startPress(id: string) {
    cancelPress()
    pressTimer.current = setTimeout(() => setMenuFor(id), LONG_PRESS_MS)
  }

  function cancelPress() {
    if (pressTimer.current) {
      clearTimeout(pressTimer.current)
      pressTimer.current = null
    }
  }

  if (messages.length === 0) {
    return (
      <div className="chat-messages chat-messages-empty">
        <p>No messages yet. Say hello.</p>
      </div>
    )
  }

  const senderName = (userId: string) =>
    userId === currentUserId ? 'You' : (getSenderName?.(userId) ?? 'User')

  return (
    <div className="chat-messages" onClick={() => setMenuFor(null)}>
      {messages.map((message) => {
        const isMine = message.senderId === currentUserId
        const isMedia =
          !message.deleted &&
          (message.type === 'IMAGE' ||
            message.type === 'VIDEO' ||
            message.type === 'EMOTION')
        // Tin chưa có messageId (đang gửi / lỗi) chưa thể thu hồi hay react.
        const persisted = Boolean(message.messageId) && message.status !== 'sending'
        const canAct = persisted && !message.deleted
        const reply = message.replyTo ? byMessageId.get(message.replyTo) : undefined
        const messageReactions = message.messageId ? (reactions[message.messageId] ?? []) : []

        const grouped = REACTION_CODES.map((code) => {
          const items = messageReactions.filter((item) => item.code === code)
          return {
            code,
            count: items.length,
            mine: items.some((item) => item.userId === currentUserId),
          }
        }).filter((group) => group.count > 0)

        return (
          <div
            key={message.id}
            className={`chat-row ${isMine ? 'is-mine' : 'is-other'}`}
          >
            <div className="message-item">
              {message.forwardedFrom && !message.deleted ? (
                <div className={`message-forwarded ${isMine ? 'is-mine' : ''}`}>
                  <i className="bi bi-arrow-90deg-right" aria-hidden="true" /> Forwarded
                </div>
              ) : null}

              <div className="message-bubble-wrap">
                <div
                  className={`message-bubble ${
                    isMine ? 'message-mine' : 'message-other'
                  } ${isMedia ? 'is-media' : ''} ${
                    message.type === 'EMOTION' && !message.deleted ? 'is-emotion' : ''
                  } ${message.deleted ? 'is-deleted' : ''}`}
                  onPointerDown={canAct ? () => startPress(message.id) : undefined}
                  onPointerUp={cancelPress}
                  onPointerLeave={cancelPress}
                  onPointerCancel={cancelPress}
                  onContextMenu={
                    canAct
                      ? (event) => {
                          event.preventDefault()
                          setMenuFor(message.id)
                        }
                      : undefined
                  }
                >
                  {message.replyTo && !message.deleted ? (
                    <div className="message-reply-quote">
                      <strong>
                        {reply ? senderName(reply.senderId) : 'Reply'}
                      </strong>
                      <span>{replySnippet(reply)}</span>
                    </div>
                  ) : null}
                  <MessageBody message={message} isMine={isMine} />
                </div>

                {canAct ? (
                  <button
                    type="button"
                    className="message-menu-trigger"
                    aria-label="Message actions"
                    aria-haspopup="menu"
                    aria-expanded={menuFor === message.id}
                    onClick={(event) => {
                      event.stopPropagation()
                      setMenuFor((open) => (open === message.id ? null : message.id))
                    }}
                  >
                    <i className="bi bi-three-dots" aria-hidden="true" />
                  </button>
                ) : null}

                {menuFor === message.id && canAct ? (
                  <div
                    className={`message-menu ${isMine ? 'is-mine' : ''}`}
                    role="menu"
                    onClick={(event) => event.stopPropagation()}
                  >
                    {onToggleReaction ? (
                      <div className="message-menu-reactions">
                        {REACTION_CODES.map((code) => (
                          <button
                            key={code}
                            type="button"
                            className="chat-emotion-chip"
                            title={code}
                            aria-label={`React ${code}`}
                            onClick={() => {
                              setMenuFor(null)
                              onToggleReaction(message, code)
                            }}
                          >
                            {EMOTION_LABEL[code]}
                          </button>
                        ))}
                      </div>
                    ) : null}
                    {onReply ? (
                      <button
                        type="button"
                        role="menuitem"
                        className="message-menu-item"
                        onClick={() => {
                          setMenuFor(null)
                          onReply(message)
                        }}
                      >
                        <i className="bi bi-reply" aria-hidden="true" /> Reply
                      </button>
                    ) : null}
                    {onForward ? (
                      <button
                        type="button"
                        role="menuitem"
                        className="message-menu-item"
                        onClick={() => {
                          setMenuFor(null)
                          onForward(message)
                        }}
                      >
                        <i className="bi bi-arrow-90deg-right" aria-hidden="true" /> Forward
                      </button>
                    ) : null}
                    {onRecall && isMine ? (
                      <button
                        type="button"
                        role="menuitem"
                        className="message-menu-item is-danger"
                        onClick={() => {
                          setMenuFor(null)
                          onRecall(message)
                        }}
                      >
                        <i className="bi bi-arrow-counterclockwise" aria-hidden="true" /> Recall
                      </button>
                    ) : null}
                  </div>
                ) : null}
              </div>

              {grouped.length > 0 ? (
                <div className={`message-reactions ${isMine ? 'is-mine' : ''}`}>
                  {grouped.map((group) => (
                    <button
                      key={group.code}
                      type="button"
                      className={`message-reaction-chip ${group.mine ? 'is-mine' : ''}`}
                      title={group.code}
                      disabled={!onToggleReaction || !canAct}
                      onClick={() => onToggleReaction?.(message, group.code)}
                    >
                      {EMOTION_LABEL[group.code]} {group.count}
                    </button>
                  ))}
                </div>
              ) : null}

              <div className={`message-time ${isMine ? 'is-mine' : ''}`}>
                {new Date(message.sentAt * 1000).toLocaleTimeString([], {
                  hour: '2-digit',
                  minute: '2-digit',
                })}
                {message.status === 'failed' ? ' · failed' : ''}
              </div>
            </div>
          </div>
        )
      })}
    </div>
  )
}

export default ChatMessages
