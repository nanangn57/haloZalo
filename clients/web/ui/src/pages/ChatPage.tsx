
import { useLocation, useParams } from 'react-router-dom'
import { useEffect, useRef, useState } from 'react'

import type { Conversation } from '@core/types/conversation'
import {
  getConversationsApi,
  createDirectConversationApi,
} from '@core/conversationService'
import { getAuth } from '@core/authSession'
import {
  findUserApi,
  lookupUsersApi,
  type UserSummary,
} from '@core/userService'
import type { AuthUser } from '@core/types/auth'

import type { User } from '../types/user'
import type { Message } from '@core/types/message'
import type { Member } from '@core/types/group'
import type { MessagingEvent } from '@core/types/event'

import ConversationItem from '../components/chat/ConversationItem'
import ChatHeader from '../components/chat/ChatHeader'
import ChatMessages from '../components/chat/ChatMessages'
import MesToolbar from '../components/chat/MesToolBar'
// import UserInfoPanel from '../components/user/UserPanel'

import {
  sendMessageApi,
  getMessagesApi,
  createGroupConversationApi,
  uploadMediaApi,
} from '@core/messageService'
import type { MediaKind } from '@core/types/message'
import { getGroupMembersApi } from '@core/groupService'
// import { socketClient } from '@core/socketClient'

import EmojiPicker, {
  type EmojiClickData,
} from 'emoji-picker-react'

// ======================================================
// TYPES
// ======================================================

interface ChatNavigationState {
  conversationId: string
  conversationType: 'GROUP' | 'DIRECT'
  title: string
}

// type SocketStatus =
//   | 'disconnected'
//   | 'connecting'
//   | 'connected'
//   | 'error'

// ======================================================
// CHAT PAGE
// ======================================================

