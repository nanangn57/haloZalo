import type { User } from '../../types/user'


interface UserItemProps {
  user: User
}

function UserAvatar({
  user
}: UserItemProps) {
    return(
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
    )
}

export default UserAvatar