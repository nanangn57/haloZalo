export interface User {
  id: number;
  name: string;
  avatar: string;
  online: boolean;
  lastSeen: number; //timestamp
  unread: number;
}