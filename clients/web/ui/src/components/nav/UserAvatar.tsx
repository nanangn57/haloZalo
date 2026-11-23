import type { User } from '../../types/user'
import Avatar from '../common/Avatar'


interface UserItemProps {
  user: User
}

function UserAvatar({
  user
}: UserItemProps) {
    return(
        <div>
            {/* Avatar */}
            <Avatar name={user.name} src={user.avatar} size={48} className="me-2" />
        </div>
    )
}

export default UserAvatar