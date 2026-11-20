import { NavLink } from 'react-router-dom'

function ContactSidebar() {
  return (
    <div className="contact-sidebar-panel">
      <form
        className="contact-search"
        role="search"
        onSubmit={(event) => event.preventDefault()}
      >
        <div className="contact-search-field">
          <i className="bi bi-search" aria-hidden="true" />
          <input
            className="app-input"
            type="search"
            placeholder="Search friends or groups"
            aria-label="Search"
          />
        </div>
      </form>

      <nav className="contact-side-nav" aria-label="Contacts">
        <NavLink
          to="/contacts/friends"
          className={({ isActive }) =>
            `contact-side-link ${isActive ? 'active' : ''}`
          }
        >
          <span className="contact-side-icon" aria-hidden="true">
            <i className="bi bi-person" />
          </span>
          <span>Friend list</span>
        </NavLink>

        <NavLink
          to="/contacts/groups"
          className={({ isActive }) =>
            `contact-side-link ${isActive ? 'active' : ''}`
          }
        >
          <span className="contact-side-icon" aria-hidden="true">
            <i className="bi bi-people" />
          </span>
          <span>Group list</span>
        </NavLink>

        <NavLink
          to="/contacts/requests"
          className={({ isActive }) =>
            `contact-side-link ${isActive ? 'active' : ''}`
          }
        >
          <span className="contact-side-icon" aria-hidden="true">
            <i className="bi bi-person-plus" />
          </span>
          <span>Friend request</span>
        </NavLink>
      </nav>
    </div>
  )
}

export default ContactSidebar
