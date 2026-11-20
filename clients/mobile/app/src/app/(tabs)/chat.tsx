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
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useLocalSearchParams, useRouter } from 'expo-router';
import ChatHeader from '../../components/chat/ChatHeader';
import ChatMessages from '../../components/chat/ChatMessages';
import ConversationItem from '../../components/chat/ConversationItem';
import { getAuth } from '@core/authSession';
import { getConversationsApi } from '@core/conversationService';
import {
  getMessagesApi,
  sendMessageApi,
  markConversationReadApi,
  type EmotionCode,
} from '@core/messageService';
import { uploadMediaApi, type MediaUploadType } from '@core/mediaService';
import { lookupUsersApi } from '@core/userService';
import { getGroupsApi } from '@core/groupService';
import { socketClient } from '@core/socketClient';
import type { Conversation } from '@core/types/conversation';
import type { Message } from '@core/types/message';
import * as ImagePicker from 'expo-image-picker';
import * as DocumentPicker from 'expo-document-picker';
import { useTheme } from '@/hooks/use-theme';
import { Radius, Spacing } from '@/constants/theme';

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
  const [newMessage, setNewMessage] = useState('');
  const [loadingList, setLoadingList] = useState(true);
  const [loadingMessages, setLoadingMessages] = useState(false);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState('');
  const [socketStatus, setSocketStatus] = useState('disconnected');
  const [typingUserId, setTypingUserId] = useState<string | null>(null);
  const [peerOnline, setPeerOnline] = useState(false);
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
      const page = await getMessagesApi(conversation.conversationId);
      setMessages(page.messages);
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
    } finally {
      setLoadingMessages(false);
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
      if (event.type === 'messaging.presence') {
        const payload = event.payload as {
          conversationId?: string;
          userId?: string;
          status?: string;
        };
        if (
          payload.conversationId !== selectedRef.current?.conversationId ||
          payload.userId === currentUserId
        ) {
          return;
        }
        setPeerOnline(payload.status === 'online');
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

  useEffect(() => {
    const conversationId = selectedConversation?.conversationId;
    setTypingUserId(null);
    setPeerOnline(false);
    lastTypingSentRef.current = false;
    if (!conversationId) return;
    socketClient.sendPresence(conversationId, 'online');
    return () => {
      socketClient.sendPresence(conversationId, 'offline');
      socketClient.sendTyping(conversationId, false);
    };
  }, [selectedConversation?.conversationId]);

  const handleSend = async () => {
    if (!selectedConversation || !newMessage.trim() || sending) return;
    const body = newMessage.trim();
    lastTypingSentRef.current = false;
    socketClient.sendTyping(selectedConversation.conversationId, false);
    setSending(true);
    setError('');
    try {
      const sent = await sendMessageApi(selectedConversation.conversationId, {
        type: 'TEXT',
        body,
        clientMsgId: uuid(),
      });
      upsertMessage(sent);
      setNewMessage('');
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to send message');
    } finally {
      setSending(false);
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

  const sendUploaded = async (type: MediaUploadType, uri: string, fileName: string, mimeType: string) => {
    if (!selectedConversation) return;
    setSending(true);
    setError('');
    try {
      const blob = await (await fetch(uri)).blob();
      const uploaded = await uploadMediaApi(blob, type, {
        fileName,
        conversationId: selectedConversation.conversationId,
      });
      const clientMsgId = uuid();
      const sent =
        type === 'DOCUMENT'
          ? await sendMessageApi(selectedConversation.conversationId, {
              type: 'DOCUMENT',
              body: uploaded.url,
              clientMsgId,
              fileName: uploaded.fileName,
              mimeType: uploaded.contentType || mimeType,
              size: uploaded.size,
            })
          : await sendMessageApi(selectedConversation.conversationId, {
              type,
              body: uploaded.url,
              clientMsgId,
            });
      upsertMessage(sent);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to upload media');
    } finally {
      setSending(false);
    }
  };

  const pickImage = async (asVideo: boolean) => {
    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) {
      setError('Media library permission is required');
      return;
    }
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: asVideo
        ? ImagePicker.MediaTypeOptions.Videos
        : ImagePicker.MediaTypeOptions.Images,
      quality: 0.8,
    });
    if (result.canceled || !result.assets[0]) return;
    const asset = result.assets[0];
    await sendUploaded(
      asVideo ? 'VIDEO' : 'IMAGE',
      asset.uri,
      asset.fileName ?? (asVideo ? 'video.mp4' : 'image.jpg'),
      asset.mimeType ?? (asVideo ? 'video/mp4' : 'image/jpeg'),
    );
  };

  const pickDocument = async () => {
    const result = await DocumentPicker.getDocumentAsync({ copyToCacheDirectory: true });
    if (result.canceled || !result.assets[0]) return;
    const asset = result.assets[0];
    await sendUploaded(
      'DOCUMENT',
      asset.uri,
      asset.name,
      asset.mimeType ?? 'application/octet-stream',
    );
  };

  const titleOf = useMemo(
    () => (conversation: Conversation) =>
      titles[conversation.conversationId] ||
      (conversation.type === 'GROUP' ? 'Group chat' : 'Direct chat'),
    [titles],
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
                active={false}
                onClick={() => void openConversation(item)}
              />
            )}
          />
        )}
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
        onBack={() => {
          setSelectedConversation(null);
          setMessages([]);
          void loadConversations();
        }}
      />
      <Text style={[styles.socketBar, { color: theme.textMuted }]}>WS: {socketStatus}</Text>
      {error ? <Text style={[styles.error, { color: theme.danger }]}>{error}</Text> : null}
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : 'height'} style={styles.chatArea}>
        {loadingMessages ? (
          <ActivityIndicator style={{ marginTop: 24 }} color={theme.primary} />
        ) : (
          <ChatMessages messages={messages} currentUserId={currentUserId} />
        )}
        <View style={styles.emotionRow}>
          {(['like', 'love', 'haha', 'wow', 'sad', 'angry'] as EmotionCode[]).map((code) => (
            <Pressable
              key={code}
              style={[styles.emotionChip, { borderColor: theme.border }]}
              onPress={() => void handleEmotion(code)}
            >
              <Text style={[styles.emotionText, { color: theme.textSecondary }]}>{code}</Text>
            </Pressable>
          ))}
        </View>
        <View style={styles.mediaRow}>
          <Pressable
            style={[styles.mediaChip, { borderColor: theme.primary }]}
            onPress={() => void pickImage(false)}
            disabled={sending}
          >
            <Text style={[styles.emotionText, { color: theme.primary }]}>Image</Text>
          </Pressable>
          <Pressable
            style={[styles.mediaChip, { borderColor: theme.primary }]}
            onPress={() => void pickDocument()}
            disabled={sending}
          >
            <Text style={[styles.emotionText, { color: theme.primary }]}>File</Text>
          </Pressable>
          <Pressable
            style={[styles.mediaChip, { borderColor: theme.primary }]}
            onPress={() => void pickImage(true)}
            disabled={sending}
          >
            <Text style={[styles.emotionText, { color: theme.primary }]}>Video</Text>
          </Pressable>
        </View>
        <View style={[styles.inputContainer, { borderTopColor: theme.border, backgroundColor: theme.background }]}>
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
      </KeyboardAvoidingView>
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
  emotionRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 6,
    paddingHorizontal: 12,
    paddingTop: 8,
  },
  mediaRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 6,
    paddingHorizontal: 12,
    paddingTop: 4,
  },
  emotionChip: {
    borderWidth: 1,
    borderRadius: Radius.full,
    paddingHorizontal: 10,
    paddingVertical: 4,
  },
  mediaChip: {
    borderWidth: 1,
    borderRadius: Radius.full,
    paddingHorizontal: 10,
    paddingVertical: 4,
  },
  emotionText: { fontSize: 12, fontWeight: '500' },
  inputContainer: {
    flexDirection: 'row',
    padding: 12,
    borderTopWidth: 1,
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
