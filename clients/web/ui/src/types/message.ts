export interface Message {
  id: string;
  conversationId: string;
  senderId: string;
  content: string;
  sentAt: number;
  status?: 'sending' | 'sent' | 'delivered' | 'seen';
}