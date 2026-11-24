import React from 'react';
import { View, Pressable, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useTheme } from '@/hooks/use-theme';
import { Spacing } from '@/constants/theme';

interface MesToolBarProps {
  /** Disables the whole toolbar (rare). */
  disabled?: boolean;
  /** Only attachment actions (image/file/video/voice) — keep emoji/text usable while uploading. */
  attachmentsDisabled?: boolean;
  emojiOpen?: boolean;
  emotionOpen?: boolean;
  recording?: boolean;
  onEmojiPress?: () => void;
  onEmotionPress?: () => void;
  onImagePress?: () => void;
  onFilePress?: () => void;
  onVideoPress?: () => void;
  /** Tap to start; tap again to stop & send (avoids hold+permission race on phone). */
  onVoiceToggle?: () => void;
}

/** Icon toolbar khớp web MesToolbar: emoji, reaction, image, file, video, voice note. */
export default function MesToolBar({
  disabled,
  attachmentsDisabled,
  emojiOpen,
  emotionOpen,
  recording,
  onEmojiPress,
  onEmotionPress,
  onImagePress,
  onFilePress,
  onVideoPress,
  onVoiceToggle,
}: MesToolBarProps) {
  const theme = useTheme();
  const allDisabled = !!disabled;
  const mediaDisabled = allDisabled || !!attachmentsDisabled;

  const iconColor = (active?: boolean) =>
    active ? theme.primary : theme.textSecondary;

  return (
    <View style={styles.toolbar} accessibilityRole="toolbar" accessibilityLabel="Message tools">
      {onEmojiPress ? (
        <Pressable
          style={[styles.iconButton, emojiOpen && { backgroundColor: theme.primarySoft }]}
          onPress={onEmojiPress}
          disabled={allDisabled}
          accessibilityLabel="Insert emoji"
          accessibilityState={{ selected: !!emojiOpen }}
        >
          <Ionicons name="happy-outline" size={22} color={iconColor(emojiOpen)} />
        </Pressable>
      ) : null}

      {onEmotionPress ? (
        <Pressable
          style={[styles.iconButton, emotionOpen && { backgroundColor: theme.primarySoft }]}
          onPress={onEmotionPress}
          disabled={allDisabled}
          accessibilityLabel="Send reaction"
          accessibilityState={{ selected: !!emotionOpen }}
        >
          <Ionicons name="heart-outline" size={22} color={iconColor(emotionOpen)} />
        </Pressable>
      ) : null}

      {onImagePress ? (
        <Pressable
          style={styles.iconButton}
          onPress={onImagePress}
          disabled={mediaDisabled}
          accessibilityLabel="Attach image"
        >
          <Ionicons name="image-outline" size={22} color={theme.textSecondary} />
        </Pressable>
      ) : null}

      {onFilePress ? (
        <Pressable
          style={styles.iconButton}
          onPress={onFilePress}
          disabled={mediaDisabled}
          accessibilityLabel="Attach file"
        >
          <Ionicons name="attach-outline" size={22} color={theme.textSecondary} />
        </Pressable>
      ) : null}

      {onVideoPress ? (
        <Pressable
          style={styles.iconButton}
          onPress={onVideoPress}
          disabled={mediaDisabled}
          accessibilityLabel="Attach video"
        >
          <Ionicons name="videocam-outline" size={22} color={theme.textSecondary} />
        </Pressable>
      ) : null}

      {onVoiceToggle ? (
        <Pressable
          style={[styles.iconButton, recording && { backgroundColor: theme.primarySoft }]}
          disabled={mediaDisabled}
          accessibilityLabel={
            recording ? 'Stop and send voice message' : 'Record voice message'
          }
          accessibilityHint="Tap once to start recording, tap again to send"
          accessibilityState={{ selected: !!recording }}
          onPress={() => {
            if (!mediaDisabled) onVoiceToggle();
          }}
        >
          <Ionicons
            name={recording ? 'mic' : 'mic-outline'}
            size={22}
            color={recording ? theme.danger : theme.textSecondary}
          />
        </Pressable>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  toolbar: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 2,
    paddingHorizontal: Spacing.two,
    paddingTop: Spacing.one,
  },
  iconButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
