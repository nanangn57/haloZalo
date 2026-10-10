import type { User } from '../../types/user';
import UserItem from './UserItem';

interface UserListProps {
  users: User[];
  selectedUserId?: number;
  onUserSelect: (user: User) => void;
}

function UserList({
  users,
  selectedUserId,
  onUserSelect,
}: UserListProps) {
  return (
    <div className="list-group">

      {users.map((user) => (
        <UserItem
          key={user.id}
          user={user}
          active={user.id === selectedUserId}
          onClick={onUserSelect}
        />
      ))}

    </div>
  );
}

export default UserList;