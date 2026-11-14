
import { useLocation } from 'react-router-dom'
import { useEffect, useState } from 'react'

import type { Conversation } from '@core/types/conversation'
import { getConversationsApi } from '@core/conversationService'
import { getAuth } from '@core/authSession'

import ConversationItem from '../components/chat/ConversationItem'
import type { User } from '../types/user'
import type { Message } from '../types/message'

import ChatHeader from '../components/chat/ChatHeader'
import ChatMessages from '../components/chat/ChatMessages'
import MesToolbar from '../components/chat/MesToolBar'
import UserInfoPanel from '../components/user/UserPanel'

import { getGroupMembersApi } from '@core/groupService'
import type { Member } from '@core/types/group'

import EmojiPicker, {
  type EmojiClickData,
} from 'emoji-picker-react'

interface ChatNavigationState {
  conversationId: string
  conversationType: 'GROUP' | 'DIRECT'
  title: string
}


const initialMessages: Message[] = [
  {
    id: 'm1',
    conversationId: 'c1',
    senderId: '2',
    content: 'Hi Khanh! How are you?',
    sentAt: Math.floor(Date.now() / 1000) - 300,
  },
  {
    id: 'm2',
    conversationId: 'c1',
    senderId: '1',
    content: "I'm good, thanks! How about you?",
    sentAt: Math.floor(Date.now() / 1000) - 240,
  },
  {
    id: 'm3',
    conversationId: 'c1',
    senderId: '2',
    content: "I'm doing well. Are you working on the chat UI?",
    sentAt: Math.floor(Date.now() / 1000) - 120,
  },
  {
    id: 'm4',
    conversationId: 'c1',
    senderId: '1',
    content: "Yes! I'm testing the message layout now.",
    sentAt: Math.floor(Date.now() / 1000) - 60,
  },
]

