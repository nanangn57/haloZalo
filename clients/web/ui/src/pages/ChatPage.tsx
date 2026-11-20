
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
import type { MessageCreatedPayload, MessagingEvent } from '@core/types/event'
import { mapEventToMessage } from '@core/types/messageMapper'

import ConversationItem from '../components/chat/ConversationItem'
import ChatHeader from '../components/chat/ChatHeader'
import ChatMessages from '../components/chat/ChatMessages'
import MesToolbar from '../components/chat/MesToolBar'
// import UserInfoPanel from '../components/user/UserPanel'

import {
  sendMessageApi,
  getMessagesApi,
  createGroupConversationApi,
  markConversationReadApi,
  type EmotionCode,
} from '@core/messageService'
import { uploadMediaApi, type MediaUploadType } from '@core/mediaService'
import { getGroupMembersApi, getGroupsApi } from '@core/groupService'
import { socketClient } from '@core/socketClient'
import type { SocketStatus } from '@core/socketClient'

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
  // Latest selection for socket callbacks; synced after render, not during it.
  const selectedConversationRef = useRef<Conversation | null>(null)
  useEffect(() => {
    selectedConversationRef.current = selectedConversation
  }, [selectedConversation])

  const [isLoading, setIsLoading] = useState(true)

  const [error, setError] = useState<string | null>(null)

  const [navigationError, setNavigationError] =
    useState<string | null>(null)

  // Messages
  const [messages, setMessages] = useState<Message[]>([])

  // Insert or replace by messageId/clientMsgId. A message can arrive twice: as the HTTP
  // response and as the /ws echo, in either order.
  function upsertMessage(incoming: Message) {
    setMessages((previous) => {
      const index = previous.findIndex(
        (message) =>
          (incoming.messageId !== undefined && message.messageId === incoming.messageId) ||
          message.clientMsgId === incoming.clientMsgId,
      )
      if (index < 0) {
        return [...previous, incoming]
      }
      return previous.map((message, i) => (i === index ? incoming : message))
    })
  }
  const messagesRef = useRef<Message[]>([])
  useEffect(() => {
    messagesRef.current = messages
  }, [messages])

  const [isLoadingMessages, setIsLoadingMessages] =
    useState(false)

  const [messageLoadError, setMessageLoadError] =
    useState<string | null>(null)


  const [newMessage, setNewMessage] = useState('')

  const [showEmojiPicker, setShowEmojiPicker] =
    useState(false)

  const [showEmotionBar, setShowEmotionBar] = useState(false)
  const [showSocketDebug, setShowSocketDebug] = useState(false)
  const [uploadingMedia, setUploadingMedia] = useState(false)
  const mediaInputRef = useRef<HTMLInputElement | null>(null)
  const mediaTypeRef = useRef<MediaUploadType>('IMAGE')

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

  // Identity group names keyed by groupId (= conversationId for GROUP).
  const [groupNamesById, setGroupNamesById] = useState<
    Record<string, string>
  >({})

  // Ids already asked for, so an id without an account is not asked again.
  const requestedUserIds = useRef<Set<string>>(new Set())

  // WebSocket
  const [socketStatus, setSocketStatus] =
    useState<SocketStatus>('disconnected')

  const [socketEvents, setSocketEvents] =
    useState<MessagingEvent[]>([])

  const [typingUserId, setTypingUserId] = useState<string | null>(null)
  const [peerOnline, setPeerOnline] = useState(false)
  const typingTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const lastTypingSentRef = useRef(false)

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
          setUsersById((previous) => {
            const next = { ...previous }
            result.members.forEach((member) => {
              if (!next[member.userId]) {
                next[member.userId] = {
                  userId: member.userId,
                  username: member.username,
                  displayName: member.username,
                }
              }
            })
            return next
          })
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
  // 5b. LOAD GROUP NAMES + MEMBER NAMES FOR LIST/HEADER
  // --------------------------------------------------

  useEffect(() => {
    const hasGroup = conversations.some(
      (conversation) => conversation.type === 'GROUP',
    )
    if (!hasGroup) {
      return
    }

    let cancelled = false
    void getGroupsApi()
      .then((groups) => {
        if (cancelled) {
          return
        }
        const next: Record<string, string> = {}
        groups.forEach((group) => {
          const name = group.name?.trim()
          if (name) {
            next[group.groupId] = name
          }
        })
        setGroupNamesById((previous) => ({ ...previous, ...next }))
      })
      .catch((err) => {
        console.error('Cannot load group names:', err)
      })

    return () => {
      cancelled = true
    }
  }, [conversations])

  useEffect(() => {
    const missing = [
      ...new Set(
        conversations
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

        const maxSeq = result.messages.reduce(
          (max, message) =>
            typeof message.seq === 'number' ? Math.max(max, message.seq) : max,
          0,
        )
        if (maxSeq > 0) {
          void markConversationReadApi(currentConversationId, maxSeq).catch(
            (err) => console.error('Cannot mark read:', err),
          )
        }
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

  useEffect(() => {
    const mergeIncoming = upsertMessage

    const unsubscribeEvent = socketClient.subscribe((event) => {
      console.log('Received WebSocket event:', event)

      setSocketEvents((previous) => [event as unknown as MessagingEvent, ...previous])

      if (event.type === 'messaging.read.updated') {
        const payload = event.payload as {
          conversationId?: string
          lastReadSeq?: number
          unreadCount?: number
        }
        if (!payload.conversationId) return
        setConversations((previous) =>
          previous.map((conversation) =>
            conversation.conversationId === payload.conversationId
              ? {
                  ...conversation,
                  lastReadSeq: payload.lastReadSeq ?? conversation.lastReadSeq,
                  unreadCount:
                    payload.unreadCount ?? conversation.unreadCount,
                }
              : conversation,
          ),
        )
        return
      }

      if (event.type === 'messaging.typing') {
        const payload = event.payload as {
          conversationId?: string
          userId?: string
          typing?: boolean
        }
        if (
          !payload.conversationId ||
          payload.conversationId !==
            selectedConversationRef.current?.conversationId ||
          payload.userId === currentUserId
        ) {
          return
        }
        setTypingUserId(payload.typing ? payload.userId || null : null)
        return
      }

      if (event.type === 'messaging.presence') {
        const payload = event.payload as {
          conversationId?: string
          userId?: string
          status?: string
        }
        if (
          !payload.conversationId ||
          payload.conversationId !==
            selectedConversationRef.current?.conversationId ||
          payload.userId === currentUserId
        ) {
          return
        }
        setPeerOnline(payload.status === 'online')
        return
      }

      if (event.type !== 'messaging.message.created') {
        return
      }

      // Payload is the contract's Message; the shared mapper keeps media and signs-in its link.
      const payload = event.payload as unknown as MessageCreatedPayload

      if (
        !payload.messageId ||
        !payload.conversationId ||
        !payload.clientMsgId
      ) {
        return
      }

      mergeIncoming(mapEventToMessage(payload))

      if (
        selectedConversationRef.current?.conversationId ===
          payload.conversationId &&
        typeof payload.seq === 'number'
      ) {
        void markConversationReadApi(
          payload.conversationId,
          payload.seq,
        ).catch((err) => console.error('Cannot mark read:', err))
      }
    })

    const unsubscribeStatus = socketClient.subscribeStatus((status) => {
      setSocketStatus(status)
    })

    const unsubscribeReconnect = socketClient.subscribeReconnect(() => {
      const activeId = selectedConversationRef.current?.conversationId
      if (!activeId) return

      const afterSeq = messagesRef.current.reduce(
        (max, message) =>
          message.conversationId === activeId && typeof message.seq === 'number'
            ? Math.max(max, message.seq)
            : max,
        0,
      )

      void getMessagesApi(activeId, { afterSeq }).then((page) => {
        page.messages.forEach(mergeIncoming)
      })
    })

    socketClient.connect()

    return () => {
      unsubscribeEvent()
      unsubscribeStatus()
      unsubscribeReconnect()
      socketClient.disconnect()
    }
  }, [])

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

  function memberDisplayNames(
    memberIds: string[],
    options?: { excludeSelf?: boolean },
  ): string[] {
    const excludeSelf = options?.excludeSelf ?? true
    const ids = excludeSelf
      ? memberIds.filter((id) => id !== currentUserId)
      : memberIds

    const names = ids
      .map((id) => usersById[id])
      .filter(Boolean)
      .map((user) => displayName(user!))

    // If everyone else is unresolved, show whoever we can resolve.
    if (names.length === 0 && excludeSelf) {
      return memberDisplayNames(memberIds, { excludeSelf: false })
    }
    return names
  }

  function formatMemberTitle(names: string[]): string {
    if (names.length === 0) {
      return ''
    }
    if (names.length <= 3) {
      return names.join(', ')
    }
    return `${names.slice(0, 3).join(', ')}…`
  }

  function groupInitials(memberIds: string[]): string {
    const names = memberDisplayNames(memberIds)
    if (names.length >= 2) {
      return `${initialsOf(names[0])}${initialsOf(names[1])}`
    }
    if (names.length === 1) {
      return initialsOf(names[0]) || 'G'
    }
    return 'G'
  }

  function resolveGroupTitle(conversation: Conversation): string {
    if (
      conversation.conversationId === requestedConversationId &&
      requestedConversationTitle.trim()
    ) {
      return requestedConversationTitle.trim()
    }

    const named = groupNamesById[conversation.conversationId]?.trim()
    if (named) {
      return named
    }

    const fromMembers = formatMemberTitle(
      memberDisplayNames(conversation.memberIds),
    )
    if (fromMembers) {
      return fromMembers
    }

    return 'Group conversation'
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
      // Prefer identity members of the open group when list memberIds are thin.
      const memberIds =
        groupMembers.length > 0
          ? groupMembers.map((member) => member.userId)
          : selectedConversation.memberIds
      const titled = resolveGroupTitle({
        ...selectedConversation,
        memberIds,
      })
      return titled
    }

    return (
      headerUser?.name ||
      'Direct conversation'
    )
  })()

  // Typing and presence belong to one conversation: reset them while rendering a
  // new selection (React's "adjust state when a prop changes"), not in an effect.
  const [presenceConversationId, setPresenceConversationId] = useState(
    selectedConversation?.conversationId,
  )
  if (presenceConversationId !== selectedConversation?.conversationId) {
    setPresenceConversationId(selectedConversation?.conversationId)
    setTypingUserId(null)
    setPeerOnline(false)
  }

  useEffect(() => {
    const conversationId = selectedConversation?.conversationId
    lastTypingSentRef.current = false
    if (!conversationId) {
      return
    }
    socketClient.sendPresence(conversationId, 'online')
    return () => {
      socketClient.sendPresence(conversationId, 'offline')
      socketClient.sendTyping(conversationId, false)
    }
  }, [selectedConversation?.conversationId])

  // Resolve subtitle.
  const chatSubtitle = (() => {
    if (typingUserId) {
      return 'Đang nhập…'
    }
    if (selectedConversation?.type === 'GROUP') {
      if (loadingGroupMembers) {
        return 'Loading members...'
      }
      const count = groupMembers.length
      return `${count} member${count === 1 ? '' : 's'}`
    }
    if (selectedConversation?.type === 'DIRECT') {
      return peerOnline ? 'Online' : 'Offline'
    }
    return ''
  })()

  // Resolve conversation list display information.
  function getConversationDisplayInfo(
    conversation: Conversation,
  ) {
    if (conversation.type === 'GROUP') {
      return {
        title: resolveGroupTitle(conversation),
        avatar: undefined,
        initials: groupInitials(conversation.memberIds),
      }
    }

    const user = getDirectUser(conversation)
    const title = user ? displayName(user) : 'Direct conversation'

    return {
      title,
      avatar: undefined,
      initials: user ? initialsOf(title) : '?',
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
    lastTypingSentRef.current = false
    socketClient.sendTyping(conversationId, false)

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

  async function handleSendEmotion(code: EmotionCode) {
    if (!selectedConversation || isLoadingMessages || messageLoadError) {
      return
    }

    const conversationId = selectedConversation.conversationId
    const clientMsgId = crypto.randomUUID()
    setShowEmotionBar(false)

    try {
      const saved = await sendMessageApi(conversationId, {
        type: 'EMOTION',
        body: code,
        clientMsgId,
      })
      upsertMessage(saved)
    } catch (err) {
      console.error('Cannot send emotion:', err)
    }
  }

  // Types and limits match POST /conversations/{id}/media. The button only filters
  // the picker; the server decides the real kind from the file.
  const MEDIA_ACCEPT: Record<MediaUploadType, string> = {
    IMAGE: 'image/jpeg,image/png,image/gif,image/webp',
    VIDEO: 'video/mp4,video/webm,video/quicktime',
    DOCUMENT: '.pdf,.txt,.csv,.zip,.doc,.docx,.xls,.xlsx,.ppt,.pptx',
  }

  function pickMedia(type: MediaUploadType) {
    mediaTypeRef.current = type
    if (mediaInputRef.current) {
      mediaInputRef.current.accept = MEDIA_ACCEPT[type]
      mediaInputRef.current.click()
    }
  }

  async function handleMediaSelected(
    event: React.ChangeEvent<HTMLInputElement>,
  ) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file || !selectedConversation) return

    const conversationId = selectedConversation.conversationId
    setUploadingMedia(true)
    try {
      // Step 1: upload. Step 2: send a message of the kind the server decided.
      const media = await uploadMediaApi(conversationId, file, file.name)
      const saved = await sendMessageApi(conversationId, {
        type: media.kind,
        mediaId: media.mediaId,
        clientMsgId: crypto.randomUUID(),
      })
      upsertMessage(saved)
    } catch (err) {
      console.error('Cannot upload media:', err)
      setError(err instanceof Error ? err.message : 'Upload failed')
    } finally {
      setUploadingMedia(false)
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

            <form
              className="chat-search-form"
              onSubmit={handleSearchUser}
            >
              <input
                type="search"
                className="form-control form-control-sm chat-search-input"
                placeholder="Username or email"
                value={searchQuery}
                onChange={(event) => {
                  setSearchQuery(event.target.value)
                  setSearchResult(null)
                  setSearchUserError(null)
                  setCreateConversationError(null)
                }}
                disabled={searchingUser || creatingConversation}
              />

              <button
                type="submit"
                className="app-button app-button-primary chat-search-button"
                disabled={
                  searchingUser ||
                  creatingConversation ||
                  !searchQuery.trim()
                }
              >
                {searchingUser ? '…' : 'Search'}
              </button>
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
        <div className="chat-thread-header flex-shrink-0">
          <ChatHeader
            title={chatTitle}
            subtitle={chatSubtitle}
            type={selectedConversation?.type ?? 'DIRECT'}
            avatar={headerUser?.avatar || undefined}
            initials={
              selectedConversation?.type === 'GROUP'
                ? groupInitials(selectedConversation.memberIds)
                : headerUser
                  ? initialsOf(headerUser.name)
                  : '?'
            }
          />
          <button
            type="button"
            className="chat-socket-pill"
            onClick={() => setShowSocketDebug((previous) => !previous)}
            title="Toggle connection debug"
          >
            <span
              className={`chat-socket-dot ${
                socketStatus === 'connected'
                  ? 'is-ok'
                  : socketStatus === 'error'
                    ? 'is-bad'
                    : ''
              }`}
            />
            {socketStatus}
          </button>
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
          className="chat-composer flex-shrink-0 position-relative"
          onSubmit={handleSendMessage}
        >
          <MesToolbar
            disabled={!selectedConversation || uploadingMedia}
            emojiOpen={showEmojiPicker}
            emotionOpen={showEmotionBar}
            onEmojiClick={() => {
              setShowEmojiPicker((previous) => !previous)
              setShowEmotionBar(false)
            }}
            onEmotionClick={() => {
              setShowEmotionBar((previous) => !previous)
              setShowEmojiPicker(false)
            }}
            onImageClick={() => pickMedia('IMAGE')}
            onFileClick={() => pickMedia('DOCUMENT')}
            onVideoClick={() => pickMedia('VIDEO')}
          />

          <input
            ref={mediaInputRef}
            type="file"
            className="d-none"
            onChange={handleMediaSelected}
          />

          {showEmojiPicker ? (
            <div className="chat-emoji-popover" role="dialog" aria-label="Emoji picker">
              <EmojiPicker
                width={320}
                height={360}
                previewConfig={{ showPreview: false }}
                onEmojiClick={(emojiData: EmojiClickData) => {
                  setNewMessage((previous) => previous + emojiData.emoji)
                  setShowEmojiPicker(false)
                }}
              />
            </div>
          ) : null}

          {showEmotionBar ? (
            <div className="chat-emotion-bar">
              {(
                [
                  ['like', '👍'],
                  ['love', '❤️'],
                  ['haha', '😄'],
                  ['wow', '😮'],
                  ['sad', '😢'],
                  ['angry', '😠'],
                ] as const
              ).map(([code, emoji]) => (
                <button
                  key={code}
                  type="button"
                  className="chat-emotion-chip"
                  disabled={!selectedConversation || uploadingMedia}
                  onClick={() => void handleSendEmotion(code)}
                  title={code}
                  aria-label={`Send ${code}`}
                >
                  {emoji}
                </button>
              ))}
            </div>
          ) : null}

          <div className="chat-composer-row">
            <input
              type="text"
              className="form-control chat-composer-input"
              placeholder={
                selectedConversation
                  ? uploadingMedia
                    ? 'Uploading…'
                    : 'Type a message...'
                  : 'Select a conversation first'
              }
              value={newMessage}
              disabled={!selectedConversation || uploadingMedia}
              onChange={(event) => {
                const value = event.target.value
                setNewMessage(value)
                const conversationId =
                  selectedConversation?.conversationId
                if (!conversationId) {
                  return
                }
                if (value.trim() && !lastTypingSentRef.current) {
                  lastTypingSentRef.current = true
                  socketClient.sendTyping(conversationId, true)
                }
                if (typingTimerRef.current) {
                  clearTimeout(typingTimerRef.current)
                }
                typingTimerRef.current = setTimeout(() => {
                  lastTypingSentRef.current = false
                  socketClient.sendTyping(conversationId, false)
                }, 1500)
              }}
            />

            <button
              className="app-button app-button-primary chat-send-button"
              type="submit"
              aria-label="Send message"
              disabled={
                !selectedConversation ||
                !newMessage.trim() ||
                isLoadingMessages ||
                Boolean(messageLoadError) ||
                uploadingMedia
              }
            >
              <i className="bi bi-send" aria-hidden="true" />
            </button>
          </div>
        </form>

        {showSocketDebug ? (
          <div className="chat-debug-panel flex-shrink-0">
            <div className="d-flex justify-content-between align-items-center mb-2">
              <strong className="small">WebSocket Debug</strong>
              <div className="d-flex gap-2 align-items-center">
                <small className="text-muted">
                  {socketEvents.length} events
                </small>
                <button
                  type="button"
                  className="app-button"
                  style={{ padding: '4px 10px', fontSize: 12 }}
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
                    <strong className="small">{event.type}</strong>
                    <small className="text-muted">{event.producer}</small>
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
        ) : null}
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
