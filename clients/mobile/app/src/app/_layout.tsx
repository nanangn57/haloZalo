import React, { useEffect, useState } from 'react';
import { ActivityIndicator, View } from 'react-native';
import { Stack } from 'expo-router';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { setAuthStorage } from '@core/authSession';
import { setDeviceIdStorage } from '@core/deviceId';
import { asyncAuthStorage, hydrateAuthStorage } from '@/lib/authStorage';

export default function RootLayout() {
  const [storageReady, setStorageReady] = useState(false);

  // Nạp session từ AsyncStorage trước khi route nào đọc getAuth()/hasSession().
  // index.tsx gọi restoreSession() sau bước này.
  useEffect(() => {
    let cancelled = false;
    void (async () => {
      await hydrateAuthStorage();
      setAuthStorage(asyncAuthStorage);
      setDeviceIdStorage(asyncAuthStorage);
      if (!cancelled) setStorageReady(true);
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  if (!storageReady) {
    return (
      <View style={{ flex: 1, justifyContent: 'center' }}>
        <ActivityIndicator />
      </View>
    );
  }

  return (
    <SafeAreaProvider>
      <Stack screenOptions={{ headerShown: false }}>
        <Stack.Screen name="index" />
        <Stack.Screen name="login" />
        <Stack.Screen name="register" />
        <Stack.Screen name="auth/verify" />
        <Stack.Screen name="(tabs)" />
      </Stack>
    </SafeAreaProvider>
  );
}
