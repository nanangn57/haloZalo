import type { User } from '../../types/user'


interface ChatHeaderProps {
  user: User
}

function ChatHeader({
  user
}: ChatHeaderProps) {
    return(
        <div className="d-flex align-items-center w-100">

            <img
                src={user.avatar}
                alt={user.name}
                className="rounded-circle me-2"
                width="48"
                height="48"
            />
            
            <div className="fw-semibold">
                {user.name}
            </div>

            <div className="ms-auto d-flex align-items-center gap-3">
                <button className="btn app-button flex-shrink-0">
                    <i class="bi bi-search fs-4"></i>
                </button>

                <button className="btn app-button flex-shrink-0">
                    <i class="bi bi-layout-sidebar fs-4"></i>
                </button>
            </div>
            
        </div>
        
    )
}

export default ChatHeader