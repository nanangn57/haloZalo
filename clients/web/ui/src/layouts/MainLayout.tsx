
import type { ReactNode } from 'react'

import NavBar from '../components/nav/NavBar'
import type { User } from '../types/user'
import { getAuth } from '@core/authSession'

interface MainLayoutProps {
  children: ReactNode
}

function MainLayout({ children }: MainLayoutProps) {
  const authUser = getAuth()?.user

  const currentUser: User = {
    id: authUser?.userId ?? '',
    name: authUser?.displayName || authUser?.username || 'User',
    avatar: 'https://i.pravatar.cc/150?img=10',
    online: true,
    lastSeen: 0,
    unread: 0,
  }

  return (
    <div className="d-flex vh-100 overflow-hidden">
      <NavBar currentUser={currentUser} />

      <div className="flex-grow-1 overflow-hidden" style={{ minWidth: 0 }}>
        {children}
      </div>
    </div>
  )
}

export default MainLayout
