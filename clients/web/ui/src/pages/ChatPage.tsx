import UserItem from '../components/user/UserItem'
import type { User } from '../types/user'
import NavBar from '../components/nav/NavBar'
import ChatHeader from '../components/chat/ChatHeader'


function ChatPage() {
  const currentUser: User = {
    id: 1,
    name: 'Khanh',
    avatar: 'https://i.pravatar.cc/150?img=1',
    online: true,
    lastSeen: 0,
    unread: 3,
  }
  const users: User[] = [
    {
      id: 2,
      name: 'Alice',
      avatar: 'https://i.pravatar.cc/150?img=20',
      online: false,
      lastSeen: 1790421420,
      unread: 0,
    },
    {
      id: 3,
      name: 'Bob',
      avatar: 'https://i.pravatar.cc/150?img=10',
      online: true,
      lastSeen: 1790421180,
      unread: 5,
    },
  ]
  const HeaderUser: User = {
    id: 4,
    name: 'Huy',
    avatar: 'https://i.pravatar.cc/150?img=1',
    online: true,
    lastSeen: 0,
    unread: 3,
  }

  return (
    <div className="d-flex w-100 vh-100">
        
      <NavBar currentUser={currentUser}/>
      
      <div className="d-flex flex-column flex-shrink-0 border-end">
          <div className="p-3">
            <form class="d-flex" role="search">
              <input class="form-control me-2" type="search" placeholder="Search" aria-label="Search"/>
              <button class="btn btn-outline-success" type="submit">Search</button>
            </form>
          </div>

          <ul className="list-group flex-grow-1 overflow-auto">
            {users.map((user) => (
              <UserItem
              key={user.id}
              user={user}
            />
            ))}
          </ul>
      </div>
      
      <div className="d-flex flex-column flex-grow-1">
        <div className="d-flex align-items-center px-3 py-2 border-bottom">
          <ChatHeader user={HeaderUser}/>
        </div>

      </div>
       

    </div>
  )
}

export default ChatPage

