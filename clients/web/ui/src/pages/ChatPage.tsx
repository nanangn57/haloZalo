
import { useLocation } from 'react-router-dom'
import { useEffect, useState } from 'react'

import type { Conversation } from '@core/types/conversation'
import { getConversationsApi, createDirectConversationApi } from '@core/conversationService'
import { getAuth } from '@core/authSession'

import ConversationItem from '../components/chat/ConversationItem'
import type { User } from '../types/user'
import type { Message } from '@core/types/message'

import { sendMessageApi, getMessagesApi } from '@core/messageService'
import ChatHeader from '../components/chat/ChatHeader'
import ChatMessages from '../components/chat/ChatMessages'
import MesToolbar from '../components/chat/MesToolBar'
import UserInfoPanel from '../components/user/UserPanel'

import { getGroupMembersApi } from '@core/groupService'
import type { Member } from '@core/types/group'

import { socketClient } from '@core/socketClient'
import type { MessagingEvent } from '@core/types/event'

import EmojiPicker, {
  type EmojiClickData,
} from 'emoji-picker-react'

interface ChatNavigationState {
  conversationId: string
  conversationType: 'GROUP' | 'DIRECT'
  title: string
}

type SocketStatus =
  | 'disconnected'
  | 'connecting'
  | 'connected'
  | 'error'

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
    useState<Message[]>([])

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

  const [socketStatus, setSocketStatus] =
  useState<SocketStatus>('disconnected')

  const [socketEvents, setSocketEvents] =
    useState<MessagingEvent[]>([])

  const [socketError, setSocketError] =
    useState<string | null>(null)

  const [targetUserId, setTargetUserId] = useState('')
  const [creatingConversation, setCreatingConversation] = useState(false)
  const [createConversationError, setCreateConversationError] =
    useState<string | null>(null)
  const [createConversationSuccess, setCreateConversationSuccess] =
    useState<string | null>(null)
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

// Load message history when conversation changes.
useEffect(() => {
  if (!selectedConversation) {
    setMessages([])
    return
  }

  let isMounted = true

  async function loadMessages() {
    try {
      const result = await getMessagesApi(
        selectedConversation!.conversationId,
      )

      if (isMounted) {
        setMessages(result.messages)
      }
    } catch (err) {
      console.error('Cannot load messages:', err)

      if (isMounted) {
        setMessages([])
      }
    }
  }

  void loadMessages()

  return () => {
    isMounted = false
  }
}, [selectedConversation?.conversationId])

// Connect WebSocket and listen for messaging events.
useEffect(() => {
  const unsubscribeEvent = socketClient.subscribe((event) => {
    console.log('Received WebSocket event:', event)

    setSocketEvents((previous) => [
      event,
      ...previous,
    ])

    if (event.type !== 'messaging.message.created') {
      return
    }

    const payload = event.payload as {
      messageId: string
      conversationId: string
      seq: number
      senderId: string
      type: 'TEXT' | 'EMOTION'
      body: string
      clientMsgId: string
      createdAt: string
    }

    if (
      !payload.messageId ||
      !payload.conversationId ||
      !payload.clientMsgId
    ) {
      return
    }

    const createdAt = Date.parse(payload.createdAt)

    const incomingMessage: Message = {
      id: payload.messageId,
      messageId: payload.messageId,
      conversationId: payload.conversationId,
      senderId: payload.senderId,
      type: payload.type,
      content: payload.body,
      seq: payload.seq,
      clientMsgId: payload.clientMsgId,
      sentAt: Number.isFinite(createdAt)
        ? Math.floor(createdAt / 1000)
        : Math.floor(Date.now() / 1000),
      status: 'sent',
    }

    setMessages((previous) => {
      const existingIndex = previous.findIndex(
        (message) =>
          message.messageId === incomingMessage.messageId ||
          message.clientMsgId === incomingMessage.clientMsgId,
      )

      if (existingIndex >= 0) {
        return previous.map((message, index) =>
          index === existingIndex
            ? incomingMessage
            : message,
        )
      }

      return [...previous, incomingMessage]
    })
  })

  // const unsubscribeStatus = socketClient.subscribeStatus(
  //   (status) => {
  //     setSocketStatus(status)
  //   },
  // )

  // socketClient.connect()

  // return () => {
  //   unsubscribeEvent()
  //   unsubscribeStatus()
  // }
}, [])


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
  async function handleSendMessage(
  event: React.FormEvent<HTMLFormElement>,
) {
  event.preventDefault()

  const content = newMessage.trim()

  if (!content || !selectedConversation) return

  const conversationId = selectedConversation.conversationId
  const clientMsgId = crypto.randomUUID()

  // Tạo tin nhắn tạm để hiển thị ngay trên UI
  const optimisticMessage: Message = {
    id: clientMsgId,
    conversationId,
    senderId: currentUserId,
    type: 'TEXT',
    content,
    clientMsgId,
    sentAt: Math.floor(Date.now() / 1000),
    status: 'sending',
  }

  setMessages((previous) => [
    ...previous,
    optimisticMessage,
  ])

  setNewMessage('')
  setShowEmojiPicker(false)

  try {
    // Gửi tin nhắn đến backend
    const savedMessage = await sendMessageApi(
      conversationId,
      {
        type: 'TEXT',
        body: content,
        clientMsgId,
      },
    )

    // Thay tin nhắn tạm bằng tin nhắn chính thức
    setMessages((previous) =>
      previous.map((message) =>
        message.clientMsgId === clientMsgId
          ? savedMessage
          : message,
      ),
    )
  } catch (error) {
    console.error('Cannot send message:', error)

    // Giữ tin nhắn để người dùng có thể thử lại
    setMessages((previous) =>
      previous.map((message) =>
        message.clientMsgId === clientMsgId
          ? {
              ...message,
              status: 'failed',
            }
          : message,
      ),
    )
  }
}

