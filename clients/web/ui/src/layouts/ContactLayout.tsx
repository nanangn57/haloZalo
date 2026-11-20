
import { Outlet } from 'react-router-dom'
import ContactSidebar from '../components/contact/ContactSidebar'

function ContactLayout() {
  return (
    <div className="d-flex w-100 h-100 overflow-hidden">
      <div
        className="flex-shrink-0 border-end"
        style={{ width: 320 }}
      >
        <ContactSidebar />
      </div>

      <div
        className="flex-grow-1 overflow-hidden"
        style={{ minWidth: 0 }}
      >
        <Outlet />
      </div>
    </div>
  )
}

export default ContactLayout
