import type { User } from '../../types/user'
import { NavLink } from "react-router-dom";

interface UserItemProps {
  currentUser: User
}

function NavBar({currentUser}: UserItemProps) {
    return(
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
                <button className="btn app-button">
                    <i class="bi bi-chat fs-4"></i>
                </button>

                <button className="btn app-button">
                    <i className="bi bi-people fs-4"></i>
                </button>
                
            </div>

            <div className="d-flex flex-column align-items-center gap-3 mt-auto">
                <button className="btn app-button">
                    <i class="bi bi-bar-chart fs-4"></i>
                </button>

                <button className="btn app-button">
                    <i class="bi bi-gear fs-4"></i>
                </button>

            </div>
            
        </nav>
    )

}

export default NavBar