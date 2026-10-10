import ContactSidebar from "../components/contact/ContactSidebar";
import ContactHeader from "../components/contact/ContactHeader";
import ContactFriendlist from "../components/contact/ContactFriendList";
import NavBar from "../components/nav/NavBar";
import type { ContactHeaderConfig } from "../types/contact";
import type { User } from "../types/user";

function ContactPage() {
    const friends: User[] = [
            {
              id: 1,
              name: 'Thành',
              avatar: 'https://i.pravatar.cc/150?img=20',
              online: false,
              lastSeen: 1790421420,
              unread: 0,
            },
            {
              id: 2,
              name: 'Ngọc Anh',
              avatar: 'https://i.pravatar.cc/150?img=10',
              online: true,
              lastSeen: 1790421180,
              unread: 5,
            },
          ]
    
    const headerSection: ContactHeaderConfig = {
        header: "Friend list",
        icon: "bi bi-person fs-4"
    }
    const currentUser: User = {
        id: 1,
        name: 'Khanh',
        avatar: 'https://i.pravatar.cc/150?img=10',
        online: true,
        lastSeen: 1790421180,
        unread: 5,
    }

    return(
        <div className="d-flex w-100 vh-100">
            <NavBar currentUser={currentUser} />
            
            <div className="flex-shrink-0">
                <ContactSidebar />
            </div>
    
            <main className="flex-grow-1 d-flex flex-column overflow-hidden" style={{ minWidth: 0, width: 0}}>

                <ContactHeader contactScreen = {headerSection} />

                <ul className="list-group flex-grow-1 overflow-auto">
                {friends.map((user) => (
                <ContactFriendlist
                key={user.id}
                user={user}
                />
                ))}
                </ul>
            </main>
        </div>

    )
}

export default ContactPage