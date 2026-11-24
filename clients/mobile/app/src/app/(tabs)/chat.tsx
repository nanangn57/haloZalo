import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  TextInput,
  Pressable,
  KeyboardAvoidingView,
  Platform,
  ActivityIndicator,
  Modal,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useLocalSearchParams, useRouter } from 'expo-router';
import ChatHeader from '../../components/chat/ChatHeader';
import ChatMessages from '../../components/chat/ChatMessages';
import ConversationItem from '../../components/chat/ConversationItem';
import MediaGalleryModal from '../../components/chat/MediaGalleryModal';
import MesToolBar from '../../components/chat/MesToolBar';
import { getAuth } from '@core/authSession';
import { getConversationsApi } from '@core/conversationService';
import {
  getMessagesApi,
  sendMessageApi,
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
} from '@core/messageService';
import { confirmAction } from '@/lib/confirm';
import {
  assertMediaWithinLimit,
  guessMimeType,
  mediaMessageBody,
  uploadMediaApi,
  type MediaUploadType,
} from '@core/mediaService';
import { lookupUsersApi } from '@core/userService';
import { getGroupsApi } from '@core/groupService';
import { getPresenceApi } from '@core/presenceService';
import { socketClient } from '@core/socketClient';
import { applyConversationPrefs } from '@core/conversationPrefs';
import type { Conversation, ConversationPrefsPatch } from '@core/types/conversation';
import type { Message } from '@core/types/message';
import * as ImagePicker from 'expo-image-picker';
import * as DocumentPicker from 'expo-document-picker';
import { VoiceRecorder, VoiceRecorderError } from '@/lib/voiceRecorder';
import { useTheme } from '@/hooks/use-theme';
import { Radius, Spacing } from '@/constants/theme';

/** Chat header poll nhanh hơn Contacts. */
const PRESENCE_POLL_MS = 5_000;

/** Trang tin khi mở hội thoại / scroll lên tải thêm. */
const HISTORY_PAGE_SIZE = 10;

