import React, { useMemo, useState } from 'react';
import { FlatList, Linking, Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import { Image } from 'expo-image';
import { Ionicons } from '@expo/vector-icons';
import { buildMediaGallery, type GalleryItem, type GalleryMediaType } from '@core/mediaGallery';
import type { Message } from '@core/types/message';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import AudioBubble from './AudioBubble';
import VideoBubble from './VideoBubble';

const FILTERS: { key: GalleryMediaType | 'ALL'; label: string }[] = [
  { key: 'ALL', label: 'All' },
  { key: 'IMAGE', label: 'Images' },
  { key: 'VIDEO', label: 'Videos' },
  { key: 'DOCUMENT', label: 'Files' },
  { key: 'AUDIO', label: 'Voice' },
];

const ICON: Record<Exclude<GalleryMediaType, 'AUDIO'>, keyof typeof Ionicons.glyphMap> = {
  IMAGE: 'image-outline',
  VIDEO: 'videocam-outline',
  DOCUMENT: 'document-outline',
};

function openUrl(url: string) {
  void Linking.openURL(url).catch(() => undefined);
}

export default function MediaGalleryModal({
  visible,
  messages,
  onClose,
}: {
  visible: boolean;
  messages: Message[];
  onClose: () => void;
}) {
  const theme = useTheme();
  const [filter, setFilter] = useState<GalleryMediaType | 'ALL'>('ALL');

  const items = useMemo(
    () => buildMediaGallery(messages, filter === 'ALL' ? undefined : filter),
    [messages, filter],
  );

  const renderItem = ({ item }: { item: GalleryItem }) => {
    const when = new Date(item.sentAt * 1000).toLocaleString();
    if (item.type === 'IMAGE') {
      return (
        <Pressable onPress={() => openUrl(item.url)} style={styles.row} accessibilityLabel="Open image">
          <Image
            source={{ uri: item.url }}
            style={[styles.thumb, { backgroundColor: theme.border }]}
            contentFit="cover"
          />
          <Text style={{ color: theme.textSecondary, fontSize: 12 }}>{when}</Text>
        </Pressable>
      );
    }
    if (item.type === 'AUDIO') {
      return (
        <View style={styles.row}>
          <AudioBubble url={item.url} color={theme.text} />
          <Text style={{ color: theme.textSecondary, fontSize: 12 }}>{when}</Text>
        </View>
      );
    }
    if (item.type === 'VIDEO') {
      return (
        <View style={styles.row}>
          <VideoBubble url={item.url} surface={theme.border} accent={theme.primary} />
          <Text style={{ color: theme.textSecondary, fontSize: 12 }}>{when}</Text>
        </View>
      );
    }
    return (
      <Pressable onPress={() => openUrl(item.url)} style={styles.row} accessibilityLabel="Open file">
        <View style={[styles.thumb, styles.iconThumb, { backgroundColor: theme.backgroundElement }]}>
          <Ionicons name={ICON.DOCUMENT} size={28} color={theme.primary} />
        </View>
        <View style={{ flex: 1 }}>
          <Text style={{ color: theme.text, fontWeight: '600' }}>File</Text>
          <Text style={{ color: theme.textSecondary, fontSize: 12 }}>{when}</Text>
        </View>
      </Pressable>
    );
  };

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <Pressable style={styles.backdrop} onPress={onClose}>
        <Pressable
          style={[styles.sheet, { backgroundColor: theme.background, borderColor: theme.border }]}
          onPress={() => undefined}
        >
          <View style={styles.header}>
            <Text style={[styles.title, { color: theme.text }]}>Media</Text>
            <Pressable onPress={onClose} hitSlop={8} accessibilityLabel="Close media">
              <Ionicons name="close" size={22} color={theme.textSecondary} />
            </Pressable>
          </View>
          <View style={styles.filters}>
            {FILTERS.map(({ key, label }) => (
              <Pressable
                key={key}
                onPress={() => setFilter(key)}
                style={[
                  styles.chip,
                  {
                    borderColor: filter === key ? theme.primary : theme.border,
                    backgroundColor: filter === key ? theme.primarySoft : 'transparent',
                  },
                ]}
              >
                <Text style={{ color: filter === key ? theme.primary : theme.textSecondary, fontSize: 12 }}>
                  {label}
                </Text>
              </Pressable>
            ))}
          </View>
          <FlatList
            data={items}
            keyExtractor={(item) => item.id}
            renderItem={renderItem}
            ListEmptyComponent={
              <Text style={{ color: theme.textSecondary, padding: Spacing.three }}>
                No media in the loaded messages.
              </Text>
            }
          />
        </Pressable>
      </Pressable>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, backgroundColor: 'rgba(0,0,0,0.4)', justifyContent: 'flex-end' },
  sheet: {
    borderTopLeftRadius: Radius.xl,
    borderTopRightRadius: Radius.xl,
    borderWidth: 1,
    padding: Spacing.three,
    maxHeight: '75%',
  },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 },
  title: { fontSize: 16, fontWeight: '700' },
  filters: { flexDirection: 'row', flexWrap: 'wrap', gap: 6, marginBottom: 8 },
  chip: { borderWidth: 1, borderRadius: Radius.full, paddingHorizontal: 10, paddingVertical: 4 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 8 },
  thumb: { width: 64, height: 64, borderRadius: 8 },
  iconThumb: { alignItems: 'center', justifyContent: 'center' },
});