function ChatPage() {
  // --------------------------------------------------
  // 1. ROUTE AND NAVIGATION
  // --------------------------------------------------

  const location = useLocation()

  const { groupId: routeGroupId } = useParams<{
    groupId: string
  }>()

  const navigationState =
    location.state as ChatNavigationState | null

  // Prefer URL params because location.state is lost after refresh.
  const requestedConversationId =
    routeGroupId || navigationState?.conversationId

  const requestedConversationType =
    routeGroupId
      ? 'GROUP'
      : navigationState?.conversationType

  const requestedConversationTitle =
    navigationState?.title || ''

  const isFromGroupPage =
    location.pathname.startsWith('/contacts/groups/') &&
    Boolean(routeGroupId || requestedConversationType === 'GROUP')

  // --------------------------------------------------
  // 2. CURRENT USER
  // --------------------------------------------------

  const authUser = getAuth()?.user

  const currentUserId = String(authUser?.userId ?? '')

  // --------------------------------------------------
  // 3. STATES
  // --------------------------------------------------

  // Conversations
  const [conversations, setConversations] =
    useState<Conversation[]>([])

  const [selectedConversation, setSelectedConversation] =
    useState<Conversation | null>(null)

  const [isLoading, setIsLoading] = useState(true)

  const [error, setError] = useState<string | null>(null)

  const [navigationError, setNavigationError] =
    useState<string | null>(null)

  // Messages
  const [messages, setMessages] = useState<Message[]>([])

  const [isLoadingMessages, setIsLoadingMessages] =
    useState(false)

  const [messageLoadError, setMessageLoadError] =
    useState<string | null>(null)

  // Attachments: hidden file inputs opened by the toolbar buttons.
  const imageInputRef = useRef<HTMLInputElement>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [attachError, setAttachError] =
    useState<string | null>(null)

  const [newMessage, setNewMessage] = useState('')

  const [showEmojiPicker, setShowEmojiPicker] =
    useState(false)

  // Group members
  const [groupMembers, setGroupMembers] =
    useState<Member[]>([])

  const [loadingGroupMembers, setLoadingGroupMembers] =
    useState(false)

  // User information panel
  // const [showUserInfo, setShowUserInfo] = useState(false)

  // User search
  const [searchQuery, setSearchQuery] = useState('')

  const [searchResult, setSearchResult] =
    useState<AuthUser | null>(null)

  const [searchingUser, setSearchingUser] = useState(false)

  const [searchUserError, setSearchUserError] =
    useState<string | null>(null)

  // Create / open DIRECT conversation
  const [creatingConversation, setCreatingConversation] =
    useState(false)

  const [createConversationError, setCreateConversationError] =
    useState<string | null>(null)

  // Store recipient profiles for DIRECT conversations
  // opened through the user search in this session.
  const [directUsersByConversationId, setDirectUsersByConversationId] =
    useState<Record<string, AuthUser>>({})

  // Names of conversation members, loaded from identity by userId.
  // Survives a reload, unlike directUsersByConversationId.
  const [usersById, setUsersById] =
    useState<Record<string, UserSummary>>({})

  // Ids already asked for, so an id without an account is not asked again.
  const requestedUserIds = useRef<Set<string>>(new Set())

  // WebSocket
  // const [socketStatus, setSocketStatus] =
  //   useState<SocketStatus>('disconnected')

  const [socketEvents, setSocketEvents] =
    useState<MessagingEvent[]>([])

  // --------------------------------------------------
  // 4. LOAD CONVERSATIONS
  // --------------------------------------------------

  useEffect(() => {
    let isMounted = true

    async function loadConversations() {
      try {
        setIsLoading(true)
        setError(null)
        setNavigationError(null)

        const data = await getConversationsApi()

        if (!isMounted) return

        let loadedConversations = data.conversations

        let targetConversation: Conversation | null = null

        if (requestedConversationId) {
          targetConversation =
            loadedConversations.find(
              (conversation) =>
                conversation.conversationId ===
                requestedConversationId,
            ) ?? null

          // GROUP conversation may be opened directly from its URL.
          if (
            !targetConversation &&
            requestedConversationType === 'GROUP'
          ) {
            targetConversation = {
              conversationId: requestedConversationId,
              type: 'GROUP',
              memberIds: [],
              lastSeq: 0,
              createdAt: new Date().toISOString(),
            }

            loadedConversations = [
              targetConversation,
              ...loadedConversations,
            ]
          } else if (!targetConversation) {
            setNavigationError(
              'Không tìm thấy cuộc trò chuyện. Vui lòng quay lại danh sách hội thoại.',
            )
          }
        } else {
          targetConversation = loadedConversations[0] ?? null
        }

        if (!isMounted) return

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
        if (isMounted) {
          setIsLoading(false)
        }
      }
    }

    void loadConversations()

    return () => {
      isMounted = false
    }
  }, [
    requestedConversationId,
    requestedConversationType,
  ])

  // --------------------------------------------------
  // 5. LOAD GROUP MEMBERS
  // --------------------------------------------------

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
  
  // --------------------------------------------------
  // 5b. LOAD MEMBER NAMES FOR DIRECT CONVERSATIONS
  // --------------------------------------------------

  useEffect(() => {
    const missing = [
      ...new Set(
        conversations
          .filter((conversation) => conversation.type === 'DIRECT')
          .flatMap((conversation) => conversation.memberIds)
          .filter(
            (userId) =>
              userId !== currentUserId &&
              !requestedUserIds.current.has(userId),
          ),
      ),
    ]

    if (missing.length === 0) {
      return
    }

    missing.forEach((userId) => requestedUserIds.current.add(userId))

    // The API takes at most 100 ids per call.
    const batches: string[][] = []
    for (let i = 0; i < missing.length; i += 100) {
      batches.push(missing.slice(i, i + 100))
    }

    void Promise.all(batches.map((ids) => lookupUsersApi(ids)))
      .then((results) => {
        setUsersById((previous) => {
          const next = { ...previous }
          results.flat().forEach((user) => {
            next[user.userId] = user
          })
          return next
        })
      })
      .catch((err) => {
        console.error('Cannot load user names:', err)
        // Allow a later attempt for these ids.
        missing.forEach((userId) =>
          requestedUserIds.current.delete(userId),
        )
      })
  }, [conversations, currentUserId])

  // --------------------------------------------------
  // 6. INITIALIZE CONVERSATION AND LOAD MESSAGE HISTORY
  // --------------------------------------------------

  
  const conversationId = selectedConversation?.conversationId
  const conversationType = selectedConversation?.type

  useEffect(() => {
    if (!conversationId || !conversationType) {
      return
    }
    const currentConversationId: string = conversationId
    const currentConversationType = conversationType

    let isMounted = true

    async function initializeAndLoadMessages() {
      try {
        // STEP 1: Initialize GROUP conversation.
        // DIRECT conversations do not need this step.
        if (currentConversationType === 'GROUP') {
          await createGroupConversationApi(currentConversationId)
        }

        if (!isMounted) return

        // STEP 2: Load message history.
        const result = await getMessagesApi(currentConversationId)

        if (!isMounted) return

        setMessages(result.messages)
        setMessageLoadError(null)
      } catch (err) {
        console.error(
          'Cannot initialize conversation or load messages:',
          err,
        )

        if (isMounted) {
          setMessages([])

          setMessageLoadError(
            err instanceof Error
              ? err.message
              : 'Cannot initialize conversation or load messages.',
          )
        }
      } finally {
        if (isMounted) {
          setIsLoadingMessages(false)
        }
      }
    }

    void initializeAndLoadMessages()

    return () => {
      isMounted = false
    }
  }, [conversationId, conversationType])



  // --------------------------------------------------
  // 7. WEBSOCKET CONNECTION
  // --------------------------------------------------

  // useEffect(() => {
  //   const unsubscribeEvent = socketClient.subscribe((event) => {
  //     console.log('Received WebSocket event:', event)

  //     setSocketEvents((previous) => [
  //       event,
  //       ...previous,
  //     ])

  //     if (event.type !== 'messaging.message.created') {
  //       return
  //     }

  //     const payload = event.payload as {
  //       messageId: string
  //       conversationId: string
  //       seq: number
  //       senderId: string
  //       type: 'TEXT' | 'EMOTION'
  //       body: string
  //       clientMsgId: string
  //       createdAt: string
  //     }

  //     if (
  //       !payload.messageId ||
  //       !payload.conversationId ||
  //       !payload.clientMsgId
  //     ) {
  //       return
  //     }

  //     const createdAt = Date.parse(payload.createdAt)

  //     const incomingMessage: Message = {
  //       id: payload.messageId,
  //       messageId: payload.messageId,
  //       conversationId: payload.conversationId,
  //       senderId: payload.senderId,
  //       type: payload.type,
  //       content: payload.body,
  //       seq: payload.seq,
  //       clientMsgId: payload.clientMsgId,
  //       sentAt: Number.isFinite(createdAt)
  //         ? Math.floor(createdAt / 1000)
  //         : Math.floor(Date.now() / 1000),
  //       status: 'sent',
  //     }

  //     setMessages((previous) => {
  //       const existingIndex = previous.findIndex(
  //         (message) =>
  //           message.messageId === incomingMessage.messageId ||
  //           message.clientMsgId === incomingMessage.clientMsgId,
  //       )

  //       if (existingIndex >= 0) {
  //         return previous.map((message, index) =>
  //           index === existingIndex
  //             ? incomingMessage
  //             : message,
  //         )
  //       }

  //       return [...previous, incomingMessage]
  //     })
  //   })

  //   const unsubscribeStatus = socketClient.subscribeStatus(
  //     (status) => {
  //       setSocketStatus(status)
  //     },
  //   )

  //   socketClient.connect()

  //   return () => {
  //     unsubscribeEvent()
  //     unsubscribeStatus()
  //   }
  // }, [])

  // --------------------------------------------------
  // 8. DERIVED DATA FOR COMPONENTS
  // --------------------------------------------------

  // Only display messages from the selected conversation.
  const visibleMessages = messages.filter(
    (message) =>
      message.conversationId ===
      selectedConversation?.conversationId,
  )

  // The other member of a DIRECT conversation: the search result opened
  // in this session, else the name loaded by userId.
  function getDirectUser(
    conversation: Conversation,
  ): UserSummary | null {
    const fromSearch =
      directUsersByConversationId[conversation.conversationId]

    if (fromSearch) {
      return fromSearch
    }

    const otherId = conversation.memberIds.find(
      (userId) => userId !== currentUserId,
    )

    return otherId ? usersById[otherId] ?? null : null
  }

  function displayName(user: UserSummary): string {
    return user.displayName || user.username || 'User'
  }

  // First letter of the name, shown instead of an avatar (no avatar yet).
  function initialsOf(name: string): string {
    return name.trim().charAt(0).toUpperCase()
  }

  const selectedDirectUser =
    selectedConversation?.type === 'DIRECT'
      ? getDirectUser(selectedConversation)
      : null

  // Adapt the user to the User type required by UserInfoPanel.
  const headerUser: User | null = selectedDirectUser
    ? {
        id: selectedDirectUser.userId,
        name: displayName(selectedDirectUser),
        avatar: '',
        online: false,
        lastSeen: 0,
        unread: 0,
      }
    : null

  // Resolve conversation title.
  const chatTitle = (() => {
    if (!selectedConversation) {
      return 'Select a conversation'
    }

    if (selectedConversation.type === 'GROUP') {
      return (
        selectedConversation.conversationId ===
        requestedConversationId
          ? requestedConversationTitle || 'Group conversation'
          : 'Group conversation'
      )
    }

    return (
      headerUser?.name ||
      'Direct conversation'
    )
  })()

  // Resolve subtitle.
  const chatSubtitle =
    selectedConversation?.type === 'GROUP'
      ? loadingGroupMembers
        ? 'Loading members...'
        : `${groupMembers.length} members`
      : ''

  // Resolve conversation list display information.
  function getConversationDisplayInfo(
    conversation: Conversation,
  ) {
    if (conversation.type === 'GROUP') {
      return {
        title:
          conversation.conversationId ===
          requestedConversationId
            ? requestedConversationTitle || 'Group conversation'
            : 'Group conversation',
        avatar: undefined,
        initials: undefined,
      }
    }

    const user = getDirectUser(conversation)
    const title = user ? displayName(user) : 'Direct conversation'

    return {
      title,
      avatar: undefined,
      initials: user ? initialsOf(title) : undefined,
    }
  }

  // --------------------------------------------------
  // 9. SELECT CONVERSATION
  // --------------------------------------------------

  function handleSelectConversation(
    conversation: Conversation,
  ) {
    setSelectedConversation(conversation)
    // setShowUserInfo(false)
    setShowEmojiPicker(false)
    setNewMessage('')
  }

  // --------------------------------------------------
  // 10. SEND MESSAGE
  // --------------------------------------------------

  async function handleSendMessage(
    event: React.FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault()

    const content = newMessage.trim()

    if (
        !content ||
        !selectedConversation ||
        isLoadingMessages ||
        messageLoadError
      ) {
        return
      }

    const conversationId =
      selectedConversation.conversationId

    const clientMsgId = crypto.randomUUID()

    // Optimistic message.
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
      const savedMessage = await sendMessageApi(
        conversationId,
        {
          type: 'TEXT',
          body: content,
          clientMsgId,
        },
      )

      // Replace optimistic message with server response.
      setMessages((previous) =>
        previous.map((message) =>
          message.clientMsgId === clientMsgId
            ? savedMessage
            : message,
        ),
      )
    } catch (err) {
      console.error('Cannot send message:', err)

      // Keep failed message for retry / debugging.
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

  // --------------------------------------------------
  // 10b. SEND FILE: upload, then send a message with the mediaId
  // --------------------------------------------------

  // Guess only for the optimistic preview; the server decides the real kind.
  function guessKind(file: File): MediaKind {
    if (file.type.startsWith('image/')) return 'IMAGE'
    if (file.type.startsWith('video/')) return 'VIDEO'
    return 'DOCUMENT'
  }

  async function handleSendFile(
    event: React.ChangeEvent<HTMLInputElement>,
  ) {
    const file = event.target.files?.[0]
    // Reset so picking the same file again still fires onChange.
    event.target.value = ''

    if (
      !file ||
      !selectedConversation ||
      isLoadingMessages ||
      messageLoadError
    ) {
      return
    }

    setAttachError(null)

    const conversationId = selectedConversation.conversationId
    const clientMsgId = crypto.randomUUID()
    const kind = guessKind(file)
    const previewUrl = URL.createObjectURL(file)

    setMessages((previous) => [
      ...previous,
      {
        id: clientMsgId,
        conversationId,
        senderId: currentUserId,
        type: kind,
        content: '',
        media: {
          mediaId: '',
          kind,
          url: previewUrl,
          fileName: file.name,
          mimeType: file.type,
          size: file.size,
        },
        clientMsgId,
        sentAt: Math.floor(Date.now() / 1000),
        status: 'sending',
      },
    ])

    try {
      const media = await uploadMediaApi(conversationId, file)
      const savedMessage = await sendMessageApi(conversationId, {
        type: media.kind,
        mediaId: media.mediaId,
        clientMsgId,
      })

      setMessages((previous) =>
        previous.map((message) =>
          message.clientMsgId === clientMsgId
            ? savedMessage
            : message,
        ),
      )
      URL.revokeObjectURL(previewUrl)
    } catch (err) {
      console.error('Cannot send file:', err)
      setAttachError(
        err instanceof Error ? err.message : 'Cannot send file.',
      )

      // Keep the preview so the user sees which file failed.
      setMessages((previous) =>
        previous.map((message) =>
          message.clientMsgId === clientMsgId
            ? { ...message, status: 'failed' }
            : message,
        ),
      )
    }
  }

  // --------------------------------------------------
  // 11. SEARCH USER
  // --------------------------------------------------

  async function handleSearchUser(
    event: React.FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault()

    const query = searchQuery.trim()

    if (!query) {
      setSearchUserError(
        'Please enter a username or email.',
      )
      return
    }

    setSearchingUser(true)
    setSearchUserError(null)
    setSearchResult(null)
    setCreateConversationError(null)

    try {
      const user = await findUserApi(query)

      if (user.userId === currentUserId) {
        setSearchUserError(
          'You cannot start a conversation with yourself.',
        )
        return
      }

      setSearchResult(user)
    } catch (err) {
      setSearchUserError(
        err instanceof Error
          ? err.message
          : 'Cannot find user.',
      )
    } finally {
      setSearchingUser(false)
    }
  }

  // --------------------------------------------------
  // 12. CREATE / OPEN DIRECT CONVERSATION
  // --------------------------------------------------

  async function handleOpenDirectConversation(
    user: AuthUser,
  ) {
    if (user.userId === currentUserId) {
      setCreateConversationError(
        'You cannot create a conversation with yourself.',
      )
      return
    }

    setCreatingConversation(true)
    setCreateConversationError(null)

    try {
      const conversation =
        await createDirectConversationApi({
          userId: user.userId,
        })

      // Add conversation to the top of the list.
      setConversations((previous) => [
        conversation,
        ...previous.filter(
          (item) =>
            item.conversationId !==
            conversation.conversationId,
        ),
      ])

      // Store recipient information for the header and info panel.
      setDirectUsersByConversationId((previous) => ({
        ...previous,
        [conversation.conversationId]: user,
      }))

      // Select the conversation.
      setSelectedConversation(conversation)

      // Reset search.
      setSearchQuery('')
      setSearchResult(null)
      setSearchUserError(null)

      // setShowUserInfo(false)
      setShowEmojiPicker(false)
    } catch (err) {
      setCreateConversationError(
        err instanceof Error
          ? err.message
          : 'Cannot open conversation.',
      )
    } finally {
      setCreatingConversation(false)
    }
  }

  // --------------------------------------------------
  // 13. RENDER
  // --------------------------------------------------

  return (
    <div
      className="d-flex w-100 h-100 overflow-hidden"
      style={{ minHeight: 0 }}
    >
      {/* ============================================ */}
      {/* LEFT SIDEBAR */}
      {/* ============================================ */}

      {!isFromGroupPage && (
        <aside
          className="d-flex flex-column flex-shrink-0 border-end"
          style={{
            width: '320px',
            minWidth: '280px',
          }}
        >
          {/* Search users */}
          <div className="p-3 border-bottom">
            <strong className="d-block mb-2">
              New conversation
            </strong>

            <form onSubmit={handleSearchUser}>
              <div className="input-group">
                <input
                  type="search"
                  className="form-control form-control-sm"
                  placeholder="Username or email"
                  value={searchQuery}
                  onChange={(event) => {
                    setSearchQuery(event.target.value)
                    setSearchResult(null)
                    setSearchUserError(null)
                    setCreateConversationError(null)
                  }}
                  disabled={
                    searchingUser || creatingConversation
                  }
                />

                <button
                  type="submit"
                  className="btn btn-primary btn-sm"
                  disabled={
                    searchingUser ||
                    creatingConversation ||
                    !searchQuery.trim()
                  }
                >
                  {searchingUser ? 'Searching...' : 'Search'}
                </button>
              </div>
            </form>

            {searchUserError && (
              <div className="text-danger small mt-2">
                {searchUserError}
              </div>
            )}

            {/* Search result */}
            {searchResult && (
              <div className="border rounded p-2 mt-2">
                <div className="fw-semibold">
                  {searchResult.displayName ||
                    searchResult.username}
                </div>

                <div className="text-muted small">
                  @{searchResult.username}
                </div>

                <div className="text-muted small">
                  {searchResult.email}
                </div>

                <button
                  type="button"
                  className="btn btn-outline-primary btn-sm w-100 mt-2"
                  onClick={() =>
                    handleOpenDirectConversation(searchResult)
                  }
                  disabled={creatingConversation}
                >
                  {creatingConversation
                    ? 'Opening...'
                    : 'Message'}
                </button>
              </div>
            )}

            {createConversationError && (
              <div className="text-danger small mt-2">
                {createConversationError}
              </div>
            )}
          </div>

          {/* Conversation list */}
          <div className="p-3 pb-2">
            <strong>Conversations</strong>
          </div>

          <div className="flex-grow-1 overflow-auto">
            {isLoading && (
              <div className="p-3 text-muted small">
                Loading conversations...
              </div>
            )}

            {error && (
              <div className="p-3 text-danger small">
                {error}
              </div>
            )}

            {!isLoading &&
              !error &&
              conversations.length === 0 && (
                <div className="p-3 text-muted small">
                  No conversations found.
                </div>
              )}

            {!isLoading &&
              !error &&
              conversations.map((conversation) => {
                const displayInfo =
                  getConversationDisplayInfo(conversation)

                return (
                  <ConversationItem
                    key={conversation.conversationId}
                    conversation={conversation}
                    title={displayInfo.title}
                    avatar={displayInfo.avatar}
                    initials={displayInfo.initials}
                    active={
                      selectedConversation?.conversationId ===
                      conversation.conversationId
                    }
                    onClick={handleSelectConversation}
                  />
                )
              })}
          </div>
        </aside>
      )}

      {/* ============================================ */}
      {/* MAIN CHAT AREA */}
      {/* ============================================ */}

      <main
        className="d-flex flex-column flex-grow-1 overflow-hidden"
        style={{
          minWidth: 0,
          minHeight: 0,
        }}
      >
        {/* Chat header */}
        <div className="flex-shrink-0 border-bottom">
          <ChatHeader
            title={chatTitle}
            subtitle={chatSubtitle}
            type={selectedConversation?.type ?? 'DIRECT'}
            avatar={headerUser?.avatar || undefined}
            initials={
              headerUser ? initialsOf(headerUser.name) : undefined
            }
            // onInfoClick={() => {
            //   if (headerUser) {
            //     setShowUserInfo((previous) => !previous)
            //   }
            // }}
          />

          {/* WebSocket connection status */}
          {/* <div className="d-flex align-items-center gap-2 px-3 py-2 border-bottom bg-light">
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
          </div> */}
        </div>

        {/* Message list */}
        <div
          className="flex-grow-1 d-flex flex-column overflow-hidden"
          style={{ minHeight: 0 }}
        >
          {selectedConversation ? (
          isLoadingMessages ? (
            <div className="flex-grow-1 d-flex align-items-center justify-content-center text-muted">
              Initializing conversation...
            </div>
          ) : messageLoadError ? (
            <div className="flex-grow-1 d-flex flex-column align-items-center justify-content-center text-danger p-3 text-center">
              <div>Unable to load messages.</div>
              <small className="mt-2">
                {messageLoadError}
              </small>
            </div>
          ) : (
            <ChatMessages
              messages={visibleMessages}
              currentUserId={currentUserId}
            />
          )
        ) : (
          <div className="flex-grow-1 d-flex align-items-center justify-content-center text-muted p-3 text-center">
            {navigationError ||
              'Select a conversation to start chatting.'}
          </div>
        )}
        </div>

        {/* Message composer */}
        <form
          className="border-top flex-shrink-0 position-relative"
          onSubmit={handleSendMessage}
        >
          <MesToolbar
            onEmojiClick={() => {
              setShowEmojiPicker((previous) => !previous)
            }}
            onImageClick={
              selectedConversation
                ? () => imageInputRef.current?.click()
                : undefined
            }
            onFileClick={
              selectedConversation
                ? () => fileInputRef.current?.click()
                : undefined
            }
          />

          {/* Types and limits match POST /conversations/{id}/media. */}
          <input
            ref={imageInputRef}
            type="file"
            hidden
            accept="image/jpeg,image/png,image/gif,image/webp,video/mp4,video/webm,video/quicktime"
            onChange={handleSendFile}
          />
          <input
            ref={fileInputRef}
            type="file"
            hidden
            accept=".pdf,.txt,.csv,.zip,.doc,.docx,.xls,.xlsx,.ppt,.pptx"
            onChange={handleSendFile}
          />

          {attachError && (
            <div className="small text-danger px-2 pt-1">
              {attachError}
            </div>
          )}

          {/* Emoji picker */}
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
                !selectedConversation ||
                !newMessage.trim() ||
                isLoadingMessages ||
                Boolean(messageLoadError)
              }
            >
              <i className="bi bi-send fs-4" />
            </button>
          </div>
        </form>

        {/* ========================================== */}
        {/* WEBSOCKET DEBUG PANEL */}
        {/* ========================================== */}

        <div
          className="border-top bg-light p-2 flex-shrink-0"
          style={{
            maxHeight: '180px',
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
      </main>

      {/* ============================================ */}
      {/* RIGHT USER INFO PANEL */}
      {/* ============================================ */}

      {/* {showUserInfo && headerUser && (
        <aside className="flex-shrink-0">
          <UserInfoPanel
            user={headerUser}
            onClose={() => setShowUserInfo(false)}
          />
        </aside>
      )} */}
    </div>
  )
}

export default ChatPage
