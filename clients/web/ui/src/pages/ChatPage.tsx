
import { useLocation, useParams } from 'react-router-dom'
import { useEffect, useRef, useState } from 'react'

import type {
  Conversation,
  ConversationPrefsPatch,
} from '@core/types/conversation'
import { applyConversationPrefs } from '@core/conversationPrefs'
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
import ConversationItem from '../components/chat/ConversationItem'
import ChatHeader from '../components/chat/ChatHeader'
import ChatMessages from '../components/chat/ChatMessages'
import MesToolbar from '../components/chat/MesToolBar'
import MediaGalleryPanel from '../components/chat/MediaGalleryPanel'
import { useVoiceRecorder } from '../hooks/useVoiceRecorder'
// import UserInfoPanel from '../components/user/UserPanel'

import {
  sendMessageApi,
  getMessagesApi,
  createGroupConversationApi,
  markConversationReadApi,
  deleteMessageApi,
  addReactionApi,
  removeReactionApi,
  listReactionsApi,
  forwardMessageApi,
  updateConversationPrefsApi,
  type EmotionCode,
  type MessageReaction,
  type ReactionCode,
} from '@core/messageService'
import { getInitials } from '@core/avatar'
import { uploadMediaApi, type MediaUploadType } from '@core/mediaService'
import { getGroupMembersApi, getGroupsApi } from '@core/groupService'
import { socketClient } from '@core/socketClient'
import type { MessagingEvent, SocketStatus } from '@core/socketClient'

import EmojiPicker, {
  type EmojiClickData,
} from 'emoji-picker-react'

// ======================================================
// TYPES
// ======================================================

