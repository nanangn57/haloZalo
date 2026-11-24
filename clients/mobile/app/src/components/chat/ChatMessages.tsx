import React, { useEffect, useRef, useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  Linking,
  Pressable,
  ActivityIndicator,
  type NativeScrollEvent,
  type NativeSyntheticEvent,
} from 'react-native';
import { Image } from 'expo-image';
import { Ionicons } from '@expo/vector-icons';
import type { Message } from '@core/types/message';
import type { MessageReaction, ReactionCode } from '@core/messageService';
import { resolveMediaUrl } from '@core/mediaService';
import { useTheme } from '@/hooks/use-theme';
import AudioBubble from './AudioBubble';
import VideoBubble from './VideoBubble';

const LOAD_OLDER_THRESHOLD_PX = 64;
const NEAR_BOTTOM_PX = 96;

const EMOTION_LABEL: Record<string, string> = {
  like: '👍',
  love: '❤️',
  haha: '😄',
  wow: '😮',
  sad: '😢',
  angry: '😠',
};

function MediaFallback({
  label,
  url,
  isMine,
  mineColor,
  otherColor,
}: {
  label: string;
  url: string;
  isMine: boolean;
  mineColor: string;
  otherColor: string;
}) {
  return (
    <Pressable onPress={() => void Linking.openURL(url)} disabled={!url}>
      <Text style={{ color: isMine ? mineColor : otherColor }}>{label}</Text>
    </Pressable>
  );
}

function MessageBody({
  message,
  isMine,
  mineColor,
  otherColor,
  imageBg,
}: {
  message: Message;
  isMine: boolean;
  mineColor: string;
  otherColor: string;
  imageBg: string;
}) {
  const [imageFailed, setImageFailed] = useState(false);
  const mediaUrl = resolveMediaUrl(message.content);

  if (message.deleted) {
    return (
      <Text style={{ color: isMine ? mineColor : otherColor, fontStyle: 'italic', opacity: 0.7 }}>
        Message recalled
      </Text>
    );
  }

  if (message.type === 'EMOTION') {
    return (
      <Text style={styles.emotion}>
        {EMOTION_LABEL[message.content] ?? message.content}
      </Text>
    );
  }
  if (message.type === 'IMAGE') {
    if (!mediaUrl || imageFailed) {
      return (
        <MediaFallback
          label="Open image"
          url={mediaUrl}
          isMine={isMine}
          mineColor={mineColor}
          otherColor={otherColor}
        />
      );
    }
    return (
      <Pressable onPress={() => void Linking.openURL(mediaUrl)}>
        <Image
          source={{ uri: mediaUrl }}
          style={[styles.image, { backgroundColor: imageBg }]}
          contentFit="cover"
          transition={200}
          onError={() => setImageFailed(true)}
          accessibilityLabel="chat image"
        />
      </Pressable>
    );
  }
  if (message.type === 'VIDEO') {
    return (
      <VideoBubble
        url={mediaUrl}
        surface={imageBg}
        accent={isMine ? mineColor : otherColor}
      />
    );
  }
  if (message.type === 'AUDIO') {
    return <AudioBubble url={mediaUrl} color={isMine ? mineColor : otherColor} />;
  }
  if (message.type === 'DOCUMENT') {
    return (
      <MediaFallback
        label="Open file"
        url={mediaUrl}
        isMine={isMine}
        mineColor={mineColor}
        otherColor={otherColor}
      />
    );
  }
  return (
    <Text style={{ color: isMine ? mineColor : otherColor }}>
      {message.content}
    </Text>
  );
}

function formatMessageTime(sentAt: number): string {
  return new Date(sentAt * 1000).toLocaleTimeString([], {
    hour: '2-digit',
    minute: '2-digit',
  });
}

function replySnippet(message: Message | undefined): string {
  if (!message) return 'Original message';
  if (message.deleted) return 'Message recalled';
  switch (message.type) {
    case 'IMAGE':
      return '🖼 Image';
    case 'VIDEO':
      return '🎬 Video';
    case 'DOCUMENT':
      return '📎 File';
    case 'AUDIO':
      return '🎤 Voice message';
    case 'EMOTION':
      return EMOTION_LABEL[message.content] ?? message.content;
    default:
      return message.content.length > 80 ? `${message.content.slice(0, 79)}…` : message.content;
  }
}

