import React from 'react';
import { View, Text, StyleSheet, Pressable } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

type ConversationItemProps = {
  conversation: {
    type: string;
    unreadCount?: number;
  };
  title?: string;
  active?: boolean;
  onClick: () => void;
};

export default function ConversationItem({
  conversation,
  title,
  active,
  onClick,
}: ConversationItemProps) {
  const theme = useTheme();
  const displayTitle =
    title || (conversation.type === 'GROUP' ? 'Group' : 'Direct Message');

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
    >
      <View
        style={[
          styles.iconContainer,
          { backgroundColor: theme.backgroundElement },
        ]}
      >
        <Ionicons
          name={conversation.type === 'GROUP' ? 'people' : 'person'}
          size={24}
          color={theme.textSecondary}
        />
      </View>
      <View style={styles.info}>
        <Text style={[styles.title, { color: theme.text }]} numberOfLines={1}>
          {displayTitle}
        </Text>
        <Text style={[styles.subtitle, { color: theme.textSecondary }]}>
          Tap to view messages
        </Text>
      </View>
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
  info: { flex: 1 },
  title: { fontSize: 16, fontWeight: '600' },
  subtitle: { fontSize: 13, marginTop: 2 },
  badge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: Radius.full,
  },
  badgeText: { color: '#fff', fontSize: 12, fontWeight: '700' },
});
