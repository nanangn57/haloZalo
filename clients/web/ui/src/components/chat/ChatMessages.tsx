import type { Message } from "../../types/message";
import "./ChatMessages.css";

interface ChatMessageProps {
    messages: Message[];
    currentUserId: string;
}

function ChatMessages({
    messages,
    currentUserId
}: ChatMessageProps) {
    return (
        <div className="chat-messages flex-grow-1 overflow-auto p-3">
            {
                messages.map((message) => {
                    const isMine = message.senderId === currentUserId;
                    return (
                        <div 
                            key={message.id}
                            className={`d-flex mb-3 ${
                                isMine ? 'justify-content-end' : 'justify-content-start'
                            }`}
                        >
                            <div className="message-item">
                                <div
                                    className={`message-bubble ${
                                    isMine ? 'message-mine' : 'message-other'
                                    }`}
                                >
                                    {message.content}
                                </div>

                                <div
                                    className={`small text-secondary mt-1 ${
                                    isMine ? 'text-end' : 'text-start'
                                    }`}
                                >
                                    {new Date(message.sentAt * 1000).toLocaleTimeString(
                                    [],
                                    { hour: '2-digit', minute: '2-digit' },
                                    )}
                                </div>
                            </div>

                        </div>
                    );
                })
            }
        </div>
    );
}
export default ChatMessages