export default function ChatMessages({
  messages,
  currentUserId,
  conversationId,
  hasMoreOlder = false,
  loadingOlder = false,
  onLoadOlder,
  reactions = {},
  getSenderName,
  onLongPress,
  onToggleReaction,
}: {
  messages: Message[];
  currentUserId: string;
  conversationId?: string;
  hasMoreOlder?: boolean;
  loadingOlder?: boolean;
  onLoadOlder?: () => boolean | Promise<boolean>;
  reactions?: Record<string, MessageReaction[]>;
  getSenderName?: (userId: string) => string;
  // Giữ lâu một tin để mở menu: reply / react / forward / recall.
  onLongPress?: (message: Message) => void;
  onToggleReaction?: (message: Message, code: ReactionCode) => void;
}) {
  const theme = useTheme();
  const listRef = useRef<FlatList<Message>>(null);
  const stickToBottomRef = useRef(true);
  const loadingOlderGuardRef = useRef(false);
  const pendingInitialScrollRef = useRef(true);

  const byMessageId = new Map<string, Message>();
  messages.forEach((message) => {
    if (message.messageId) byMessageId.set(message.messageId, message);
  });

  const senderName = (userId: string) =>
    userId === currentUserId ? 'You' : (getSenderName?.(userId) ?? 'User');

  useEffect(() => {
    pendingInitialScrollRef.current = true;
    stickToBottomRef.current = true;
  }, [conversationId]);

  useEffect(() => {
    if (messages.length === 0) return;
    if (pendingInitialScrollRef.current || stickToBottomRef.current) {
      requestAnimationFrame(() => {
        listRef.current?.scrollToEnd({ animated: !pendingInitialScrollRef.current });
        pendingInitialScrollRef.current = false;
      });
    }
  }, [messages, conversationId]);

  async function handleScroll(event: NativeSyntheticEvent<NativeScrollEvent>) {
    const { contentOffset, contentSize, layoutMeasurement } = event.nativeEvent;
    const distanceFromBottom =
      contentSize.height - contentOffset.y - layoutMeasurement.height;
    stickToBottomRef.current = distanceFromBottom < NEAR_BOTTOM_PX;

    if (
      !onLoadOlder ||
      !hasMoreOlder ||
      loadingOlder ||
      loadingOlderGuardRef.current ||
      contentOffset.y > LOAD_OLDER_THRESHOLD_PX
    ) {
      return;
    }

    loadingOlderGuardRef.current = true;
    try {
      await onLoadOlder();
    } finally {
      loadingOlderGuardRef.current = false;
    }
  }

  return (
    <FlatList
      ref={listRef}
      data={messages}
      keyExtractor={(item) => item.id}
      contentContainerStyle={styles.list}
      extraData={reactions}
      maintainVisibleContentPosition={{ minIndexForVisible: 0 }}
      onScroll={(event) => {
        void handleScroll(event);
      }}
      scrollEventThrottle={16}
      ListHeaderComponent={
        loadingOlder ? (
          <ActivityIndicator style={{ marginVertical: 8 }} color={theme.primary} />
        ) : null
      }
      renderItem={({ item }) => {
        const isMine = item.senderId === currentUserId;
        const persisted = Boolean(item.messageId) && item.status !== 'sending';
        const canAct = persisted && !item.deleted;
        const reply = item.replyTo ? byMessageId.get(item.replyTo) : undefined;
        const textColor = isMine ? theme.chatOwnText : theme.chatOtherText;
        const itemReactions = item.messageId ? (reactions[item.messageId] ?? []) : [];
        const grouped = (['like', 'love', 'haha', 'wow', 'sad', 'angry'] as ReactionCode[])
          .map((code) => {
            const rows = itemReactions.filter((reaction) => reaction.code === code);
            return {
              code,
              count: rows.length,
              mine: rows.some((reaction) => reaction.userId === currentUserId),
            };
          })
          .filter((group) => group.count > 0);

        return (
          <View
            style={[
              styles.messageWrapper,
              isMine ? styles.mineWrapper : styles.otherWrapper,
            ]}
          >
            {item.forwardedFrom && !item.deleted ? (
              <View style={styles.forwardedRow}>
                <Ionicons name="arrow-redo-outline" size={11} color={theme.textMuted} />
                <Text style={[styles.forwarded, { color: theme.textMuted }]}>Forwarded</Text>
              </View>
            ) : null}
            <View style={styles.bubbleRow}>
              {/* Web: tin mình → ⋯ bên trái bubble; tin người khác → ⋯ bên phải. */}
              {isMine && canAct && onLongPress ? (
                <Pressable
                  style={[styles.menuTrigger, { backgroundColor: theme.backgroundElement }]}
                  onPress={() => onLongPress(item)}
                  hitSlop={8}
                  accessibilityLabel="Message actions"
                  accessibilityRole="button"
                >
                  <Ionicons name="ellipsis-horizontal" size={16} color={theme.textMuted} />
                </Pressable>
              ) : null}
              <Pressable
                onLongPress={canAct ? () => onLongPress?.(item) : undefined}
                delayLongPress={350}
                style={[
                  styles.bubble,
                  {
                    backgroundColor: isMine ? theme.chatOwn : theme.chatOther,
                    borderBottomRightRadius: isMine ? 4 : 16,
                    borderBottomLeftRadius: isMine ? 16 : 4,
                    opacity: item.deleted ? 0.6 : 1,
                  },
                ]}
              >
                {item.replyTo && !item.deleted ? (
                  <View style={[styles.replyQuote, { borderLeftColor: textColor }]}>
                    <Text style={[styles.replyName, { color: textColor }]} numberOfLines={1}>
                      {reply ? senderName(reply.senderId) : 'Reply'}
                    </Text>
                    <Text style={[styles.replyText, { color: textColor }]} numberOfLines={1}>
                      {replySnippet(reply)}
                    </Text>
                  </View>
                ) : null}
                <MessageBody
                  key={item.id}
                  message={item}
                  isMine={isMine}
                  mineColor={theme.chatOwnText}
                  otherColor={theme.chatOtherText}
                  imageBg={theme.border}
                />
              </Pressable>
              {!isMine && canAct && onLongPress ? (
                <Pressable
                  style={[styles.menuTrigger, { backgroundColor: theme.backgroundElement }]}
                  onPress={() => onLongPress(item)}
                  hitSlop={8}
                  accessibilityLabel="Message actions"
                  accessibilityRole="button"
                >
                  <Ionicons name="ellipsis-horizontal" size={16} color={theme.textMuted} />
                </Pressable>
              ) : null}
            </View>
            {grouped.length > 0 ? (
              <View style={[styles.reactions, isMine && styles.reactionsMine]}>
                {grouped.map((group) => (
                  <Pressable
                    key={group.code}
                    disabled={!canAct || !onToggleReaction}
                    onPress={() => onToggleReaction?.(item, group.code)}
                    style={[
                      styles.reactionChip,
                      {
                        borderColor: group.mine ? theme.primary : theme.border,
                        backgroundColor: group.mine ? theme.primarySoft : theme.background,
                      },
                    ]}
                  >
                    <Text style={{ fontSize: 12, color: theme.text }}>
                      {EMOTION_LABEL[group.code]} {group.count}
                    </Text>
                  </Pressable>
                ))}
              </View>
            ) : null}
            <Text
              style={[
                styles.messageTime,
                isMine ? styles.messageTimeMine : styles.messageTimeOther,
                { color: theme.textMuted },
              ]}
            >
              {formatMessageTime(item.sentAt)}
              {item.status === 'failed' ? ' · failed' : ''}
            </Text>
          </View>
        );
      }}
    />
  );
}

