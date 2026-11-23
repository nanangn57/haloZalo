import React from 'react';
import { View, Text, StyleSheet, Pressable } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { getInitials } from '@core/avatar';
import { getLastMessagePreview } from '@core/conversationPreview';
import type { Conversation } from '@core/types/conversation';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

type ConversationItemProps = {
  conversation: Conversation;
  title?: string;
  currentUserId?: string;
  active?: boolean;
  onClick: () => void;
  // Giữ lâu để mở menu pin / mute / archive.
  onLongPress?: () => void;
};

export default function ConversationItem({
  conversation,
  title,
  currentUserId,
  active,
  onClick,
  onLongPress,
}: ConversationItemProps) {
  const theme = useTheme();
  const displayTitle =
    title || (conversation.type === 'GROUP' ? 'Group' : 'Direct Message');
  const preview = getLastMessagePreview(conversation, currentUserId);
  const hasTitle = Boolean(title);

  return (
    <Pressable
      style={[
        styles.container,
        {
          borderBottomColor: theme.border,
          backgroundColor: active ? theme.backgroundSelected : 'transparent',
        },
      ]}
      onPress={onClick}
      onLongPress={onLongPress}
      delayLongPress={350}
      accessibilityHint={onLongPress ? 'Long press for pin, mute and archive' : undefined}
    >
      <View
        style={[
          styles.iconContainer,
          { backgroundColor: theme.backgroundElement },
        ]}
      >
        {hasTitle ? (
          <Text style={[styles.initials, { color: theme.primary }]}>
            {getInitials(displayTitle)}
          </Text>
        ) : (
          <Ionicons
            name={conversation.type === 'GROUP' ? 'people' : 'person'}
            size={24}
            color={theme.textSecondary}
          />
        )}
      </View>
      <View style={styles.info}>
        <Text style={[styles.title, { color: theme.text }]} numberOfLines={1}>
          {displayTitle}
        </Text>
        <Text
          style={[styles.subtitle, { color: theme.textSecondary }]}
          numberOfLines={1}
        >
          {preview ?? 'Tap to view messages'}
        </Text>
      </View>
      {conversation.muted ? (
        <Ionicons
          name="notifications-off-outline"
          size={16}
          color={theme.textMuted}
          accessibilityLabel="Muted"
          style={styles.flag}
        />
      ) : null}
      {conversation.pinned ? (
        <Ionicons
          name="pin"
          size={16}
          color={theme.textMuted}
          accessibilityLabel="Pinned"
          style={styles.flag}
        />
      ) : null}
      {(conversation.unreadCount ?? 0) > 0 ? (
        <View style={[styles.badge, { backgroundColor: theme.primary }]}>
          <Text style={styles.badgeText}>{conversation.unreadCount}</Text>
        </View>
      ) : null}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: Spacing.three,
    borderBottomWidth: 1,
  },
  iconContainer: {
    width: 48,
    height: 48,
    borderRadius: 24,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  initials: { fontSize: 16, fontWeight: '700' },
  info: { flex: 1 },
  flag: { marginLeft: 6 },
  title: { fontSize: 16, fontWeight: '600' },
  subtitle: { fontSize: 13, marginTop: 2 },
  badge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: Radius.full,
  },
  badgeText: { color: '#fff', fontSize: 12, fontWeight: '700' },
});
