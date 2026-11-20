import { Platform } from 'react-native';
import { getAuth } from '@core/authSession';
import { registerDeviceTokenApi } from '@core/deviceService';

/**
 * Registers an Expo push token after login when expo-notifications is available.
 * Falls back to a stable local demo token so offline-push wiring can be tested without FCM/cloud.
 */
export async function registerPushForCurrentUser(): Promise<string | null> {
  const userId = getAuth()?.user?.userId;
  if (!userId) {
    return null;
  }

  let token: string | null = null;
  let platform: 'ios' | 'android' | 'web' | 'unknown' = 'unknown';

  try {
    // Optional: install with `npx expo install expo-notifications`.
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const Notifications = require('expo-notifications') as {
      getPermissionsAsync: () => Promise<{ status: string }>;
      requestPermissionsAsync: () => Promise<{ status: string }>;
      getExpoPushTokenAsync: () => Promise<{ data: string }>;
    };
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const Device = require('expo-device') as { isDevice: boolean };

    if (Device.isDevice) {
      const current = await Notifications.getPermissionsAsync();
      let status = current.status;
      if (status !== 'granted') {
        status = (await Notifications.requestPermissionsAsync()).status;
      }
      if (status === 'granted') {
        token = (await Notifications.getExpoPushTokenAsync()).data;
      }
    }
  } catch {
    // Module missing or permission denied — use demo token below.
  }

  if (!token) {
    token = `demo-push-${userId}`;
  }

  if (Platform.OS === 'ios') platform = 'ios';
  else if (Platform.OS === 'android') platform = 'android';
  else if (Platform.OS === 'web') platform = 'web';

  await registerDeviceTokenApi(token, platform);
  return token;
}
