import type { Group } from '@core/types/group'
import { getInitials } from '@core/avatar'

interface GroupListItemProps {
  group: Group
  memberCount?: number
  onSelect: (group: Group) => void
  onOpenChat: (group: Group) => void
}

function GroupListItem({
  group,
  memberCount,
  onSelect,
  onOpenChat,
}: GroupListItemProps) {
  const isOwner = group.role === 'OWNER'
  const initial = group.name?.trim() ? getInitials(group.name) : 'G'

  return (
    <li className="group-row-item">
      <button
        type="button"
        className="group-row-main"
        onClick={() => onSelect(group)}
      >
        <span className="group-avatar" aria-hidden="true">
          {initial}
        </span>

        <span className="group-row-copy">
          <span className="group-row-name">{group.name}</span>
          <span className="group-row-meta">
            {isOwner ? 'Owner' : 'Member'}
            {' · '}
            {memberCount === undefined
              ? '…'
              : `${memberCount} members`}
          </span>
        </span>
      </button>

      <button
        type="button"
        className="app-icon-button group-row-chat"
        onClick={() => onOpenChat(group)}
        aria-label={`Open chat ${group.name}`}
        title="Open chat"
      >
        <i className="bi bi-chat-dots" aria-hidden="true" />
      </button>
    </li>
  )
}

export default GroupListItem
