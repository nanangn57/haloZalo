
import { NavLink } from 'react-router-dom'

function ContactSidebar() {
  return (
    <div className="w-100">
      <div className="d-flex align-items-center p-3 gap-2">
        <div className="flex-grow-1">
          <form
            className="d-flex"
            role="search"
            onSubmit={(event) => event.preventDefault()}
          >
            <input
              className="form-control me-2"
              type="search"
              placeholder="Search"
              aria-label="Search"
            />

            <button
              className="btn btn-outline-success"
              type="submit"
            >
              Search
            </button>
          </form>
        </div>

        <button
          type="button"
          className="btn app-button"
          title="Add friend"
        >
          <i className="bi bi-person-plus fs-4"></i>
        </button>
      </div>

      <div className="d-flex align-items-center p-3 gap-2">
        <NavLink
          to="/contacts/friends"
          className={({ isActive }) =>
            `btn app-button w-100 text-start ${
              isActive ? 'active' : ''
            }`
          }
        >
          <i className="bi bi-person fs-4"></i>
          <span> Friend list </span>
        </NavLink>
      </div>

      <div className="d-flex align-items-center p-3 gap-2">
        <NavLink
          to="/contacts/groups"
          className={({ isActive }) =>
            `btn app-button w-100 text-start ${
              isActive ? 'active' : ''
            }`
          }
        >
          <i className="bi bi-people fs-4"></i>
          <span> Group list </span>
        </NavLink>
      </div>

      <div className="d-flex align-items-center p-3 gap-2">
        <NavLink
          to="/contacts/requests"
          className={({ isActive }) =>
            `btn app-button w-100 text-start ${
              isActive ? 'active' : ''
            }`
          }
        >
          <i className="bi bi-person-plus fs-4"></i>
          <span> Friend request </span>
        </NavLink>
      </div>
    </div>
  )
}

export default ContactSidebar
