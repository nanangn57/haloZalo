import React from 'react';
import { View, Text, StyleSheet, Pressable } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export default function ChatHeader({
  title,
  subtitle,
  onBack,
  onMediaPress,
}: {
  title: string;
  subtitle?: string;
  onBack: () => void;
  // Mở danh sách media của hội thoại.
  onMediaPress?: () => void;
}) {
  const theme = useTheme();

  return (
    <View
      style={[
        styles.container,
        {
          borderBottomColor: theme.border,
          backgroundColor: theme.background,
        },
      ]}
    >
      <Pressable
        onPress={onBack}
        style={styles.backButton}
        hitSlop={8}
        accessibilityRole="button"
        accessibilityLabel="Back to conversations"
      >
        <Ionicons name="arrow-back" size={24} color={theme.text} />
      </Pressable>
      <View style={styles.titles}>
        <Text style={[styles.title, { color: theme.text }]} numberOfLines={1}>
          {title}
        </Text>
        {subtitle ? (
          <Text style={[styles.subtitle, { color: theme.textSecondary }]}>
            {subtitle}
          </Text>
        ) : null}
      </View>
      {onMediaPress ? (
        <Pressable
          onPress={onMediaPress}
          style={styles.mediaButton}
          hitSlop={8}
          accessibilityRole="button"
          accessibilityLabel="Shared media"
        >
          <Ionicons name="images-outline" size={22} color={theme.text} />
        </Pressable>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: Spacing.three,
    paddingVertical: 14,
    borderBottomWidth: 1,
  },
  backButton: { marginRight: Spacing.three },
  titles: { flex: 1, minWidth: 0 },
  mediaButton: { marginLeft: Spacing.three },
  title: { fontSize: 18, fontWeight: '600' },
  subtitle: { fontSize: 12, marginTop: 2 },
});
