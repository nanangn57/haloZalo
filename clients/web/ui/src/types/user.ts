export interface User {
  id: string;
  name: string;
  avatar: string;
  online: boolean;
  lastSeen: number; //timestamp
  unread: number;
}