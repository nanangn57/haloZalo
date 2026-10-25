function ContactSidebar() {
    return(
        <div className="w-100">
            <div className="d-flex align-items-center p-3 gap-2">
                <div className="flex-grow-1">
                    <form className="d-flex" role="search">
                        <input className="form-control me-2" type="search" placeholder="Search" aria-label="Search"/>
                        <button className="btn btn-outline-success" type="submit">Search</button>
                    </form>
                </div>

                <button className="btn app-button">
                    <i className="bi bi-person-plus fs-4"></i>
                </button>
            </div>

            <div className="d-flex align-items-center p-3 gap-2">
                <button className="btn app-button w-100 text-start">
                    <i className="bi bi-person fs-4"></i>
                    <span> Friend list </span>
                </button>
            </div>

            <div className="d-flex align-items-center p-3 gap-2">
                <button className="btn app-button w-100 text-start">
                    <i className="bi bi-people fs-4"></i>
                    <span> Group list </span>
                </button>
            </div>

            <div className="d-flex align-items-center p-3 gap-2">
                <button className="btn app-button w-100 text-start">
                    <i className="bi bi-person-plus fs-4"></i>
                    <span> Friend request </span>
                </button>
            </div>
            
        </div>
    )
}

export default ContactSidebar