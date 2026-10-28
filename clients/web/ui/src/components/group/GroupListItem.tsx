
import type { Group } from '../../types/group'

interface GroupListItemProps {
  group: Group
  onSelect: (group: Group) => void
}

function GroupListItem({
  group,
  onSelect,
}: GroupListItemProps) {
  const isOwner = group.role === 'OWNER'

  return (
    <li className="list-group-item p-0">
      <button
        type="button"
        className="btn btn-light w-100 rounded-0 text-start p-3"
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
            </small>
          </div>

          <i className="bi bi-chevron-right text-secondary" />
        </div>
      </button>
    </li>
  )
}

export default GroupListItem