function ChatPage() {
  const location = useLocation()

  const navigationState =
    location.state as ChatNavigationState | null

  const isFromGroupPage =
  navigationState?.conversationType === 'GROUP' &&
  location.pathname.startsWith('/contacts/groups/')

  const authUser = getAuth()?.user

  const currentUser: User = {
    id: authUser?.userId ?? '',
    name:
      authUser?.displayName ||
      authUser?.username ||
      'User',
    avatar: 'https://i.pravatar.cc/150?img=10',
    online: true,
    lastSeen: 0,
    unread: 0,
  }

  // Placeholder user info for the existing UserInfoPanel.
  // Group information is not loaded by this component yet.
  const HeaderUser: User = {
    id: '4',
    name: 'Huy',
    avatar: 'https://i.pravatar.cc/150?img=1',
    online: true,
    lastSeen: 0,
    unread: 0,
  }

  const currentUserId = String(currentUser.id)

  const [messages, setMessages] =
    useState<Message[]>(initialMessages)

  const [newMessage, setNewMessage] = useState('')
  const [showEmojiPicker, setShowEmojiPicker] = useState(false)
  const [showUserInfo, setShowUserInfo] = useState(false)

  const [conversations, setConversations] =
    useState<Conversation[]>([])

  const [selectedConversation, setSelectedConversation] =
    useState<Conversation | null>(null)

  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [groupMembers, setGroupMembers] = useState<Member[]>([])
  const [loadingGroupMembers, setLoadingGroupMembers] = useState(false)

  // Load conversations and select the requested conversation.
  useEffect(() => {
  let isMounted = true

  async function loadGroupMembers() {
    if (selectedConversation?.type !== 'GROUP') {
      setGroupMembers([])
      setLoadingGroupMembers(false)
      return
    }

    setLoadingGroupMembers(true)

    try {
      const result = await getGroupMembersApi(
        selectedConversation.conversationId,
      )

      if (isMounted) {
        setGroupMembers(result.members)
      }
    } catch (err) {
      console.error('Cannot load group members:', err)

      if (isMounted) {
        setGroupMembers([])
      }
    } finally {
      if (isMounted) {
        setLoadingGroupMembers(false)
      }
    }
  }

  void loadGroupMembers()

  return () => {
    isMounted = false
  }
}, [
  selectedConversation?.conversationId,
  selectedConversation?.type,
])

  useEffect(() => {
  let isMounted = true

  async function loadConversations() {
    try {
      setIsLoading(true)
      setError(null)

      const data = await getConversationsApi()

      if (!isMounted) return

      let loadedConversations = data.conversations
      let targetConversation: Conversation | null = null

      if (navigationState?.conversationId) {
        targetConversation =
          loadedConversations.find(
            (conversation) =>
              conversation.conversationId ===
              navigationState.conversationId,
          ) ?? null

        if (
          !targetConversation &&
          navigationState.conversationType === 'GROUP'
        ) {
          targetConversation = {
            conversationId: navigationState.conversationId,
            type: 'GROUP',
            memberIds: [],
            lastSeq: 0,
            createdAt: new Date().toISOString(),
          }

          loadedConversations = [
            targetConversation,
            ...loadedConversations,
          ]
        }
      } else {
        targetConversation = loadedConversations[0] ?? null
      }

      setConversations(loadedConversations)
      setSelectedConversation(targetConversation)
    } catch (err) {
      if (!isMounted) return

      setError(
        err instanceof Error
          ? err.message
          : 'Không thể tải danh sách hội thoại.',
      )
    } finally {
      if (isMounted) setIsLoading(false)
    }
  }

  void loadConversations()

  return () => {
    isMounted = false
  }
}, [
  navigationState?.conversationId,
  navigationState?.conversationType,
  navigationState?.title,
])

  // Display only messages belonging to the selected conversation.
  const visibleMessages = messages.filter(
    (message) =>
      message.conversationId ===
      selectedConversation?.conversationId,
  )

  // Determine the chat header title.
  const chatTitle = (() => {
  if (
    navigationState?.conversationType === 'GROUP' &&
    navigationState.title
  ) {
    return navigationState.title
  }

  if (!selectedConversation) {
    return 'Select a conversation'
  }

  return selectedConversation.type === 'GROUP'
    ? 'Group conversation'
    : 'Direct conversation'
})()

  const chatSubtitle =
    selectedConversation?.type === 'GROUP'
      ? loadingGroupMembers
        ? 'Loading members...'
        : `${groupMembers.length} members`
      : ''

  // Send a local demo message.
  function handleSendMessage(
    event: React.FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault()

    const content = newMessage.trim()

    if (!content || !selectedConversation) return

    const newMessageItem: Message = {
      id: crypto.randomUUID(),
      conversationId: selectedConversation.conversationId,
      senderId: currentUserId,
      content,
      sentAt: Math.floor(Date.now() / 1000),
    }

    setMessages((previous) => [
      ...previous,
      newMessageItem,
    ])

    setNewMessage('')
    setShowEmojiPicker(false)
  }

  return (
    <div
      className="d-flex w-100 h-100 overflow-hidden"
      style={{ minHeight: 0 }}
    >
      {/* Conversation sidebar */}
      {!isFromGroupPage && (
        <div className="d-flex flex-column flex-shrink-0 border-end">
          <div className="p-3">
            <form
              className="d-flex"
              role="search"
              onSubmit={(event) => event.preventDefault()}
            >
              <input
                className="form-control me-2"
                type="search"
                placeholder="Search"
                aria-label="Search"
              />

              <button
                className="btn btn-outline-success"
                type="submit"
              >
                Search
              </button>
            </form>
          </div>

          <ul className="list-group flex-grow-1 overflow-auto">
            {isLoading && (
              <li className="list-group-item text-muted">
                Loading conversations...
              </li>
            )}

            {error && (
              <li className="list-group-item text-danger">
                {error}
              </li>
            )}

            {!isLoading && !error && conversations.length === 0 && (
              <li className="list-group-item text-muted">
                No conversations found.
              </li>
            )}

            {!isLoading &&
              !error &&
              conversations.map((conversation) => (
                <ConversationItem
                  key={conversation.conversationId}
                  conversation={conversation}
                  active={
                    selectedConversation?.conversationId ===
                    conversation.conversationId
                  }
                  onClick={(conversation) => {
                    setSelectedConversation(conversation)
                    setShowUserInfo(false)
                  }}
                />
              ))}
          </ul>
        </div>
      )}

      {/* Main chat area */}
      <div
        className="d-flex flex-column flex-grow-1 overflow-hidden"
        style={{ minWidth: 0, minHeight: 0 }}
      >
        <div className="flex-shrink-0 border-bottom">
          <ChatHeader
            title={chatTitle}
            subtitle={chatSubtitle}
            type={selectedConversation?.type ?? 'DIRECT'}
            avatar={undefined}
            onInfoClick={() => setShowUserInfo((previous) => !previous)}
          />
        </div>

        <div
          className="flex-grow-1 d-flex flex-column overflow-hidden"
          style={{ minHeight: 0 }}
        >
          {selectedConversation ? (
            <ChatMessages
              messages={visibleMessages}
              currentUserId={currentUserId}
            />
          ) : (
            <div className="flex-grow-1 d-flex align-items-center justify-content-center text-muted">
              Select a conversation to start chatting.
            </div>
          )}
        </div>

        {/* Message input */}
        <form
          className="border-top flex-shrink-0 position-relative"
          onSubmit={handleSendMessage}
        >
          <MesToolbar
            onEmojiClick={() => {
              setShowEmojiPicker((previous) => !previous)
            }}
          />

          {showEmojiPicker && (
            <div
              className="position-absolute"
              style={{
                bottom: '100%',
                left: '8px',
                zIndex: 1050,
              }}
            >
              <EmojiPicker
                onEmojiClick={(emojiData: EmojiClickData) => {
                  setNewMessage(
                    (previous) => previous + emojiData.emoji,
                  )
                }}
              />
            </div>
          )}

          <div className="d-flex align-items-center gap-2 p-2">
            <input
              type="text"
              className="form-control"
              placeholder={
                selectedConversation
                  ? 'Type a message...'
                  : 'Select a conversation first'
              }
              value={newMessage}
              disabled={!selectedConversation}
              onChange={(event) =>
                setNewMessage(event.target.value)
              }
            />

            <button
              className="btn btn-primary flex-shrink-0"
              type="submit"
              disabled={
                !selectedConversation || !newMessage.trim()
              }
            >
              <i className="bi bi-send fs-4" />
            </button>
          </div>
        </form>
      </div>

      {/* User information panel */}
      {showUserInfo && (
        <UserInfoPanel
          user={HeaderUser}
          onClose={() => setShowUserInfo(false)}
        />
      )}
    </div>
  )
}

export default ChatPage
