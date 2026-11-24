import React, { useEffect, useState } from 'react';
import { Linking, Pressable, StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useVideoPlayer, VideoView } from 'expo-video';

/**
 * Chat video: tap to load player (avoids N players in a long thread).
 * Native controls + fullscreen; fallback link if playback fails.
 */
export default function VideoBubble({
  url,
  surface,
  accent,
}: {
  url: string;
  surface: string;
  accent: string;
}) {
  const [active, setActive] = useState(false);
  const [failed, setFailed] = useState(false);

  if (!url || failed) {
    return (
      <Pressable
        onPress={() => url && void Linking.openURL(url)}
        disabled={!url}
        accessibilityRole="link"
        accessibilityLabel="Open video"
      >
        <Text style={{ color: accent }}>{failed ? 'Video unavailable — open' : 'Open video'}</Text>
      </Pressable>
    );
  }

  if (!active) {
    return (
      <Pressable
        onPress={() => setActive(true)}
        style={[styles.frame, { backgroundColor: surface }]}
        accessibilityRole="button"
        accessibilityLabel="Play video"
      >
        <Ionicons name="play-circle" size={52} color={accent} />
        <Text style={[styles.hint, { color: accent }]}>Tap to play</Text>
      </Pressable>
    );
  }

  return <ActiveVideo url={url} surface={surface} onFailed={() => setFailed(true)} />;
}

function ActiveVideo({
  url,
  surface,
  onFailed,
}: {
  url: string;
  surface: string;
  onFailed: () => void;
}) {
  const player = useVideoPlayer({ uri: url }, (instance) => {
    instance.loop = false;
    instance.play();
  });

  useEffect(() => {
    const sub = player.addListener('statusChange', ({ status, error }) => {
      if (status === 'error' || error) {
        onFailed();
      }
    });
    return () => {
      sub.remove();
    };
  }, [player, onFailed]);

  return (
    <View style={[styles.frame, { backgroundColor: surface }]}>
      <VideoView
        player={player}
        style={styles.video}
        contentFit="contain"
        nativeControls
        fullscreenOptions={{ enable: true }}
        accessibilityLabel="Chat video"
      />
    </View>
  );
}

const styles = StyleSheet.create({
  frame: {
    width: 220,
    height: 160,
    borderRadius: 8,
    overflow: 'hidden',
    alignItems: 'center',
    justifyContent: 'center',
  },
  video: {
    width: '100%',
    height: '100%',
  },
  hint: {
    marginTop: 6,
    fontSize: 12,
    fontWeight: '600',
  },
});
