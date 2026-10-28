import { useState } from 'react'
import UserItem from '../components/user/UserItem'
import type { User } from '../types/user'
import type { Message } from '../types/message'
import NavBar from '../components/nav/NavBar'
import ChatHeader from '../components/chat/ChatHeader'
import ChatMessages from '../components/chat/ChatMessages'
import MesToolbar from '../components/chat/MesToolBar'
import UserInfoPanel from '../components/user/UserPanel'

const initialMessages: Message[] = [
  {
    id: "m1",
    conversationId: "c1",
    senderId: "2",
    content: "Hi Khanh! How are you?",
    sentAt: Math.floor(Date.now() / 1000) - 300,
  },
  {
    id: "m2",
    conversationId: "c1",
    senderId: "1",
    content: "I'm good, thanks! How about you?",
    sentAt: Math.floor(Date.now() / 1000) - 240,
  },
  {
    id: "m3",
    conversationId: "c1",
    senderId: "2",
    content: "I'm doing well. Are you working on the chat UI?",
    sentAt: Math.floor(Date.now() / 1000) - 120,
  },
  {
    id: "m4",
    conversationId: "c1",
    senderId: "1",
    content: "Yes! I'm testing the message layout now.",
    sentAt: Math.floor(Date.now() / 1000) - 60,
  },
]

function ChatPage() {
  const currentUser: User = {
    id: "1",
    name: 'Khanh',
    avatar: 'https://i.pravatar.cc/150?img=1',
    online: true,
    lastSeen: 0,
    unread: 3,
  }
  const users: User[] = [
    {
      id: "2",
      name: 'Alice',
      avatar: 'https://i.pravatar.cc/150?img=20',
      online: false,
      lastSeen: 1790421420,
      unread: 0,
    },
    {
      id: "3",
      name: 'Bob',
      avatar: 'https://i.pravatar.cc/150?img=10',
      online: true,
      lastSeen: 1790421180,
      unread: 5,
    },
  ]
  const HeaderUser: User = {
    id: "4",
    name: 'Huy',
    avatar: 'https://i.pravatar.cc/150?img=1',
    online: true,
    lastSeen: 0,
    unread: 3,
  }
  const [messages, setMessages] = useState<Message[]>(initialMessages);
  const [newMessage, setNewMessage] = useState("");
  const currentUserId = String(currentUser.id);
  const [showUserInfo, setShowUserInfo] = useState(false)
  const [selectedUser, setSelectedUser] = useState<User>(users[0])

  return (
    <div 
    className="d-flex w-100 vh-100 overflow-hidden"
    style={{ minHeight: 0}}
    >
        
      <NavBar currentUser={currentUser}/>
      
      <div className="d-flex flex-column flex-shrink-0 border-end">
          <div className="p-3">
            <form className="d-flex" role="search">
              <input className="form-control me-2" type="search" placeholder="Search" aria-label="Search"/>
              <button className="btn btn-outline-success" type="submit">Search</button>
            </form>
          </div>

          <ul className="list-group flex-grow-1 overflow-auto">
            {users.map((user) => (
              <UserItem
                key={user.id}
                user={user}
                active={selectedUser.id === user.id}
                onClick={(clickedUser) => setSelectedUser(clickedUser)}
              />
            ))}
          </ul>
      </div>
      
      <div 
      className="d-flex flex-column flex-grow-1 overflow-hidden"
      style={{minWidth: 0, minHeight: 0}}
      >
        <div className="flex-shrink-0 border-bottom">
          <ChatHeader 
          user={HeaderUser}
          onInfoClick={() => setShowUserInfo(true)}
          />
        </div>

        <div 
        className="flex-grow-1 d-flex flex-column overflow-hidden"
        style={{ minHeight: 0}}
        >
            <ChatMessages
              messages={messages}
              currentUserId={currentUserId}
            />
        </div>

        <form 
        className="border-top flex-shrink-0"
        onSubmit={(event) => {
          event.preventDefault();

          const content = newMessage.trim();
          if (!content) return;

          const newMessageItem: Message = {
            id: crypto.randomUUID(),
            conversationId: "c1",
            senderId: currentUserId,
            content,
            sentAt: Math.floor(Date.now() / 1000),
          };

          setMessages((previous) => [...previous, newMessageItem]);
          setNewMessage("");
        }}
        >
          <MesToolbar />

          <div className="d-flex align-items-center gap-2 p-2">
            <input
            type="text"
            className="form-control"
            placeholder="Type a message..."
            value={newMessage}
            onChange={(event) => setNewMessage(event.target.value)}
            />

            <button className="btn btn-primary flex-shrink-0" type="submit">
              <i className="bi bi-send fs-4"></i>
            </button>
          </div>
        </form>
      </div>
      
      {showUserInfo && (
        <UserInfoPanel
          user={HeaderUser}
          onClose={() => setShowUserInfo(false)}
        />
      )}


    </div>
  )
}

export default ChatPage

