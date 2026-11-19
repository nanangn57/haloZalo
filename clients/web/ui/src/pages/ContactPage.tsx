
import ContactHeader from '../components/contact/ContactHeader'
import ContactFriendlist from '../components/contact/ContactFriendList'

import type { ContactHeaderConfig } from '../types/contact'
import type { User } from '../types/user'

function ContactPage() {
  const friends: User[] = [
    {
      id: '1',
      name: 'Thành',
      avatar: 'https://i.pravatar.cc/150?img=20',
      online: false,
      lastSeen: 1790421420,
      unread: 0,
    },
    {
      id: '2',
      name: 'Ngọc Anh',
      avatar: 'https://i.pravatar.cc/150?img=10',
      online: true,
      lastSeen: 1790421180,
      unread: 5,
    },
  ]

  const headerSection: ContactHeaderConfig = {
    header: 'Friend list',
    icon: 'bi bi-person fs-4',
  }

  return (
    <main
      className="d-flex flex-column h-100 overflow-hidden"
      style={{ minWidth: 0 }}
    >
      <ContactHeader contactScreen={headerSection} />

      <ul className="list-group flex-grow-1 overflow-auto">
        {friends.map((user) => (
          <ContactFriendlist
            key={user.id}
            user={user}
          />
        ))}
      </ul>
    </main>
  )
}

export default ContactPage
