import React, { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { createAudioPlayer, setAudioModeAsync, type AudioPlayer, type AudioStatus } from 'expo-audio';

function formatClock(seconds: number): string {
  const total = Math.max(0, Math.round(seconds));
  const minutes = Math.floor(total / 60);
  const secs = total % 60;
  return `${minutes}:${String(secs).padStart(2, '0')}`;
}

/** Voice note: nút play/pause + thời lượng. Sound chỉ tải khi bấm play lần đầu. */
export default function AudioBubble({ url, color }: { url: string; color: string }) {
  const playerRef = useRef<AudioPlayer | null>(null);
  const [loading, setLoading] = useState(false);
  const [playing, setPlaying] = useState(false);
  const [failed, setFailed] = useState(false);
  const [position, setPosition] = useState(0);
  const [duration, setDuration] = useState(0);

  useEffect(() => {
    return () => {
      playerRef.current?.remove();
      playerRef.current = null;
    };
  }, []);

  const onStatus = (status: AudioStatus) => {
    if (status.error) {
      setFailed(true);
      return;
    }
    if (!status.isLoaded) return;
    setPosition(status.currentTime);
    if (status.duration > 0) setDuration(status.duration);
    setPlaying(status.playing);
    if (status.didJustFinish) {
      setPlaying(false);
      setPosition(0);
      void playerRef.current?.seekTo(0).catch(() => undefined);
    }
  };

  const toggle = async () => {
    if (!url || loading) return;
    try {
      if (!playerRef.current) {
        setLoading(true);
        setFailed(false);
        await setAudioModeAsync({ playsInSilentMode: true });
        const player = createAudioPlayer({ uri: url }, { updateInterval: 250 });
        player.addListener('playbackStatusUpdate', onStatus);
        playerRef.current = player;
        player.play();
        return;
      }
      if (playing) {
        playerRef.current.pause();
      } else {
        if (duration > 0 && playerRef.current.currentTime >= duration - 0.05) {
          await playerRef.current.seekTo(0);
        }
        playerRef.current.play();
      }
    } catch {
      setFailed(true);
    } finally {
      setLoading(false);
    }
  };

  if (!url) {
    return <Text style={{ color }}>Voice message unavailable</Text>;
  }

  return (
    <Pressable
      style={styles.row}
      onPress={() => void toggle()}
      accessibilityRole="button"
      accessibilityLabel={playing ? 'Pause voice message' : 'Play voice message'}
    >
      {loading ? (
        <ActivityIndicator color={color} />
      ) : (
        <Ionicons name={playing ? 'pause-circle' : 'play-circle'} size={32} color={color} />
      )}
      <View style={{ marginLeft: 8 }}>
        <Text style={{ color, fontWeight: '600' }}>Voice message</Text>
        <Text style={{ color, fontSize: 12, opacity: 0.8 }}>
          {failed
            ? 'Cannot play'
            : duration > 0
              ? `${formatClock(position)} / ${formatClock(duration)}`
              : 'Tap to play'}
        </Text>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', minWidth: 160 },
});
