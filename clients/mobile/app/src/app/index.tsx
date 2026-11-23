import React, { useEffect, useState } from 'react';
import { ActivityIndicator, View } from 'react-native';
import { Redirect } from 'expo-router';
import { hasSession, restoreSession } from '@core/authSession';
import { registerPushForCurrentUser } from '@/lib/pushRegistration';
import { useTheme } from '@/hooks/use-theme';

// Khôi phục session đã lưu: còn token hợp lệ (server xác nhận qua GET /me) thì vào tab chat,
// 401 hoặc hết hạn thì về login. Lỗi mạng vẫn giữ session đã lưu.
export default function Index() {
  const theme = useTheme();
  const [target, setTarget] = useState<'/(tabs)/chat' | '/login' | null>(
    hasSession() ? null : '/login',
  );

  useEffect(() => {
    if (target) return;
    let cancelled = false;
    void restoreSession().then((auth) => {
      if (cancelled) return;
      if (auth) {
        void registerPushForCurrentUser().catch(() => undefined);
      }
      setTarget(auth ? '/(tabs)/chat' : '/login');
    });
    return () => {
      cancelled = true;
    };
  }, [target]);

  if (!target) {
    return (
      <View style={{ flex: 1, justifyContent: 'center', backgroundColor: theme.background }}>
        <ActivityIndicator color={theme.primary} />
      </View>
    );
  }
  return <Redirect href={target} />;
}
