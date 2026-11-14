```tsx
import type { Group } from '@core/types/group'

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

  return (
    <li className="list-group-item p-0">
      <div className="d-flex align-items-center">
        {/* Click vùng thông tin để xem thành viên */}
        <button
          type="button"
          className="btn btn-light rounded-0 text-start p-3
            flex-grow-1 border-0"
          onClick={() => onSelect(group)}
        >
          <div className="d-flex align-items-center gap-3">
            <div
              className="rounded-circle bg-primary-subtle text-primary
                d-flex align-items-center justify-content-center"
              style={{ width: 48, height: 48, flexShrink: 0 }}
            >
              <i className="bi bi-people fs-4" />
            </div>

            <div className="flex-grow-1 overflow-hidden">
              <div className="fw-semibold text-truncate">
                {group.name}
              </div>

              <small className="text-secondary">
                {isOwner ? 'Owner' : 'Member'}
                {' · '}
                {memberCount === undefined
                  ? '...'
                  : `${memberCount} members`}
              </small>
            </div>
          </div>
        </button>

        {/* Click mũi tên để mở chat */}
        <button
          type="button"
          className="btn btn-light rounded-0 h-100 px-3 border-0"
          onClick={() => onOpenChat(group)}
          aria-label={`Open chat ${group.name}`}
        >
          <i className="bi bi-chevron-right text-secondary fs-5" />
        </button>
      </div>
    </li>
  )
}

export default GroupListItem
```