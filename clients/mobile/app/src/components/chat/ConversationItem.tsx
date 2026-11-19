import React from 'react';
import { View, Text, StyleSheet, Pressable } from 'react-native';
import { Ionicons } from '@expo/vector-icons';

export default function ConversationItem({ conversation, title, active, onClick }) {
  const displayTitle = title || (conversation.type === 'GROUP' ? 'Group' : 'Direct Message');
  return (
    <Pressable style={[styles.container, active && styles.active]} onPress={onClick}>
      <View style={styles.iconContainer}>
        <Ionicons name={conversation.type === 'GROUP' ? 'people' : 'person'} size={24} color="#64748b" />
      </View>
      <View style={styles.info}>
        <Text style={styles.title} numberOfLines={1}>{displayTitle}</Text>
        <Text style={styles.subtitle}>Tap to view messages</Text>
      </View>
      {conversation.unreadCount > 0 && (
        <View style={styles.badge}><Text style={styles.badgeText}>{conversation.unreadCount}</Text></View>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  container: { flexDirection: 'row', alignItems: 'center', padding: 16, borderBottomWidth: 1, borderBottomColor: '#f1f5f9' },
  active: { backgroundColor: '#eef2ff' },
  iconContainer: { width: 48, height: 48, borderRadius: 24, backgroundColor: '#f1f5f9', justifyContent: 'center', alignItems: 'center', marginRight: 12 },
  info: { flex: 1 },
  title: { fontSize: 16, fontWeight: '600', color: '#1e293b' },
  subtitle: { fontSize: 13, color: '#64748b', marginTop: 2 },
  badge: { backgroundColor: '#4f46e5', paddingHorizontal: 8, paddingVertical: 4, borderRadius: 12 },
  badgeText: { color: '#fff', fontSize: 12, fontWeight: 'bold' }
});