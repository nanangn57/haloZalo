import React, { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Audio, type AVPlaybackStatus } from 'expo-av';

function formatClock(millis: number): string {
  const total = Math.max(0, Math.round(millis / 1000));
  const minutes = Math.floor(total / 60);
  const seconds = total % 60;
  return `${minutes}:${String(seconds).padStart(2, '0')}`;
}

/** Voice note: nút play/pause + thời lượng. Sound chỉ tải khi bấm play lần đầu. */
export default function AudioBubble({ url, color }: { url: string; color: string }) {
  const soundRef = useRef<Audio.Sound | null>(null);
  const [loading, setLoading] = useState(false);
  const [playing, setPlaying] = useState(false);
  const [failed, setFailed] = useState(false);
  const [position, setPosition] = useState(0);
  const [duration, setDuration] = useState(0);

  useEffect(() => {
    return () => {
      void soundRef.current?.unloadAsync().catch(() => undefined);
      soundRef.current = null;
    };
  }, []);

  const onStatus = (status: AVPlaybackStatus) => {
    if (!status.isLoaded) {
      if (status.error) setFailed(true);
      return;
    }
    setPosition(status.positionMillis);
    if (status.durationMillis) setDuration(status.durationMillis);
    setPlaying(status.isPlaying);
    if (status.didJustFinish) {
      setPlaying(false);
      setPosition(0);
      void soundRef.current?.setPositionAsync(0).catch(() => undefined);
    }
  };

  const toggle = async () => {
    if (!url || loading) return;
    try {
      if (!soundRef.current) {
        setLoading(true);
        setFailed(false);
        await Audio.setAudioModeAsync({ playsInSilentModeIOS: true });
        const { sound } = await Audio.Sound.createAsync({ uri: url }, { shouldPlay: true }, onStatus);
        soundRef.current = sound;
        return;
      }
      if (playing) {
        await soundRef.current.pauseAsync();
      } else {
        await soundRef.current.playAsync();
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
