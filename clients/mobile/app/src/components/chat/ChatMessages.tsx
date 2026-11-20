import React from 'react';
import { View, Text, StyleSheet, FlatList } from 'react-native';

export default function ChatMessages({ messages, currentUserId }) {
  return (
    <FlatList
      data={messages}
      keyExtractor={(item) => item.id}
      contentContainerStyle={styles.list}
      renderItem={({ item }) => {
        const isMine = item.senderId === currentUserId;
        return (
          <View style={[styles.messageWrapper, isMine ? styles.mineWrapper : styles.otherWrapper]}>
            <View style={[styles.bubble, isMine ? styles.mineBubble : styles.otherBubble]}>
              <Text style={isMine ? styles.mineText : styles.otherText}>{item.content}</Text>
            </View>
          </View>
        );
      }}
    />
  );
}

const styles = StyleSheet.create({
  list: { padding: 16, flexGrow: 1, justifyContent: 'flex-end' },
  messageWrapper: { marginBottom: 12, maxWidth: '80%' },
  mineWrapper: { alignSelf: 'flex-end' },
  otherWrapper: { alignSelf: 'flex-start' },
  bubble: { paddingHorizontal: 16, paddingVertical: 10, borderRadius: 16 },
  mineBubble: { backgroundColor: '#4f46e5', borderBottomRightRadius: 4 },
  otherBubble: { backgroundColor: '#f1f5f9', borderBottomLeftRadius: 4 },
  mineText: { color: '#fff' },
  otherText: { color: '#1e293b' }
});