async function handleCreateDirectConversation(
  event: React.FormEvent<HTMLFormElement>,
) {
  event.preventDefault()

  const userId = targetUserId.trim()

  if (!userId) return

  if (userId === currentUserId) {
    setCreateConversationError(
      'You cannot create a conversation with yourself.',
    )
    return
  }

  setCreatingConversation(true)
  setCreateConversationError(null)
  setCreateConversationSuccess(null)

  try {
    const conversation = await createDirectConversationApi({
      userId,
    })

    // Thêm conversation vào danh sách nếu chưa tồn tại
    setConversations((previous) => {
      const exists = previous.some(
        (item) =>
          item.conversationId === conversation.conversationId,
      )

      if (exists) return previous

      return [conversation, ...previous]
    })

    // Mở conversation vừa tạo hoặc lấy lại
    setSelectedConversation(conversation)

    // Reset input
    setTargetUserId('')

    setCreateConversationSuccess(
      `Conversation ready: ${conversation.conversationId}`,
    )

    setShowUserInfo(false)
  } catch (err) {
    setCreateConversationError(
      err instanceof Error
        ? err.message
        : 'Cannot create conversation.',
    )
  } finally {
    setCreatingConversation(false)
  }
}

  return (
    <div
      className="d-flex w-100 h-100 overflow-hidden"
      style={{ minHeight: 0 }}
    >
      
{/* Conversation sidebar */}
{!isFromGroupPage && (
  <div
    className="d-flex flex-column flex-shrink-0 border-end"
    style={{ width: '320px', minWidth: '280px' }}
  >
    {/* TEST: Create Direct Conversation */}
    <div className="p-3 border-bottom bg-light">
      <div className="d-flex align-items-center justify-content-between mb-2">
        <strong>Test: New Direct Chat</strong>
        <span className="badge bg-warning text-dark">
          DEV
        </span>
      </div>

      <form onSubmit={handleCreateDirectConversation}>
        <input
          type="text"
          className="form-control form-control-sm mb-2"
          placeholder="Enter target User ID"
          value={targetUserId}
          onChange={(event) => {
            setTargetUserId(event.target.value)
            setCreateConversationError(null)
            setCreateConversationSuccess(null)
          }}
          disabled={creatingConversation}
        />

        <button
          type="submit"
          className="btn btn-primary btn-sm w-100"
          disabled={
            creatingConversation ||
            !targetUserId.trim()
          }
        >
          {creatingConversation
            ? 'Creating...'
            : 'Create / Open Direct Chat'}
        </button>
      </form>

      {createConversationError && (
        <div className="text-danger small mt-2">
          {createConversationError}
        </div>
      )}

      {createConversationSuccess && (
        <div className="text-success small mt-2">
          {createConversationSuccess}
        </div>
      )}

      <small className="text-muted d-block mt-2">
        Current user: {currentUserId || 'Not logged in'}
      </small>
    </div>

    {/* Search conversations */}
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

    {/* Giữ nguyên phần danh sách conversations bên dưới */}


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

          <div className="d-flex align-items-center gap-2 px-3 py-2 border-bottom bg-light">
            <span
              className={`rounded-circle ${
                socketStatus === 'connected'
                  ? 'bg-success'
                  : socketStatus === 'error'
                    ? 'bg-danger'
                    : 'bg-secondary'
              }`}
              style={{
                width: 9,
                height: 9,
                display: 'inline-block',
              }}
            />

  <small className="text-muted">
    WebSocket: {socketStatus}
  </small>

  {socketError && (
    <small className="text-danger ms-2">
      {socketError}
    </small>
  )}
</div>

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

      {/* WebSocket Debug Panel */}
      <div
        className="border-top bg-light p-2"
        style={{
          maxHeight: '220px',
          overflowY: 'auto',
        }}
      >
        <div className="d-flex justify-content-between align-items-center mb-2">
          <strong className="small">
            WebSocket Debug
          </strong>

          <div className="d-flex gap-2 align-items-center">
            <small className="text-muted">
              {socketEvents.length} events
            </small>

            <button
              type="button"
              className="btn btn-sm btn-outline-secondary"
              onClick={() => setSocketEvents([])}
            >
              Clear
            </button>
          </div>
        </div>

        {socketEvents.length === 0 ? (
          <small className="text-muted">
            Waiting for WebSocket events...
          </small>
        ) : (
          socketEvents.map((event) => (
            <div
              key={event.eventId}
              className="border rounded bg-white p-2 mb-2"
            >
              <div className="d-flex justify-content-between">
                <strong className="small">
                  {event.type}
                </strong>

                <small className="text-muted">
                  {event.producer}
                </small>
              </div>

              <pre
                className="small mb-0 mt-2"
                style={{
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-word',
                }}
              >
                {JSON.stringify(event.payload, null, 2)}
              </pre>
            </div>
          ))
        )}
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
