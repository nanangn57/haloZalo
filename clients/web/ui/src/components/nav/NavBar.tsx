
import type { User } from '../../types/user'
import { NavLink } from 'react-router-dom'

interface UserItemProps {
  currentUser: User
}

function NavBar({ currentUser }: UserItemProps) {
  return (
    <nav className="d-flex flex-column align-items-center vh-100 border-end p-2 flex-shrink-0">
      <div className="mb-4">
        <img
          src={currentUser.avatar}
          alt={currentUser.name}
          className="rounded-circle me-2"
          width="48"
          height="48"
        />
      </div>

      <div className="d-flex flex-column align-items-center gap-3">
        <NavLink
          to="/chat"
          className={({ isActive }) =>
            `btn app-button ${isActive ? 'active' : ''}`
          }
          title="Chat"
        >
          <i className="bi bi-chat fs-4"></i>
        </NavLink>

        <NavLink
          to="/contacts/friends"
          className={({ isActive }) =>
            `btn app-button ${
              isActive || window.location.pathname.startsWith('/contacts')
                ? 'active'
                : ''
            }`
          }
          title="Contacts"
        >
          <i className="bi bi-people fs-4"></i>
        </NavLink>
      </div>

      <div className="d-flex flex-column align-items-center gap-3 mt-auto">
        <button
          type="button"
          className="btn app-button"
          title="Statistics"
        >
          <i className="bi bi-bar-chart fs-4"></i>
        </button>

        <button
          type="button"
          className="btn app-button"
          title="Settings"
        >
          <i className="bi bi-gear fs-4"></i>
        </button>
      </div>
    </nav>
  )
}

export default NavBar
