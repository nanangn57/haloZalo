import React, { useState } from 'react';
import { View, Text, StyleSheet, FlatList, TextInput, Pressable, KeyboardAvoidingView, Platform } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import ChatHeader from '../../components/chat/ChatHeader';
import ChatMessages from '../../components/chat/ChatMessages';
import ConversationItem from '../../components/chat/ConversationItem';

export default function ChatPage() {
  const [selectedConversation, setSelectedConversation] = useState(null);
  const [newMessage, setNewMessage] = useState('');
  
  // Mock Data
  const conversations = [{ conversationId: '1', type: 'DIRECT', title: 'John Doe', unreadCount: 2 }];
  const messages = [{ id: 'm1', content: 'Hello there!', senderId: '1', sentAt: Date.now() / 1000 }];
  const currentUserId = 'me';

  if (!selectedConversation) {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.sidebarHeader}><Text style={styles.sidebarTitle}>Conversations</Text></View>
        <FlatList
          data={conversations}
          keyExtractor={(item) => item.conversationId}
          renderItem={({ item }) => (
            <ConversationItem 
              conversation={item} 
              title={item.title} 
              active={false} 
              onClick={() => setSelectedConversation(item)} 
            />
          )}
        />
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.container}>
      <ChatHeader 
        title={selectedConversation.title} 
        onBack={() => setSelectedConversation(null)} 
      />
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : 'height'} style={styles.chatArea}>
        <ChatMessages messages={messages} currentUserId={currentUserId} />
        <View style={styles.inputContainer}>
          <TextInput
            style={styles.input}
            placeholder="Type a message..."
            value={newMessage}
            onChangeText={setNewMessage}
          />
          <Pressable style={styles.sendButton}>
            <Ionicons name="send" size={20} color="#fff" />
          </Pressable>
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  sidebarHeader: { padding: 16, borderBottomWidth: 1, borderBottomColor: '#e2e8f0' },
  sidebarTitle: { fontSize: 20, fontWeight: 'bold' },
  chatArea: { flex: 1 },
  inputContainer: { flexDirection: 'row', padding: 12, borderTopWidth: 1, borderTopColor: '#e2e8f0', alignItems: 'center' },
  input: { flex: 1, backgroundColor: '#f1f5f9', borderRadius: 20, paddingHorizontal: 16, paddingVertical: 10, marginRight: 8 },
  sendButton: { backgroundColor: '#4f46e5', width: 40, height: 40, borderRadius: 20, justifyContent: 'center', alignItems: 'center' }
});