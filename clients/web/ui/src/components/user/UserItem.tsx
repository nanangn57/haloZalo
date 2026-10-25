import type { User } from '../../types/user'
import { lastSeenConvert } from '../../utils/lastSeenConvert'

interface UserItemProps {
  user: User
  active: boolean
  onClick: (user: User) => void
}

function UserItem({
  user,
  active,
  onClick,
}: UserItemProps) {
    return(
    <li
    className={`list-group-item d-flex align-items-center ${
        active ? 'active' : ''
    }`}
    onClick={() => onClick(user)}
    role="button"
    >
        <div>
            {/* Avatar */}
            <img
            src={user.avatar}
            alt={user.name}
            className="rounded-circle me-2"
            width="48"
            height="48"
            />
        </div>

        <div className="flex-grow-1">
            <div className="fw-semibold">{user.name}</div>
            <div className="text-muted small">
            {user.online
            ? 'Online'
            : lastSeenConvert(user.lastSeen)}
            </div>  
        </div>
        
        <div style={{ minWidth: '24px', textAlign: 'center' }}>
            {user.unread > 0 && 
            (<span className="badge text-bg-primary rounded-pill">
                {user.unread}
            </span>
            )}
        </div>
        
    </li>
    )
}

export default UserItem