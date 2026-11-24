import { Platform } from 'react-native';
import { isRunningInExpoGo } from 'expo';
import { requireOptionalNativeModule } from 'expo-modules-core';
import { getAuth } from '@core/authSession';
import { registerDeviceTokenApi } from '@core/deviceService';

/**
 * True only when native pieces for Expo push exist.
 * Never `require('expo-notifications')` first — that package eagerly loads
 * `expo-application`, which throws `Cannot find native module 'ExpoApplication'`
 * in some Expo Go builds and still paints a redbox even inside try/catch.
 */
function canUseNativeExpoPush(): boolean {
  if (isRunningInExpoGo()) {
    // Expo Go (esp. Android SDK 53+): remote push unsupported; avoid native require.
    return false;
  }
  return requireOptionalNativeModule('ExpoApplication') != null;
}

/**
 * Registers an Expo push token after login when native modules allow it.
 * Otherwise registers a stable demo token so messaging push wiring still works.
 */
export async function registerPushForCurrentUser(): Promise<string | null> {
  const userId = getAuth()?.user?.userId;
  if (!userId) {
    return null;
  }

  let token: string | null = null;
  let platform: 'ios' | 'android' | 'web' | 'unknown' = 'unknown';

  if (canUseNativeExpoPush()) {
    try {
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
      // Native push unavailable — demo token below.
    }
  }

  if (!token) {
    token = `demo-push-${userId}`;
  }

  if (Platform.OS === 'ios') platform = 'ios';
  else if (Platform.OS === 'android') platform = 'android';
  else if (Platform.OS === 'web') platform = 'web';

  try {
    await registerDeviceTokenApi(token, platform);
  } catch {
    // Non-fatal: chat must work even if device-token API fails.
  }
  return token;
}
