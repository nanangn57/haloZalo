import React from 'react';
import { View, Pressable, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useTheme } from '@/hooks/use-theme';
import { Spacing } from '@/constants/theme';

interface MesToolBarProps {
  disabled?: boolean;
  emojiOpen?: boolean;
  emotionOpen?: boolean;
  recording?: boolean;
  onEmojiPress?: () => void;
  onEmotionPress?: () => void;
  onImagePress?: () => void;
  onFilePress?: () => void;
  onVideoPress?: () => void;
  onVoiceStart?: () => void;
  onVoiceStop?: () => void;
}

/** Icon toolbar khớp web MesToolbar: emoji, reaction, image, file, video, hold-to-record. */
export default function MesToolBar({
  disabled,
  emojiOpen,
  emotionOpen,
  recording,
  onEmojiPress,
  onEmotionPress,
  onImagePress,
  onFilePress,
  onVideoPress,
  onVoiceStart,
  onVoiceStop,
}: MesToolBarProps) {
  const theme = useTheme();

  const iconColor = (active?: boolean) =>
    active ? theme.primary : theme.textSecondary;

  return (
    <View style={styles.toolbar} accessibilityRole="toolbar" accessibilityLabel="Message tools">
      {onEmojiPress ? (
        <Pressable
          style={[styles.iconButton, emojiOpen && { backgroundColor: theme.primarySoft }]}
          onPress={onEmojiPress}
          disabled={disabled}
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
          disabled={disabled}
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
          disabled={disabled}
          accessibilityLabel="Attach image"
        >
          <Ionicons name="image-outline" size={22} color={theme.textSecondary} />
        </Pressable>
      ) : null}

      {onFilePress ? (
        <Pressable
          style={styles.iconButton}
          onPress={onFilePress}
          disabled={disabled}
          accessibilityLabel="Attach file"
        >
          <Ionicons name="attach-outline" size={22} color={theme.textSecondary} />
        </Pressable>
      ) : null}

      {onVideoPress ? (
        <Pressable
          style={styles.iconButton}
          onPress={onVideoPress}
          disabled={disabled}
          accessibilityLabel="Attach video"
        >
          <Ionicons name="videocam-outline" size={22} color={theme.textSecondary} />
        </Pressable>
      ) : null}

      {onVoiceStart && onVoiceStop ? (
        <Pressable
          style={[styles.iconButton, recording && { backgroundColor: theme.primarySoft }]}
          disabled={disabled}
          accessibilityLabel={
            recording ? 'Recording… release to send' : 'Hold to record voice message'
          }
          accessibilityState={{ selected: !!recording }}
          onPressIn={() => {
            if (!disabled) onVoiceStart();
          }}
          onPressOut={() => {
            if (!disabled) onVoiceStop();
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