function uuid(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export default function ChatPage() {
  const router = useRouter();
  const theme = useTheme();
  const params = useLocalSearchParams<{
    conversationId?: string;
    conversationType?: string;
    title?: string;
  }>();
  const auth = getAuth();
  const currentUserId = auth?.user?.userId ?? '';

  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [selectedConversation, setSelectedConversation] = useState<Conversation | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const messagesRef = useRef<Message[]>([]);
  messagesRef.current = messages;
  const selectedRef = useRef<Conversation | null>(null);
  selectedRef.current = selectedConversation;

  const [titles, setTitles] = useState<Record<string, string>>({});
  const [usersById, setUsersById] = useState<Record<string, string>>({});
  const [reactionsByMessage, setReactionsByMessage] = useState<Record<string, MessageReaction[]>>({});
  const [actionTarget, setActionTarget] = useState<Message | null>(null);
  const [replyTarget, setReplyTarget] = useState<Message | null>(null);
  const [forwardTarget, setForwardTarget] = useState<Message | null>(null);
  const [newMessage, setNewMessage] = useState('');
  const [loadingList, setLoadingList] = useState(true);
  const [loadingMessages, setLoadingMessages] = useState(false);
  const [hasMoreOlder, setHasMoreOlder] = useState(false);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const loadingOlderRef = useRef(false);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState('');
  const [socketStatus, setSocketStatus] = useState('disconnected');
  const [typingUserId, setTypingUserId] = useState<string | null>(null);
  const [peerOnline, setPeerOnline] = useState(false);
  const [galleryOpen, setGalleryOpen] = useState(false);
  const [prefsTarget, setPrefsTarget] = useState<Conversation | null>(null);
  const [showEmojiPicker, setShowEmojiPicker] = useState(false);
  const [showEmotionBar, setShowEmotionBar] = useState(false);
  const [recording, setRecording] = useState(false);
  const voiceRef = useRef<VoiceRecorder | null>(null);
  const recordingLiveRef = useRef(false);
  const typingTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const lastTypingSentRef = useRef(false);

  useEffect(() => {
    if (!auth?.accessToken) {
      router.replace('/login');
    }
  }, [auth?.accessToken, router]);

  const resolveTitles = useCallback(async (list: Conversation[]) => {
    const next: Record<string, string> = {};
    const lookupIds: string[] = [];
    const groupIds: string[] = [];

    for (const conversation of list) {
      if (conversation.type === 'DIRECT') {
        const peer = conversation.memberIds.find((id) => id !== currentUserId);
        if (peer) lookupIds.push(peer);
      } else {
        groupIds.push(conversation.conversationId);
        conversation.memberIds.forEach((id) => {
          if (id !== currentUserId) lookupIds.push(id);
        });
      }
    }

    const byId: Record<string, string> = {};
    if (lookupIds.length > 0) {
      try {
        const users = await lookupUsersApi(lookupIds);
        users.forEach((user) => {
          byId[user.userId] = user.displayName || user.username;
        });
        setUsersById((previous) => ({ ...previous, ...byId }));
      } catch {
        // keep fallback titles
      }
    }

    for (const conversation of list) {
      if (conversation.type !== 'DIRECT') continue;
      const peer = conversation.memberIds.find((id) => id !== currentUserId);
      if (peer && byId[peer]) {
        next[conversation.conversationId] = byId[peer];
      }
    }

    if (groupIds.length > 0) {
      try {
        const groups = await getGroupsApi();
        const groupNameById = Object.fromEntries(
          groups.map((group) => [group.groupId, group.name?.trim() || '']),
        );
        for (const conversation of list) {
          if (conversation.type !== 'GROUP') continue;
          const named = groupNameById[conversation.conversationId];
          if (named) {
            next[conversation.conversationId] = named;
            continue;
          }
          const names = conversation.memberIds
            .filter((id) => id !== currentUserId)
            .map((id) => byId[id])
            .filter(Boolean);
          if (names.length > 0) {
            next[conversation.conversationId] =
              names.length <= 3
                ? names.join(', ')
                : `${names.slice(0, 3).join(', ')}…`;
          }
        }
      } catch {
        for (const conversation of list) {
          if (conversation.type !== 'GROUP') continue;
          const names = conversation.memberIds
            .filter((id) => id !== currentUserId)
            .map((id) => byId[id])
            .filter(Boolean);
          if (names.length > 0) {
            next[conversation.conversationId] =
              names.length <= 3
                ? names.join(', ')
                : `${names.slice(0, 3).join(', ')}…`;
          }
        }
      }
    }

    setTitles((previous) => ({ ...previous, ...next }));
  }, [currentUserId]);

  const loadConversations = useCallback(async () => {
    try {
      setLoadingList(true);
      setError('');
      const data = await getConversationsApi();
      setConversations(data.conversations);
      await resolveTitles(data.conversations);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load conversations');
    } finally {
      setLoadingList(false);
    }
  }, [resolveTitles]);

  useEffect(() => {
    if (auth?.accessToken) {
      void loadConversations();
    }
  }, [auth?.accessToken, loadConversations]);

  const openConversation = async (conversation: Conversation) => {
    setSelectedConversation(conversation);
    setLoadingMessages(true);
    setError('');
    try {
      const page = await getMessagesApi(conversation.conversationId, {
        limit: HISTORY_PAGE_SIZE,
      });
      setMessages(page.messages);
      setHasMoreOlder(page.hasMore);
      setReplyTarget(null);
      setReactionsByMessage({});
      // Reaction không nằm trong trang tin; endpoint chưa có thì listReactionsApi trả [].
      const recent = page.messages
        .filter((message) => message.messageId && !message.deleted && message.type !== 'EMOTION')
        .slice(-30);
      void Promise.all(
        recent.map((message) =>
          listReactionsApi(conversation.conversationId, message.messageId as string)
            .then((rows) => [message.messageId as string, rows] as const)
            .catch(() => null),
        ),
      ).then((entries) => {
        setReactionsByMessage((previous) => {
          const next = { ...previous };
          entries.forEach((entry) => {
            if (entry && entry[1].length > 0) next[entry[0]] = entry[1];
          });
          return next;
        });
      });
      const maxSeq = page.messages.reduce(
        (max, message) =>
          typeof message.seq === 'number' ? Math.max(max, message.seq) : max,
        0,
      );
      if (maxSeq > 0) {
        void markConversationReadApi(conversation.conversationId, maxSeq).catch(() => undefined);
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load messages');
      setMessages([]);
      setHasMoreOlder(false);
    } finally {
      setLoadingMessages(false);
    }
  };

  const handleLoadOlderMessages = async (): Promise<boolean> => {
    const conversation = selectedConversation;
    if (!conversation || loadingOlderRef.current || !hasMoreOlder) {
      return false;
    }

    const oldestSeq = messages.reduce<number | null>((min, message) => {
      if (typeof message.seq !== 'number') return min;
      return min === null ? message.seq : Math.min(min, message.seq);
    }, null);
    if (oldestSeq === null) return false;

    loadingOlderRef.current = true;
    setLoadingOlder(true);
    try {
      const page = await getMessagesApi(conversation.conversationId, {
        beforeSeq: oldestSeq,
        limit: HISTORY_PAGE_SIZE,
      });
      if (page.messages.length === 0) {
        setHasMoreOlder(false);
        return false;
      }

      const seen = new Set(messages.map((message) => message.messageId ?? message.id));
      const older = page.messages.filter((message) => {
        const key = message.messageId ?? message.id;
        if (seen.has(key)) return false;
        seen.add(key);
        return true;
      });
      if (older.length === 0) {
        setHasMoreOlder(page.hasMore);
        return false;
      }

      setMessages((previous) => [...older, ...previous]);
      setHasMoreOlder(page.hasMore);
      return true;
    } catch {
      return false;
    } finally {
      loadingOlderRef.current = false;
      setLoadingOlder(false);
    }
  };

  useEffect(() => {
    if (!params.conversationId || conversations.length === 0) return;
    const existing = conversations.find((item) => item.conversationId === params.conversationId);
    const target: Conversation =
      existing ??
      ({
        conversationId: params.conversationId,
        type: params.conversationType === 'GROUP' ? 'GROUP' : 'DIRECT',
        memberIds: [],
        lastSeq: 0,
        createdAt: new Date().toISOString(),
      } as Conversation);
    if (params.title) {
      setTitles((previous) => ({ ...previous, [params.conversationId!]: String(params.title) }));
    }
    void openConversation(target);
  }, [params.conversationId, params.conversationType, params.title, conversations]);

  const upsertMessage = useCallback((incoming: Message) => {
    setMessages((previous) => {
      const index = previous.findIndex(
        (message) =>
          (incoming.messageId && message.messageId === incoming.messageId) ||
          (incoming.clientMsgId && message.clientMsgId === incoming.clientMsgId) ||
          message.id === incoming.id,
      );
      if (index >= 0) {
        const next = previous.slice();
        next[index] = { ...previous[index], ...incoming };
        return next;
      }
      return [...previous, incoming];
    });
  }, []);

  useEffect(() => {
    const mergeIncoming = (incoming: Message) => {
      upsertMessage(incoming);
    };

    const unsubscribeEvent = socketClient.subscribe((event) => {
      if (event.type === 'messaging.read.updated') {
        const payload = event.payload as {
          conversationId?: string;
          lastReadSeq?: number;
          unreadCount?: number;
        };
        if (!payload.conversationId) return;
        setConversations((previous) =>
          previous.map((conversation) =>
            conversation.conversationId === payload.conversationId
              ? {
                  ...conversation,
                  lastReadSeq: payload.lastReadSeq ?? conversation.lastReadSeq,
                  unreadCount: payload.unreadCount ?? conversation.unreadCount,
                }
              : conversation,
          ),
        );
        return;
      }
      if (event.type === 'messaging.typing') {
        const payload = event.payload as {
          conversationId?: string;
          userId?: string;
          typing?: boolean;
        };
        if (
          payload.conversationId !== selectedRef.current?.conversationId ||
          payload.userId === currentUserId
        ) {
          return;
        }
        setTypingUserId(payload.typing ? payload.userId || null : null);
        return;
      }
      // messaging.presence = đang mở hội thoại (ephemeral). Header Online/Offline
      // lấy từ GET /presence — xem effect refreshPeerPresence bên dưới.
      if (event.type === 'messaging.message.deleted') {
        const payload = event.payload as { messageId?: string };
        if (!payload.messageId) return;
        setMessages((previous) =>
          previous.map((message) =>
            message.messageId === payload.messageId
              ? { ...message, content: '', deleted: true }
              : message,
          ),
        );
        return;
      }
      if (event.type === 'messaging.reaction.added' || event.type === 'messaging.reaction.removed') {
        const payload = event.payload as {
          messageId?: string;
          userId?: string;
          code?: ReactionCode;
        };
        if (!payload.messageId || !payload.userId || !payload.code) return;
        const { messageId, userId, code } = payload;
        setReactionsByMessage((previous) => {
          const without = (previous[messageId] ?? []).filter(
            (row) => !(row.userId === userId && row.code === code),
          );
          return {
            ...previous,
            [messageId]:
              event.type === 'messaging.reaction.removed'
                ? without
                : [...without, { userId, code, createdAt: event.occurredAt }],
          };
        });
        return;
      }
      if (event.type !== 'messaging.message.created') return;
      const payload = event.payload as {
        messageId?: string;
        conversationId?: string;
        seq?: number;
        senderId?: string;
        type?: Message['type'];
        body?: string;
        clientMsgId?: string;
        createdAt?: string;
        replyTo?: string;
        forwardedFrom?: string;
      };
      if (!payload.messageId || !payload.conversationId || !payload.clientMsgId) return;
      const createdAt = Date.parse(payload.createdAt ?? '');
      mergeIncoming({
        id: payload.messageId,
        messageId: payload.messageId,
        conversationId: payload.conversationId,
        senderId: payload.senderId ?? '',
        type: payload.type ?? 'TEXT',
        content: payload.body ?? '',
        seq: payload.seq,
        clientMsgId: payload.clientMsgId,
        sentAt: Number.isFinite(createdAt)
          ? Math.floor(createdAt / 1000)
          : Math.floor(Date.now() / 1000),
        status: 'sent',
        replyTo: payload.replyTo,
        forwardedFrom: payload.forwardedFrom,
      });
      if (
        selectedRef.current?.conversationId === payload.conversationId &&
        typeof payload.seq === 'number'
      ) {
        void markConversationReadApi(payload.conversationId, payload.seq).catch(() => undefined);
      }
    });

    const unsubscribeStatus = socketClient.subscribeStatus((status) => {
      setSocketStatus(status);
    });

    const unsubscribeReconnect = socketClient.subscribeReconnect(() => {
      const active = selectedRef.current;
      if (!active) return;
      const afterSeq = messagesRef.current.reduce(
        (max, message) =>
          message.conversationId === active.conversationId && typeof message.seq === 'number'
            ? Math.max(max, message.seq)
            : max,
        0,
      );
      void getMessagesApi(active.conversationId, { afterSeq }).then((page) => {
        page.messages.forEach(mergeIncoming);
      });
    });

    socketClient.connect();
    return () => {
      unsubscribeEvent();
      unsubscribeStatus();
      unsubscribeReconnect();
      socketClient.disconnect();
    };
  }, [currentUserId, upsertMessage]);

  const selectedConversationId = selectedConversation?.conversationId;
  const selectedConversationType = selectedConversation?.type;
  const selectedMemberIdsKey = selectedConversation?.memberIds.join(',') ?? '';

  useEffect(() => {
    lastTypingSentRef.current = false;
    setTypingUserId(null);
    if (!selectedConversationId) return;

    const peerId =
      selectedConversationType === 'DIRECT'
        ? selectedMemberIdsKey
            .split(',')
            .filter(Boolean)
            .find((userId) => userId !== currentUserId)
        : undefined;

    let cancelled = false;
    let pollTimer: ReturnType<typeof setInterval> | undefined;

    const refreshPeerPresence = () => {
      if (!peerId) return;
      void getPresenceApi([peerId])
        .then((result) => {
          if (cancelled) return;
          if (selectedRef.current?.conversationId !== selectedConversationId) return;
          setPeerOnline(result[peerId]?.online === true);
        })
        .catch(() => undefined);
    };

    if (!peerId) {
      setPeerOnline(false);
    } else {
      refreshPeerPresence();
      pollTimer = setInterval(refreshPeerPresence, PRESENCE_POLL_MS);
    }

    socketClient.sendPresence(selectedConversationId, 'online');
    return () => {
      cancelled = true;
      if (pollTimer !== undefined) clearInterval(pollTimer);
      socketClient.sendPresence(selectedConversationId, 'offline');
      socketClient.sendTyping(selectedConversationId, false);
    };
  }, [
    selectedConversationId,
    selectedConversationType,
    selectedMemberIdsKey,
    currentUserId,
  ]);

  useEffect(() => {
    return () => {
      void voiceRef.current?.cancel();
      voiceRef.current = null;
    };
  }, []);

  const handleSend = async () => {
    if (!selectedConversation || !newMessage.trim() || sending) return;
    const body = newMessage.trim();
    lastTypingSentRef.current = false;
    socketClient.sendTyping(selectedConversation.conversationId, false);
    setSending(true);
    setError('');
    try {
      const replyToId = replyTarget?.messageId;
      const sent = await sendMessageApi(selectedConversation.conversationId, {
        type: 'TEXT',
        body,
        clientMsgId: uuid(),
        ...(replyToId ? { replyTo: replyToId } : {}),
      });
      upsertMessage(sent);
      setNewMessage('');
      setReplyTarget(null);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to send message');
    } finally {
      setSending(false);
    }
  };

  const senderNameOf = (userId: string) => usersById[userId] ?? 'User';

  const handleRecall = (message: Message) => {
    if (!message.messageId || message.senderId !== currentUserId) return;
    confirmAction('Recall message', 'Recall this message for everyone?', () => {
      void (async () => {
        setError('');
        try {
          await deleteMessageApi(message.conversationId, message.messageId as string);
          setMessages((previous) =>
            previous.map((item) =>
              item.messageId === message.messageId ? { ...item, content: '', deleted: true } : item,
            ),
          );
        } catch (err: unknown) {
          setError(err instanceof Error ? err.message : 'Cannot recall message');
        }
      })();
    }, 'Recall');
  };

  const handleToggleReaction = async (message: Message, code: ReactionCode) => {
    const messageId = message.messageId;
    if (!messageId) return;
    const mine = (reactionsByMessage[messageId] ?? []).some(
      (row) => row.userId === currentUserId && row.code === code,
    );
    const apply = (adding: boolean) =>
      setReactionsByMessage((previous) => {
        const without = (previous[messageId] ?? []).filter(
          (row) => !(row.userId === currentUserId && row.code === code),
        );
        return {
          ...previous,
          [messageId]: adding
            ? [...without, { userId: currentUserId, code, createdAt: new Date().toISOString() }]
            : without,
        };
      });
    setError('');
    apply(!mine);
    try {
      if (mine) {
        await removeReactionApi(message.conversationId, messageId, code);
      } else {
        await addReactionApi(message.conversationId, messageId, code);
      }
    } catch (err: unknown) {
      apply(mine);
      setError(err instanceof Error ? err.message : 'Cannot update reaction');
    }
  };

  const handleForwardTo = async (target: Conversation) => {
    const source = forwardTarget;
    if (!source?.messageId) return;
    setForwardTarget(null);
    setError('');
    try {
      const saved = await forwardMessageApi(target.conversationId, {
        messageId: source.messageId,
        clientMsgId: uuid(),
      });
      if (target.conversationId === selectedRef.current?.conversationId) {
        upsertMessage(saved);
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Cannot forward message');
    }
  };

  const handleEmotion = async (code: EmotionCode) => {
    if (!selectedConversation || sending) return;
    setSending(true);
    try {
      const sent = await sendMessageApi(selectedConversation.conversationId, {
        type: 'EMOTION',
        body: code,
        clientMsgId: uuid(),
      });
      upsertMessage(sent);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to send emotion');
    } finally {
      setSending(false);
    }
  };

  const sendUploaded = async (
    type: MediaUploadType,
    uri: string,
    fileName: string,
    mimeType: string,
    durationSec?: number,
    sizeBytes?: number | null,
  ) => {
    if (!selectedConversation) return;
    setSending(true);
    setError('');
    try {
      const contentType =
        mimeType && mimeType !== 'application/octet-stream'
          ? mimeType
          : guessMimeType(fileName, type);
      assertMediaWithinLimit(type, sizeBytes);
      // Native multipart { uri, name, type } — Blob upload often strips MIME on device.
      const uploaded = await uploadMediaApi(
        { uri, name: fileName, type: contentType },
        type,
        {
          fileName,
          contentType,
          size: sizeBytes ?? undefined,
          conversationId: selectedConversation.conversationId,
        },
      );
      const body = mediaMessageBody(uploaded.url);
      const clientMsgId = uuid();
      const conversationId = selectedConversation.conversationId;
      let sent: Message;
      if (type === 'DOCUMENT') {
        sent = await sendMessageApi(conversationId, {
          type: 'DOCUMENT',
          body,
          clientMsgId,
          fileName: uploaded.fileName,
          mimeType: uploaded.contentType || contentType,
          size: uploaded.size,
        });
      } else if (type === 'AUDIO') {
        sent = await sendMessageApi(conversationId, {
          type: 'AUDIO',
          body,
          clientMsgId,
          fileName: uploaded.fileName,
          mimeType: uploaded.contentType || contentType,
          size: uploaded.size,
          ...(durationSec ? { duration: durationSec } : {}),
        });
      } else {
        sent = await sendMessageApi(conversationId, { type, body, clientMsgId });
      }
      upsertMessage(sent);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to upload media');
    } finally {
      setSending(false);
    }
  };

  const pickImage = async (asVideo: boolean) => {
    try {
      const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
      if (!permission.granted) {
        setError('Media library permission is required');
        return;
      }
      const result = await ImagePicker.launchImageLibraryAsync({
        mediaTypes: asVideo ? ['videos'] : ['images'],
        quality: 0.8,
        videoMaxDuration: 120,
        // Prefer a filesystem copy Expo can multipart-upload (avoids ph:// / incomplete assets).
        preferredAssetRepresentationMode:
          ImagePicker.UIImagePickerPreferredAssetRepresentationMode.Compatible,
        // iOS: compress gallery videos toward the 20MB server limit.
        ...(asVideo && Platform.OS === 'ios'
          ? {
              videoQuality: ImagePicker.UIImagePickerControllerQualityType.Medium,
              videoExportPreset: ImagePicker.VideoExportPreset.H264_640x480,
            }
          : {}),
      });
      if (result.canceled || !result.assets[0]) return;
      const asset = result.assets[0];
      const type: MediaUploadType = asVideo ? 'VIDEO' : 'IMAGE';
      const uriExt = asset.uri.split('?')[0].split('.').pop()?.toLowerCase();
      const safeExt =
        uriExt && uriExt.length <= 5 && /^[a-z0-9]+$/.test(uriExt)
          ? uriExt
          : asVideo
            ? 'mp4'
            : 'jpg';
      const fileName =
        asset.fileName ??
        (asVideo ? `video-${Date.now()}.${safeExt}` : `image-${Date.now()}.jpg`);
      const mimeType =
        asset.mimeType && asset.mimeType !== 'application/octet-stream'
          ? asset.mimeType
          : guessMimeType(fileName, type);
      await sendUploaded(type, asset.uri, fileName, mimeType, undefined, asset.fileSize);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to pick media');
    }
  };

  const pickDocument = async () => {
    const result = await DocumentPicker.getDocumentAsync({ copyToCacheDirectory: true });
    if (result.canceled || !result.assets[0]) return;
    const asset = result.assets[0];
    await sendUploaded(
      'DOCUMENT',
      asset.uri,
      asset.name,
      asset.mimeType ?? guessMimeType(asset.name, 'DOCUMENT'),
    );
  };

  /** Tap mic once to record; tap again to stop & send (hold-to-record fights the permission sheet). */
  const toggleVoice = async () => {
    if (!selectedConversation || sending) return;

    if (recordingLiveRef.current && voiceRef.current) {
      const recorder = voiceRef.current;
      voiceRef.current = null;
      recordingLiveRef.current = false;
      setRecording(false);
      try {
        const clip = await recorder.stop();
        await sendUploaded('AUDIO', clip.uri, clip.fileName, clip.mimeType, clip.durationSec);
      } catch (err: unknown) {
        setError(err instanceof Error ? err.message : 'Cannot send voice message');
      }
      return;
    }

    if (voiceRef.current) return;
    setError('');
    const recorder = new VoiceRecorder();
    voiceRef.current = recorder;
    try {
      await recorder.start();
      if (voiceRef.current !== recorder) {
        void recorder.cancel();
        return;
      }
      recordingLiveRef.current = true;
      setRecording(true);
    } catch (err: unknown) {
      if (voiceRef.current === recorder) {
        voiceRef.current = null;
      }
      recordingLiveRef.current = false;
      setRecording(false);
      setError(
        err instanceof VoiceRecorderError && err.code === 'permission-denied'
          ? 'Microphone permission denied. Enable it in Settings to send voice messages.'
          : err instanceof Error
            ? err.message
            : 'Cannot start recording',
      );
    }
  };

  const applyPrefs = async (conversation: Conversation, patch: ConversationPrefsPatch) => {
    setPrefsTarget(null);
    setError('');
    try {
      const ok = await updateConversationPrefsApi(conversation.conversationId, patch);
      if (!ok) {
        setError('Server does not support conversation preferences yet');
        return;
      }
      setConversations((previous) =>
        applyConversationPrefs(previous, conversation.conversationId, patch),
      );
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Cannot update conversation');
    }
  };

  const titleOf = useMemo(
    () => (conversation: Conversation) =>
      titles[conversation.conversationId] ||
      (conversation.type === 'GROUP' ? 'Group chat' : 'Direct chat'),
    [titles],
  );

  const prefsSheet = (
    <Modal
      visible={prefsTarget !== null}
      transparent
      animationType="fade"
      onRequestClose={() => setPrefsTarget(null)}
    >
      <Pressable style={styles.modalBackdrop} onPress={() => setPrefsTarget(null)}>
        <Pressable
          style={[styles.sheet, { backgroundColor: theme.background, borderColor: theme.border }]}
          onPress={() => undefined}
        >
          <Text style={[styles.sheetTitle, { color: theme.text }]} numberOfLines={1}>
            {prefsTarget ? titleOf(prefsTarget) : ''}
          </Text>
          {prefsTarget ? (
            <>
              <Pressable
                style={styles.sheetItem}
                onPress={() => void applyPrefs(prefsTarget, { pinned: !prefsTarget.pinned })}
              >
                <Text style={[styles.sheetText, { color: theme.text }]}>
                  {prefsTarget.pinned ? 'Unpin' : 'Pin'}
                </Text>
              </Pressable>
              <Pressable
                style={styles.sheetItem}
                onPress={() => void applyPrefs(prefsTarget, { muted: !prefsTarget.muted })}
              >
                <Text style={[styles.sheetText, { color: theme.text }]}>
                  {prefsTarget.muted ? 'Unmute' : 'Mute'}
                </Text>
              </Pressable>
              <Pressable
                style={styles.sheetItem}
                onPress={() => void applyPrefs(prefsTarget, { archived: true })}
              >
                <Text style={[styles.sheetText, { color: theme.text }]}>Archive</Text>
              </Pressable>
            </>
          ) : null}
        </Pressable>
      </Pressable>
    </Modal>
  );

  if (!selectedConversation) {
    return (
      <SafeAreaView style={[styles.container, { backgroundColor: theme.background }]}>
        <View style={[styles.sidebarHeader, { borderBottomColor: theme.border }]}>
          <Text style={[styles.sidebarTitle, { color: theme.text }]}>Conversations</Text>
          <Text style={[styles.socket, { color: theme.textMuted }]}>{socketStatus}</Text>
          <Pressable onPress={() => void loadConversations()} hitSlop={8}>
            <Ionicons name="refresh" size={20} color={theme.primary} />
          </Pressable>
        </View>
        {error ? <Text style={[styles.error, { color: theme.danger }]}>{error}</Text> : null}
        {loadingList ? (
          <ActivityIndicator style={{ marginTop: 24 }} color={theme.primary} />
        ) : (
          <FlatList
            data={conversations}
            keyExtractor={(item) => item.conversationId}
            ListEmptyComponent={
              <Text style={[styles.empty, { color: theme.textSecondary }]}>
                No conversations yet. Start one from Contacts.
              </Text>
            }
            renderItem={({ item }) => (
              <ConversationItem
                conversation={item}
                title={titleOf(item)}
                currentUserId={currentUserId}
                active={false}
                onClick={() => void openConversation(item)}
                onLongPress={() => setPrefsTarget(item)}
              />
            )}
          />
        )}
        {prefsSheet}
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: theme.background }]}>
      <ChatHeader
        title={titleOf(selectedConversation)}
        subtitle={
          typingUserId
            ? 'Đang nhập…'
            : selectedConversation.type === 'DIRECT'
              ? peerOnline
                ? 'Online'
                : 'Offline'
              : undefined
        }
        onMediaPress={() => setGalleryOpen(true)}
        onBack={() => {
          setSelectedConversation(null);
          setMessages([]);
          setHasMoreOlder(false);
          setShowEmojiPicker(false);
          setShowEmotionBar(false);
          void loadConversations();
        }}
      />
      <Text style={[styles.socketBar, { color: theme.textMuted }]}>WS: {socketStatus}</Text>
      {error ? <Text style={[styles.error, { color: theme.danger }]}>{error}</Text> : null}
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : 'height'} style={styles.chatArea}>
        {loadingMessages ? (
          <ActivityIndicator style={{ marginTop: 24 }} color={theme.primary} />
        ) : (
          <ChatMessages
            messages={messages}
            currentUserId={currentUserId}
            conversationId={selectedConversation.conversationId}
            hasMoreOlder={hasMoreOlder}
            loadingOlder={loadingOlder}
            onLoadOlder={handleLoadOlderMessages}
            reactions={reactionsByMessage}
            getSenderName={senderNameOf}
            onLongPress={setActionTarget}
            onToggleReaction={(message, code) => void handleToggleReaction(message, code)}
          />
        )}
        {replyTarget ? (
          <View style={[styles.replyBanner, { borderLeftColor: theme.primary, backgroundColor: theme.backgroundElement }]}>
            <View style={{ flex: 1 }}>
              <Text style={[styles.replyBannerTitle, { color: theme.text }]} numberOfLines={1}>
                Replying to {replyTarget.senderId === currentUserId ? 'yourself' : senderNameOf(replyTarget.senderId)}
              </Text>
              <Text style={{ color: theme.textSecondary, fontSize: 12 }} numberOfLines={1}>
                {replyTarget.type === 'TEXT' ? replyTarget.content : `[${replyTarget.type.toLowerCase()}]`}
              </Text>
            </View>
            <Pressable onPress={() => setReplyTarget(null)} hitSlop={8} accessibilityLabel="Cancel reply">
              <Ionicons name="close" size={20} color={theme.textSecondary} />
            </Pressable>
          </View>
        ) : null}
        <View style={[styles.composer, { borderTopColor: theme.border, backgroundColor: theme.background }]}>
          <MesToolBar
            disabled={sending}
            emojiOpen={showEmojiPicker}
            emotionOpen={showEmotionBar}
            recording={recording}
            onEmojiPress={() => {
              setShowEmojiPicker((previous) => !previous);
              setShowEmotionBar(false);
            }}
            onEmotionPress={() => {
              setShowEmotionBar((previous) => !previous);
              setShowEmojiPicker(false);
            }}
            onImagePress={() => void pickImage(false)}
            onFilePress={() => void pickDocument()}
            onVideoPress={() => void pickImage(true)}
            onVoiceToggle={() => void toggleVoice()}
          />

          {recording ? (
            <Text style={[styles.recordingHint, { color: theme.danger }]} accessibilityLiveRegion="polite">
              Recording… tap mic again to send
            </Text>
          ) : null}

          {showEmojiPicker ? (
            <View
              style={[styles.emojiPanel, { borderColor: theme.border, backgroundColor: theme.backgroundElement }]}
              accessibilityLabel="Emoji picker"
            >
              {(
                [
                  '😀', '😂', '🥹', '😍', '😎', '🤔', '👍', '👎',
                  '❤️', '🔥', '🎉', '👏', '🙏', '💯', '✨', '😢',
                  '😮', '😡', '🤝', '👋', '💪', '⭐', '✅', '❌',
                ] as const
              ).map((emoji) => (
                <Pressable
                  key={emoji}
                  style={styles.emojiCell}
                  onPress={() => {
                    setNewMessage((previous) => previous + emoji);
                    setShowEmojiPicker(false);
                  }}
                  accessibilityLabel={`Insert ${emoji}`}
                >
                  <Text style={styles.emojiGlyph}>{emoji}</Text>
                </Pressable>
              ))}
            </View>
          ) : null}

          {showEmotionBar ? (
            <View style={styles.emotionRow}>
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
                <Pressable
                  key={code}
                  style={[styles.emotionChip, { borderColor: theme.border, backgroundColor: theme.backgroundElement }]}
                  onPress={() => {
                    setShowEmotionBar(false);
                    void handleEmotion(code);
                  }}
                  disabled={sending}
                  accessibilityLabel={`Send ${code}`}
                >
                  <Text style={styles.emotionEmoji}>{emoji}</Text>
                </Pressable>
              ))}
            </View>
          ) : null}

          <View style={styles.inputContainer}>
            <TextInput
              style={[
                styles.input,
                {
                  borderColor: theme.inputBorder,
                  backgroundColor: theme.inputBg,
                  color: theme.text,
                },
              ]}
              placeholder="Type a message..."
              placeholderTextColor={theme.textMuted}
              value={newMessage}
              onChangeText={(value) => {
                setNewMessage(value);
                const conversationId = selectedConversation.conversationId;
                if (value.trim() && !lastTypingSentRef.current) {
                  lastTypingSentRef.current = true;
                  socketClient.sendTyping(conversationId, true);
                }
                if (typingTimerRef.current) {
                  clearTimeout(typingTimerRef.current);
                }
                typingTimerRef.current = setTimeout(() => {
                  lastTypingSentRef.current = false;
                  socketClient.sendTyping(conversationId, false);
                }, 1500);
              }}
            />
            <Pressable
              style={[
                styles.sendButton,
                { backgroundColor: theme.primary },
                (!newMessage.trim() || sending) && styles.sendDisabled,
              ]}
              onPress={() => void handleSend()}
              disabled={!newMessage.trim() || sending}
            >
              <Ionicons name="send" size={20} color="#fff" />
            </Pressable>
          </View>
        </View>
      </KeyboardAvoidingView>

      <Modal
        visible={actionTarget !== null}
        transparent
        animationType="fade"
        onRequestClose={() => setActionTarget(null)}
      >
        <Pressable style={styles.menuBackdrop} onPress={() => setActionTarget(null)}>
          <Pressable
            style={[
              styles.messageMenu,
              {
                backgroundColor: theme.background,
                borderColor: theme.border,
                shadowColor: '#000',
              },
            ]}
            onPress={() => undefined}
            accessibilityRole="menu"
          >
            <View style={[styles.menuReactions, { borderBottomColor: theme.border }]}>
              {(['like', 'love', 'haha', 'wow', 'sad', 'angry'] as ReactionCode[]).map((code) => (
                <Pressable
                  key={code}
                  style={[styles.menuReactionChip, { backgroundColor: theme.backgroundElement }]}
                  onPress={() => {
                    const target = actionTarget;
                    setActionTarget(null);
                    if (target) void handleToggleReaction(target, code);
                  }}
                  accessibilityLabel={`React ${code}`}
                  accessibilityRole="menuitem"
                >
                  <Text style={{ fontSize: 18 }}>
                    {{ like: '👍', love: '❤️', haha: '😄', wow: '😮', sad: '😢', angry: '😠' }[code]}
                  </Text>
                </Pressable>
              ))}
            </View>
            <Pressable
              style={styles.menuItem}
              onPress={() => {
                setReplyTarget(actionTarget);
                setActionTarget(null);
              }}
              accessibilityRole="menuitem"
            >
              <Ionicons name="arrow-undo-outline" size={18} color={theme.text} />
              <Text style={[styles.menuItemText, { color: theme.text }]}>Reply</Text>
            </Pressable>
            <Pressable
              style={styles.menuItem}
              onPress={() => {
                setForwardTarget(actionTarget);
                setActionTarget(null);
              }}
              accessibilityRole="menuitem"
            >
              <Ionicons name="arrow-redo-outline" size={18} color={theme.text} />
              <Text style={[styles.menuItemText, { color: theme.text }]}>Forward</Text>
            </Pressable>
            {actionTarget?.senderId === currentUserId ? (
              <Pressable
                style={styles.menuItem}
                onPress={() => {
                  const target = actionTarget;
                  setActionTarget(null);
                  if (target) handleRecall(target);
                }}
                accessibilityRole="menuitem"
              >
                <Ionicons name="refresh-outline" size={18} color={theme.danger} />
                <Text style={[styles.menuItemText, { color: theme.danger }]}>Recall</Text>
              </Pressable>
            ) : null}
          </Pressable>
        </Pressable>
      </Modal>

      <MediaGalleryModal
        visible={galleryOpen}
        messages={messages}
        onClose={() => setGalleryOpen(false)}
      />

      <Modal
        visible={forwardTarget !== null}
        transparent
        animationType="slide"
        onRequestClose={() => setForwardTarget(null)}
      >
        <Pressable style={styles.modalBackdrop} onPress={() => setForwardTarget(null)}>
          <Pressable
            style={[styles.sheet, { backgroundColor: theme.background, borderColor: theme.border, maxHeight: '70%' }]}
            onPress={() => undefined}
          >
            <Text style={[styles.sheetTitle, { color: theme.text }]}>Forward to…</Text>
            <FlatList
              data={conversations}
              keyExtractor={(item) => item.conversationId}
              ListEmptyComponent={
                <Text style={{ color: theme.textSecondary, padding: 12 }}>No conversations.</Text>
              }
              renderItem={({ item }) => (
                <ConversationItem
                  conversation={item}
                  title={titleOf(item)}
                  currentUserId={currentUserId}
                  onClick={() => void handleForwardTo(item)}
                />
              )}
            />
          </Pressable>
        </Pressable>
      </Modal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  sidebarHeader: {
    padding: Spacing.three,
    borderBottomWidth: 1,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: 8,
  },
  sidebarTitle: { fontSize: 20, fontWeight: '700', letterSpacing: -0.3, flex: 1 },
  socket: { fontSize: 12 },
  socketBar: { fontSize: 12, paddingHorizontal: Spacing.three, paddingTop: 4 },
  chatArea: { flex: 1 },
  composer: {
    borderTopWidth: 1,
  },
  recordingHint: {
    fontSize: 12,
    paddingHorizontal: 12,
    paddingTop: 4,
  },
  emojiPanel: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    marginHorizontal: 12,
    marginTop: 6,
    padding: 8,
    borderWidth: 1,
    borderRadius: Radius.lg,
    gap: 2,
  },
  emojiCell: {
    width: 40,
    height: 40,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emojiGlyph: { fontSize: 22 },
  emotionRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 6,
    paddingHorizontal: 12,
    paddingTop: 6,
  },
  emotionChip: {
    borderWidth: 1,
    borderRadius: Radius.full,
    paddingHorizontal: 10,
    paddingVertical: 6,
  },
  emotionEmoji: { fontSize: 20 },
  replyBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginHorizontal: 12,
    marginTop: 8,
    padding: 8,
    borderLeftWidth: 3,
    borderRadius: 6,
  },
  replyBannerTitle: { fontSize: 13, fontWeight: '600' },
  modalBackdrop: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.4)',
    justifyContent: 'flex-end',
  },
  menuBackdrop: {
    flex: 1,
    backgroundColor: 'rgba(15, 23, 42, 0.28)',
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 24,
  },
  messageMenu: {
    width: '100%',
    maxWidth: 280,
    borderWidth: 1,
    borderRadius: 12,
    padding: 6,
    shadowOffset: { width: 0, height: 8 },
    shadowOpacity: 0.12,
    shadowRadius: 24,
    elevation: 8,
  },
  menuReactions: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 4,
    paddingBottom: 6,
    marginBottom: 4,
    borderBottomWidth: 1,
  },
  menuReactionChip: {
    width: 36,
    height: 36,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
  },
  menuItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingVertical: 10,
    paddingHorizontal: 10,
    borderRadius: 8,
  },
  menuItemText: { fontSize: 14, fontWeight: '500' },
  sheet: {
    borderTopLeftRadius: Radius.xl,
    borderTopRightRadius: Radius.xl,
    borderWidth: 1,
    padding: Spacing.three,
  },
  sheetTitle: { fontSize: 16, fontWeight: '700', marginBottom: 8 },
  sheetItem: { paddingVertical: 14 },
  sheetText: { fontSize: 16 },
  inputContainer: {
    flexDirection: 'row',
    padding: 12,
    alignItems: 'center',
  },
  input: {
    flex: 1,
    borderWidth: 1,
    borderRadius: 20,
    paddingHorizontal: 14,
    paddingVertical: 8,
    marginRight: 8,
  },
  sendButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
  },
  sendDisabled: { opacity: 0.5 },
  error: { paddingHorizontal: Spacing.three, paddingTop: 8 },
  empty: {
    textAlign: 'center',
    marginTop: 24,
    paddingHorizontal: 24,
    lineHeight: 20,
  },
});
