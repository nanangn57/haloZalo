import React, { useState } from 'react';
import { View, Text, StyleSheet, FlatList, Linking, Pressable } from 'react-native';
import { Image } from 'expo-image';
import type { Message } from '@core/types/message';
import { resolveMediaUrl } from '@core/mediaService';
import { useTheme } from '@/hooks/use-theme';

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
      <MediaFallback
        label="Open video"
        url={mediaUrl}
        isMine={isMine}
        mineColor={mineColor}
        otherColor={otherColor}
      />
    );
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

export default function ChatMessages({
  messages,
  currentUserId,
}: {
  messages: Message[];
  currentUserId: string;
}) {
  const theme = useTheme();

  return (
    <FlatList
      data={messages}
      keyExtractor={(item) => item.id}
      contentContainerStyle={styles.list}
      renderItem={({ item }) => {
        const isMine = item.senderId === currentUserId;
        return (
          <View
            style={[
              styles.messageWrapper,
              isMine ? styles.mineWrapper : styles.otherWrapper,
            ]}
          >
            <View
              style={[
                styles.bubble,
                {
                  backgroundColor: isMine ? theme.chatOwn : theme.chatOther,
                  borderBottomRightRadius: isMine ? 4 : 16,
                  borderBottomLeftRadius: isMine ? 16 : 4,
                },
              ]}
            >
              <MessageBody
                key={item.id}
                message={item}
                isMine={isMine}
                mineColor={theme.chatOwnText}
                otherColor={theme.chatOtherText}
                imageBg={theme.border}
              />
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
  emotion: { fontSize: 28 },
  image: { width: 180, height: 180, borderRadius: 8 },
});
