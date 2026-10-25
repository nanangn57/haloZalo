import type { ContactHeaderConfig } from "../../types/contact";

interface ContactHeaderProps {
  contactScreen: ContactHeaderConfig
}

function ContactHeader({contactScreen}: ContactHeaderProps) {
    return(
            <div className="d-flex align-items-center p-3 gap-2 text-start">
                <i className={contactScreen.icon}></i>
                <span> {contactScreen.header} </span>
            </div>
    )
}

export default ContactHeader