
export type SocketStatus =
    | 'connecting'
    | 'connected'
    | 'disconnected'
    | 'error';

export type SocketEventHandler = (
    event: Event
) => void;

export type SocketMessageHandler = (
    data: MessageEvent
) => void;