const styles = StyleSheet.create({
  list: { padding: 16, flexGrow: 1, justifyContent: 'flex-end' },
  messageWrapper: { marginBottom: 12, maxWidth: '88%' },
  mineWrapper: { alignSelf: 'flex-end' },
  otherWrapper: { alignSelf: 'flex-start' },
  bubbleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  menuTrigger: {
    width: 28,
    height: 28,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
  },
  bubble: { paddingHorizontal: 16, paddingVertical: 10, borderRadius: 16, maxWidth: '100%', flexShrink: 1 },
  emotion: { fontSize: 28 },
  forwardedRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    marginBottom: 2,
  },
  forwarded: { fontSize: 11 },
  replyQuote: {
    borderLeftWidth: 3,
    paddingLeft: 8,
    marginBottom: 6,
    opacity: 0.85,
  },
  replyName: { fontSize: 12, fontWeight: '700' },
  replyText: { fontSize: 12 },
  reactions: { flexDirection: 'row', flexWrap: 'wrap', gap: 4, marginTop: 4 },
  reactionsMine: { justifyContent: 'flex-end' },
  reactionChip: {
    borderWidth: 1,
    borderRadius: 999,
    paddingHorizontal: 8,
    paddingVertical: 2,
  },
  messageTime: {
    fontSize: 11,
    marginTop: 4,
  },
  messageTimeMine: { alignSelf: 'flex-end' },
  messageTimeOther: { alignSelf: 'flex-start' },
  image: { width: 180, height: 180, borderRadius: 8 },
});
