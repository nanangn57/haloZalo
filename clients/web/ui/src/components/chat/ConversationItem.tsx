import { useEffect, useRef, useState } from 'react'
import type {
  Conversation,
  ConversationPrefsPatch,
} from '@core/types/conversation'
import { getInitials } from '@core/avatar'
import { getLastMessagePreview } from '@core/conversationPreview'
import './ConversationItem.css'

const LONG_PRESS_MS = 500

interface ConversationItemProps {
  conversation: Conversation
  title?: string
  avatar?: string
  initials?: string
  currentUserId?: string
  active: boolean
  onClick: (conversation: Conversation) => void
  // Pin / mute / archive. Không truyền thì không hiện menu.
  onUpdatePrefs?: (
    conversation: Conversation,
    patch: ConversationPrefsPatch,
  ) => void
}

function ConversationItem({
  conversation,
  title,
  avatar,
  initials,
  currentUserId,
  active,
  onClick,
  onUpdatePrefs,
}: ConversationItemProps) {
  const [menuOpen, setMenuOpen] = useState(false)
  const pressTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const longPressed = useRef(false)
  const rowRef = useRef<HTMLDivElement | null>(null)

  // Bấm ra ngoài hàng thì đóng menu.
  useEffect(() => {
    if (!menuOpen) return
    const close = (event: PointerEvent) => {
      if (!rowRef.current?.contains(event.target as Node)) setMenuOpen(false)
    }
    document.addEventListener('pointerdown', close)
    return () => document.removeEventListener('pointerdown', close)
  }, [menuOpen])

  function cancelPress() {
    if (pressTimer.current) {
      clearTimeout(pressTimer.current)
      pressTimer.current = null
    }
  }

  function update(patch: ConversationPrefsPatch) {
    setMenuOpen(false)
    onUpdatePrefs?.(conversation, patch)
  }

  const displayTitle =
    title ??
    (conversation.type === 'GROUP'
      ? 'Group conversation'
      : 'Direct conversation')

  const unreadCount = conversation.unreadCount ?? 0
  const mark = (initials || getInitials(displayTitle)).slice(0, 2).toUpperCase()
  const preview = getLastMessagePreview(conversation, currentUserId)

  return (
    <div className="conversation-row" ref={rowRef}>
    <button
      type="button"
      className={`conversation-item ${active ? 'is-active' : ''}`}
      onClick={() => {
        if (longPressed.current) {
          longPressed.current = false
          return
        }
        onClick(conversation)
      }}
      onPointerDown={
        onUpdatePrefs
          ? (event) => {
              if (event.pointerType === 'mouse') return
              longPressed.current = false
              cancelPress()
              pressTimer.current = setTimeout(() => {
                longPressed.current = true
                setMenuOpen(true)
              }, LONG_PRESS_MS)
            }
          : undefined
      }
      onPointerUp={cancelPress}
      onPointerLeave={cancelPress}
      onPointerCancel={cancelPress}
      onContextMenu={
        onUpdatePrefs
          ? (event) => {
              event.preventDefault()
              setMenuOpen(true)
            }
          : undefined
      }
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
          {preview ??
            (conversation.type === 'GROUP'
              ? `${conversation.memberIds.length} member${
                  conversation.memberIds.length === 1 ? '' : 's'
                }`
              : 'Direct message')}
        </span>
      </span>

      {conversation.muted ? (
        <i className="bi bi-bell-slash text-muted small" aria-label="Muted" />
      ) : null}
      {conversation.pinned ? (
        <i className="bi bi-pin-angle text-muted small" aria-label="Pinned" />
      ) : null}

      {unreadCount > 0 ? (
        <span className="conversation-unread">{unreadCount}</span>
      ) : null}
    </button>

    {onUpdatePrefs ? (
      <>
        <button
          type="button"
          className="conversation-more"
          aria-label="Conversation options"
          aria-haspopup="menu"
          aria-expanded={menuOpen}
          onClick={() => setMenuOpen((open) => !open)}
        >
          <i className="bi bi-three-dots" aria-hidden="true" />
        </button>
        {menuOpen ? (
          <div className="conversation-menu" role="menu">
            <button
              type="button"
              role="menuitem"
              className="conversation-menu-item"
              onClick={() => update({ pinned: !conversation.pinned })}
            >
              <i className="bi bi-pin-angle" aria-hidden="true" />
              {conversation.pinned ? 'Unpin' : 'Pin'}
            </button>
            <button
              type="button"
              role="menuitem"
              className="conversation-menu-item"
              onClick={() => update({ muted: !conversation.muted })}
            >
              <i
                className={`bi ${conversation.muted ? 'bi-bell' : 'bi-bell-slash'}`}
                aria-hidden="true"
              />
              {conversation.muted ? 'Unmute' : 'Mute'}
            </button>
            <button
              type="button"
              role="menuitem"
              className="conversation-menu-item"
              onClick={() => update({ archived: true })}
            >
              <i className="bi bi-archive" aria-hidden="true" />
              Archive
            </button>
          </div>
        ) : null}
      </>
    ) : null}
    </div>
  )
}

export default ConversationItem