// Loại chọn từ hộp thoại file. AUDIO đi đường ghi âm riêng.
type FilePickType = Exclude<MediaUploadType, 'AUDIO'>

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
  const selectedConversationRef = useRef<Conversation | null>(null)

  const [isLoading, setIsLoading] = useState(true)

  const [error, setError] = useState<string | null>(null)

  const [navigationError, setNavigationError] =
    useState<string | null>(null)

  // Messages
  const [messages, setMessages] = useState<Message[]>([])
  const messagesRef = useRef<Message[]>([])

  useEffect(() => {
    selectedConversationRef.current = selectedConversation
  }, [selectedConversation])

  useEffect(() => {
    messagesRef.current = messages
  }, [messages])

  const [isLoadingMessages, setIsLoadingMessages] =
    useState(false)

  const [messageLoadError, setMessageLoadError] =
    useState<string | null>(null)

  const [newMessage, setNewMessage] = useState('')

  // Reply / forward / reactions / recall
  const [replyTarget, setReplyTarget] = useState<Message | null>(null)
  const [forwardTarget, setForwardTarget] = useState<Message | null>(null)
  const [forwardingTo, setForwardingTo] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [reactionsByMessage, setReactionsByMessage] = useState<
    Record<string, MessageReaction[]>
  >({})

  const [showEmojiPicker, setShowEmojiPicker] =
    useState(false)

  const [showEmotionBar, setShowEmotionBar] = useState(false)
  const [showSocketDebug, setShowSocketDebug] = useState(false)
  const [uploadingMedia, setUploadingMedia] = useState(false)
  const mediaInputRef = useRef<HTMLInputElement | null>(null)
  const mediaTypeRef = useRef<FilePickType>('IMAGE')
  const [showGallery, setShowGallery] = useState(false)
  const voice = useVoiceRecorder()

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
        setReplyTarget(null)
        setReactionsByMessage({})

        // Reaction không nằm trong trang tin: lấy cho các tin gần nhất.
        // Endpoint chưa có thì listReactionsApi trả [] nên UI không vỡ.
        const recent = result.messages
          .filter(
            (message) =>
              message.messageId && !message.deleted && message.type !== 'EMOTION',
          )
          .slice(-30)
        void Promise.all(
          recent.map((message) =>
            listReactionsApi(currentConversationId, message.messageId as string)
              .then((items) => [message.messageId as string, items] as const)
              .catch(() => null),
          ),
        ).then((entries) => {
          if (!isMounted) return
          setReactionsByMessage((previous) => {
            const next = { ...previous }
            entries.forEach((entry) => {
              if (entry && entry[1].length > 0) next[entry[0]] = entry[1]
            })
            return next
          })
        })

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
    const mergeIncoming = (incomingMessage: Message) => {
      setMessages((previous) => {
        const existingIndex = previous.findIndex(
          (message) =>
            message.messageId === incomingMessage.messageId ||
            message.clientMsgId === incomingMessage.clientMsgId,
        )

        if (existingIndex >= 0) {
          return previous.map((message, index) =>
            index === existingIndex ? incomingMessage : message,
          )
        }

        return [...previous, incomingMessage]
      })
    }

    const unsubscribeEvent = socketClient.subscribe((event) => {
      console.log('Received WebSocket event:', event)

      setSocketEvents((previous) => [event, ...previous])

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

      if (event.type === 'messaging.message.deleted') {
        const payload = event.payload as { messageId?: string }
        if (!payload.messageId) return
        setMessages((previous) =>
          previous.map((message) =>
            message.messageId === payload.messageId
              ? { ...message, content: '', deleted: true }
              : message,
          ),
        )
        return
      }

      if (
        event.type === 'messaging.reaction.added' ||
        event.type === 'messaging.reaction.removed'
      ) {
        const payload = event.payload as {
          messageId?: string
          userId?: string
          code?: ReactionCode
        }
        if (!payload.messageId || !payload.userId || !payload.code) return
        const { messageId, userId, code } = payload
        setReactionsByMessage((previous) => {
          const current = previous[messageId] ?? []
          const without = current.filter(
            (item) => !(item.userId === userId && item.code === code),
          )
          if (event.type === 'messaging.reaction.removed') {
            return { ...previous, [messageId]: without }
          }
          return {
            ...previous,
            [messageId]: [
              ...without,
              { userId, code, createdAt: event.occurredAt },
            ],
          }
        })
        return
      }

      if (event.type !== 'messaging.message.created') {
        return
      }

      const payload = event.payload as {
        messageId: string
        conversationId: string
        seq: number
        senderId: string
        type: Message['type']
        body: string
        clientMsgId: string
        createdAt: string
        replyTo?: string
        forwardedFrom?: string
      }

      if (
        !payload.messageId ||
        !payload.conversationId ||
        !payload.clientMsgId
      ) {
        return
      }

      const createdAt = Date.parse(payload.createdAt)

      mergeIncoming({
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
        replyTo: payload.replyTo,
        forwardedFrom: payload.forwardedFrom,
      })

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
    return getInitials(name, 1)
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

  useEffect(() => {
    const conversationId = selectedConversation?.conversationId
    lastTypingSentRef.current = false
    if (!conversationId) {
      return
    }
    // Reset ephemeral UI after paint so we do not sync-setState in the effect body.
    const reset = window.setTimeout(() => {
      setTypingUserId(null)
      setPeerOnline(false)
    }, 0)
    socketClient.sendPresence(conversationId, 'online')
    return () => {
      window.clearTimeout(reset)
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
    const replyToId = replyTarget?.messageId

    // Optimistic message.
    const optimisticMessage: Message = {
      id: clientMsgId,
      conversationId,
      senderId: currentUserId,
      type: 'TEXT',
      content,
      clientMsgId,
      replyTo: replyToId,
      sentAt: Math.floor(Date.now() / 1000),
      status: 'sending',
    }

    setMessages((previous) => [
      ...previous,
      optimisticMessage,
    ])

    setNewMessage('')
    setReplyTarget(null)
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
          ...(replyToId ? { replyTo: replyToId } : {}),
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
      setMessages((previous) => [...previous, saved])
    } catch (err) {
      console.error('Cannot send emotion:', err)
    }
  }

  // --------------------------------------------------
  // MESSAGE ACTIONS: recall / react / reply / forward
  // --------------------------------------------------

  async function handleRecall(message: Message) {
    if (!message.messageId || message.senderId !== currentUserId) return
    if (!window.confirm('Recall this message for everyone?')) return

    setActionError(null)
    try {
      await deleteMessageApi(message.conversationId, message.messageId)
      setMessages((previous) =>
        previous.map((item) =>
          item.messageId === message.messageId
            ? { ...item, content: '', deleted: true }
            : item,
        ),
      )
    } catch (err) {
      setActionError(
        err instanceof Error ? err.message : 'Cannot recall message.',
      )
    }
  }

  async function handleToggleReaction(message: Message, code: ReactionCode) {
    const messageId = message.messageId
    if (!messageId) return

    const mine = (reactionsByMessage[messageId] ?? []).some(
      (item) => item.userId === currentUserId && item.code === code,
    )

    // Optimistic update, rollback on failure.
    const apply = (adding: boolean) =>
      setReactionsByMessage((previous) => {
        const current = previous[messageId] ?? []
        const without = current.filter(
          (item) => !(item.userId === currentUserId && item.code === code),
        )
        return {
          ...previous,
          [messageId]: adding
            ? [
                ...without,
                {
                  userId: currentUserId,
                  code,
                  createdAt: new Date().toISOString(),
                },
              ]
            : without,
        }
      })

    setActionError(null)
    apply(!mine)
    try {
      if (mine) {
        await removeReactionApi(message.conversationId, messageId, code)
      } else {
        await addReactionApi(message.conversationId, messageId, code)
      }
    } catch (err) {
      apply(mine)
      setActionError(
        err instanceof Error ? err.message : 'Cannot update reaction.',
      )
    }
  }

  async function handleForwardTo(target: Conversation) {
    const source = forwardTarget
    if (!source?.messageId) return

    setForwardingTo(target.conversationId)
    setActionError(null)
    try {
      if (target.type === 'GROUP') {
        await createGroupConversationApi(target.conversationId)
      }
      const saved = await forwardMessageApi(target.conversationId, {
        messageId: source.messageId,
        clientMsgId: crypto.randomUUID(),
      })
      if (target.conversationId === selectedConversation?.conversationId) {
        setMessages((previous) =>
          previous.some((item) => item.messageId === saved.messageId)
            ? previous
            : [...previous, saved],
        )
      }
      setForwardTarget(null)
    } catch (err) {
      setActionError(
        err instanceof Error ? err.message : 'Cannot forward message.',
      )
    } finally {
      setForwardingTo(null)
    }
  }

  function senderNameOf(userId: string): string {
    const user = usersById[userId]
    return user ? displayName(user) : 'User'
  }

  function pickMedia(type: FilePickType) {
    mediaTypeRef.current = type
    mediaInputRef.current?.click()
  }

  async function handleMediaSelected(
    event: React.ChangeEvent<HTMLInputElement>,
  ) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file || !selectedConversation) return

    const conversationId = selectedConversation.conversationId
    const type = mediaTypeRef.current
    setUploadingMedia(true)
    try {
      const uploaded = await uploadMediaApi(file, type, {
        fileName: file.name,
        conversationId,
      })
      const clientMsgId = crypto.randomUUID()
      const saved =
        type === 'DOCUMENT'
          ? await sendMessageApi(conversationId, {
              type: 'DOCUMENT',
              body: uploaded.url,
              clientMsgId,
              fileName: uploaded.fileName,
              mimeType: uploaded.contentType,
              size: uploaded.size,
            })
          : await sendMessageApi(conversationId, {
              type,
              body: uploaded.url,
              clientMsgId,
            })
      setMessages((previous) => [...previous, saved])
    } catch (err) {
      console.error('Cannot upload media:', err)
      setError(err instanceof Error ? err.message : 'Upload failed')
    } finally {
      setUploadingMedia(false)
    }
  }

  // Voice note: giữ nút mic để ghi, nhả ra thì upload AUDIO rồi gửi tin AUDIO.
  async function handleVoiceStop() {
    const conversationId = selectedConversation?.conversationId
    const clip = await voice.stop()
    if (!clip || !conversationId) return

    setActionError(null)
    setUploadingMedia(true)
    try {
      const uploaded = await uploadMediaApi(clip.blob, 'AUDIO', {
        fileName: clip.fileName,
        conversationId,
      })
      const saved = await sendMessageApi(conversationId, {
        type: 'AUDIO',
        body: uploaded.url,
        clientMsgId: crypto.randomUUID(),
        mimeType: uploaded.contentType || clip.mimeType,
        size: uploaded.size,
        duration: clip.durationSec,
        fileName: uploaded.fileName,
      })
      setMessages((previous) => [...previous, saved])
    } catch (err) {
      console.error('Cannot send voice message:', err)
      setActionError(
        err instanceof Error ? err.message : 'Cannot send voice message.',
      )
    } finally {
      setUploadingMedia(false)
    }
  }

  // Pin / mute / archive từ menu của hội thoại. Server chưa hỗ trợ thì giữ nguyên.
  async function handleUpdatePrefs(
    conversation: Conversation,
    patch: ConversationPrefsPatch,
  ) {
    setActionError(null)
    try {
      const ok = await updateConversationPrefsApi(
        conversation.conversationId,
        patch,
      )
      if (!ok) {
        setActionError('Server does not support conversation preferences yet.')
        return
      }
      setConversations((previous) =>
        applyConversationPrefs(previous, conversation.conversationId, patch),
      )
      if (
        patch.archived &&
        selectedConversationRef.current?.conversationId ===
          conversation.conversationId
      ) {
        setSelectedConversation(null)
        setMessages([])
      }
    } catch (err) {
      setActionError(
        err instanceof Error ? err.message : 'Cannot update conversation.',
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
                <div className="p-3 app-empty-state">
                  <p className="mb-2 fw-semibold">Chưa có hội thoại</p>
                  <p className="mb-0 small text-muted">
                    Tìm bạn bằng email ở Contacts, hoặc tạo nhóm để bắt đầu chat.
                  </p>
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
                    currentUserId={currentUserId}
                    active={
                      selectedConversation?.conversationId ===
                      conversation.conversationId
                    }
                    onClick={handleSelectConversation}
                    onUpdatePrefs={(target, patch) =>
                      void handleUpdatePrefs(target, patch)
                    }
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
            onMediaClick={
              selectedConversation ? () => setShowGallery(true) : undefined
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
              reactions={reactionsByMessage}
              getSenderName={senderNameOf}
              onReply={setReplyTarget}
              onForward={setForwardTarget}
              onRecall={(message) => void handleRecall(message)}
              onToggleReaction={(message, code) =>
                void handleToggleReaction(message, code)
              }
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
          {actionError ? (
            <div className="text-danger small px-3 pt-2" role="alert">
              {actionError}
            </div>
          ) : null}

          {replyTarget ? (
            <div className="chat-reply-banner">
              <div className="chat-reply-banner-copy">
                <strong>
                  Replying to{' '}
                  {replyTarget.senderId === currentUserId
                    ? 'yourself'
                    : senderNameOf(replyTarget.senderId)}
                </strong>
                <span>
                  {replyTarget.type === 'TEXT'
                    ? replyTarget.content
                    : `[${replyTarget.type.toLowerCase()}]`}
                </span>
              </div>
              <button
                type="button"
                className="app-icon-button"
                aria-label="Cancel reply"
                onClick={() => setReplyTarget(null)}
              >
                <i className="bi bi-x-lg" aria-hidden="true" />
              </button>
            </div>
          ) : null}

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
            recording={voice.status === 'recording'}
            onVoiceStart={() => void voice.start()}
            onVoiceStop={() => void handleVoiceStop()}
          />

          {voice.status === 'recording' ? (
            <div className="text-danger small px-3 pt-1" role="status">
              <i className="bi bi-record-circle" aria-hidden="true" /> Recording…
              release to send
            </div>
          ) : null}
          {voice.error ? (
            <div className="text-danger small px-3 pt-1" role="alert">
              {voice.error}
            </div>
          ) : null}

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

      {showGallery && selectedConversation ? (
        <MediaGalleryPanel
          messages={visibleMessages}
          getSenderName={senderNameOf}
          onClose={() => setShowGallery(false)}
        />
      ) : null}

      {forwardTarget ? (
        <div
          className="app-modal-backdrop"
          role="presentation"
          onClick={() => setForwardTarget(null)}
        >
          <div
            className="app-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="forward-dialog-title"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="app-modal-header">
              <h2 id="forward-dialog-title">Forward to…</h2>
              <button
                type="button"
                className="app-icon-button"
                aria-label="Close"
                onClick={() => setForwardTarget(null)}
              >
                <i className="bi bi-x-lg" aria-hidden="true" />
              </button>
            </div>

            {conversations.length === 0 ? (
              <p className="text-muted">No conversations to forward to.</p>
            ) : (
              <div style={{ maxHeight: 360, overflowY: 'auto' }}>
                {conversations.map((conversation) => {
                  const info = getConversationDisplayInfo(conversation)
                  return (
                    <button
                      key={conversation.conversationId}
                      type="button"
                      className="conversation-item"
                      disabled={forwardingTo !== null}
                      onClick={() => void handleForwardTo(conversation)}
                    >
                      <span
                        className={`conversation-avatar ${
                          conversation.type === 'GROUP' ? 'is-group' : 'is-direct'
                        }`}
                        aria-hidden="true"
                      >
                        {info.initials}
                      </span>
                      <span className="conversation-copy">
                        <span className="conversation-title">{info.title}</span>
                      </span>
                      {forwardingTo === conversation.conversationId ? (
                        <span className="small text-muted">Sending…</span>
                      ) : null}
                    </button>
                  )
                })}
              </div>
            )}

            {actionError ? (
              <div className="auth-alert auth-alert-error mt-3" role="alert">
                {actionError}
              </div>
            ) : null}
          </div>
        </div>
      ) : null}

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
