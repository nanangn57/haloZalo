import React, { useCallback, useState } from 'react';
import {
  View,
  Text,
  TextInput,
  Pressable,
  StyleSheet,
  ScrollView,
  ActivityIndicator,
  Switch,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useFocusEffect, useRouter } from 'expo-router';
import { getAccessToken, getAuth, logout } from '@core/authSession';
import { changePasswordApi, listSessionsApi, revokeSessionApi } from '@core/sessionService';
import { socketClient } from '@core/socketClient';
import {
  DEFAULT_NOTIFICATION_PREFS,
  getNotificationPrefsApi,
  updateNotificationPrefsApi,
  type NotificationPrefs,
} from '@core/notificationService';
import type { SessionInfo } from '@core/types/auth';
import { confirmAction } from '@/lib/confirm';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

function formatDate(value?: string): string {
  if (!value) return '—';
  const time = Date.parse(value);
  return Number.isFinite(time) ? new Date(time).toLocaleString() : value;
}

export default function SettingsPage() {
  const router = useRouter();
  const theme = useTheme();
  const user = getAuth()?.user;
  const currentToken = getAccessToken();

  const [sessions, setSessions] = useState<SessionInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [revokingId, setRevokingId] = useState<string | null>(null);
  const [loggingOut, setLoggingOut] = useState(false);

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [revokeOthers, setRevokeOthers] = useState(true);
  const [pwBusy, setPwBusy] = useState(false);
  const [pwError, setPwError] = useState('');
  const [pwMessage, setPwMessage] = useState('');

  const [notifyPrefs, setNotifyPrefs] = useState<NotificationPrefs>(DEFAULT_NOTIFICATION_PREFS);
  const [notifyBusy, setNotifyBusy] = useState(false);
  const [notifyError, setNotifyError] = useState('');
  // true khi server chưa có /me/notifications: lựa chọn chỉ giữ trong phiên này.
  const [notifyLocalOnly, setNotifyLocalOnly] = useState(false);

  const loadNotificationPrefs = useCallback(async () => {
    try {
      const prefs = await getNotificationPrefsApi();
      if (prefs) {
        setNotifyPrefs(prefs);
        setNotifyLocalOnly(false);
      } else {
        setNotifyLocalOnly(true);
      }
      setNotifyError('');
    } catch (err: unknown) {
      setNotifyError(err instanceof Error ? err.message : 'Cannot load notification settings');
    }
  }, []);

  const toggleNotification = async (key: keyof NotificationPrefs, value: boolean) => {
    const previous = notifyPrefs;
    setNotifyPrefs({ ...previous, [key]: value });
    setNotifyBusy(true);
    setNotifyError('');
    try {
      const saved = await updateNotificationPrefsApi({ [key]: value });
      if (saved) {
        setNotifyPrefs(saved);
        setNotifyLocalOnly(false);
      } else {
        setNotifyLocalOnly(true);
      }
    } catch (err: unknown) {
      setNotifyPrefs(previous);
      setNotifyError(err instanceof Error ? err.message : 'Cannot save notification settings');
    } finally {
      setNotifyBusy(false);
    }
  };

  const loadSessions = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setSessions(await listSessionsApi());
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Cannot load sessions');
    } finally {
      setLoading(false);
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      void loadSessions();
      void loadNotificationPrefs();
    }, [loadSessions, loadNotificationPrefs]),
  );

  const doLogout = async () => {
    setLoggingOut(true);
    try {
      socketClient.disconnect();
      await logout(); // gọi server rồi mới xoá token local
    } finally {
      setLoggingOut(false);
      router.replace('/login');
    }
  };

  const handleRevoke = (session: SessionInfo) => {
    const isCurrent = session.sessionId === currentToken;
    confirmAction(
      isCurrent ? 'Sign out' : 'Sign out device',
      isCurrent ? 'Sign out of this device?' : 'Sign out this device remotely?',
      () => {
        void (async () => {
          setRevokingId(session.sessionId);
          setError('');
          try {
            await revokeSessionApi(session.sessionId);
            if (isCurrent) {
              await doLogout();
              return;
            }
            setSessions((previous) =>
              previous.filter((item) => item.sessionId !== session.sessionId),
            );
          } catch (err: unknown) {
            setError(err instanceof Error ? err.message : 'Cannot sign out that session');
          } finally {
            setRevokingId(null);
          }
        })();
      },
      'Sign out',
    );
  };

  const handleChangePassword = async () => {
    setPwError('');
    setPwMessage('');
    if (newPassword.length < 8 || newPassword.length > 72) {
      setPwError('New password must be between 8 and 72 characters');
      return;
    }
    if (newPassword !== confirmPassword) {
      setPwError('Passwords do not match');
      return;
    }
    setPwBusy(true);
    try {
      await changePasswordApi({
        currentPassword,
        newPassword,
        revokeOtherSessions: revokeOthers,
      });
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      setPwMessage('Password changed');
      if (revokeOthers) void loadSessions();
    } catch (err: unknown) {
      setPwError(err instanceof Error ? err.message : 'Cannot change password');
    } finally {
      setPwBusy(false);
    }
  };

  const inputStyle = [
    styles.input,
    { borderColor: theme.inputBorder, backgroundColor: theme.inputBg, color: theme.text },
  ];

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: theme.background }]}>
      <ScrollView contentContainerStyle={styles.scroll}>
        <Text style={[styles.title, { color: theme.text }]}>Settings</Text>
        <Text style={[styles.subtitle, { color: theme.textSecondary }]}>
          {user?.displayName || user?.username} · {user?.email}
        </Text>

        <Pressable
          style={[styles.button, { backgroundColor: theme.danger }, loggingOut && styles.disabled]}
          onPress={() => void doLogout()}
          disabled={loggingOut}
        >
          <Text style={styles.buttonText}>{loggingOut ? 'Signing out…' : 'Log out'}</Text>
        </Pressable>

        <Text style={[styles.section, { color: theme.text }]}>Active sessions</Text>
        {error ? <Text style={{ color: theme.danger, marginBottom: 8 }}>{error}</Text> : null}
        {loading ? <ActivityIndicator color={theme.primary} /> : null}
        {!loading && sessions.length === 0 && !error ? (
          <Text style={{ color: theme.textSecondary }}>No active sessions.</Text>
        ) : null}
        {sessions.map((session) => {
          const isCurrent = session.sessionId === currentToken;
          return (
            <View
              key={session.sessionId}
              style={[
                styles.card,
                { borderColor: theme.border, backgroundColor: theme.backgroundElement },
              ]}
            >
              <View style={{ flex: 1 }}>
                <Text style={{ color: theme.text, fontWeight: '600' }} numberOfLines={1}>
                  {session.deviceLabel || session.userAgent || 'Unknown device'}
                  {isCurrent ? '  · This device' : ''}
                </Text>
                <Text style={{ color: theme.textSecondary, fontSize: 12, marginTop: 2 }}>
                  {session.deviceId ? `Device ${session.deviceId} · ` : ''}
                  Signed in {formatDate(session.createdAt)}
                  {session.lastSeenAt ? ` · last active ${formatDate(session.lastSeenAt)}` : ''}
                </Text>
              </View>
              <Pressable
                style={[styles.smallButton, { backgroundColor: theme.danger }]}
                disabled={revokingId !== null}
                onPress={() => handleRevoke(session)}
              >
                <Text style={styles.buttonText}>
                  {revokingId === session.sessionId ? '…' : 'Sign out'}
                </Text>
              </Pressable>
            </View>
          );
        })}

        <Text style={[styles.section, { color: theme.text }]}>Change password</Text>
        <View style={styles.form}>
          <TextInput
            style={inputStyle}
            placeholder="Current password"
            placeholderTextColor={theme.textMuted}
            value={currentPassword}
            onChangeText={setCurrentPassword}
            secureTextEntry
          />
          <TextInput
            style={inputStyle}
            placeholder="New password"
            placeholderTextColor={theme.textMuted}
            value={newPassword}
            onChangeText={setNewPassword}
            secureTextEntry
          />
          <TextInput
            style={inputStyle}
            placeholder="Confirm new password"
            placeholderTextColor={theme.textMuted}
            value={confirmPassword}
            onChangeText={setConfirmPassword}
            secureTextEntry
          />
          <View style={styles.switchRow}>
            <Text style={{ color: theme.text, flex: 1 }}>Sign out my other devices</Text>
            <Switch value={revokeOthers} onValueChange={setRevokeOthers} />
          </View>
          {pwError ? <Text style={{ color: theme.danger }}>{pwError}</Text> : null}
          {pwMessage ? <Text style={{ color: theme.success }}>{pwMessage}</Text> : null}
          <Pressable
            style={[
              styles.button,
              { backgroundColor: theme.primary },
              (pwBusy || !currentPassword || !newPassword) && styles.disabled,
            ]}
            onPress={() => void handleChangePassword()}
            disabled={pwBusy || !currentPassword || !newPassword}
          >
            <Text style={styles.buttonText}>{pwBusy ? 'Saving…' : 'Change password'}</Text>
          </Pressable>
        </View>

        <Text style={[styles.section, { color: theme.text }]}>Notifications</Text>
        <View style={styles.switchRow}>
          <Text style={{ color: theme.text, flex: 1 }}>Push notifications</Text>
          <Switch
            value={notifyPrefs.pushEnabled}
            disabled={notifyBusy}
            onValueChange={(value) => void toggleNotification('pushEnabled', value)}
          />
        </View>
        <View style={[styles.switchRow, { marginTop: Spacing.two }]}>
          <Text style={{ color: theme.text, flex: 1 }}>Show message preview</Text>
          <Switch
            value={notifyPrefs.previewEnabled}
            disabled={notifyBusy || !notifyPrefs.pushEnabled}
            onValueChange={(value) => void toggleNotification('previewEnabled', value)}
          />
        </View>
        {notifyError ? <Text style={{ color: theme.danger, marginTop: 4 }}>{notifyError}</Text> : null}
        {notifyLocalOnly ? (
          <Text style={{ color: theme.textSecondary, fontSize: 12, marginTop: 4 }}>
            Server does not support notification settings yet. Changes apply to this session only.
          </Text>
        ) : null}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  scroll: { padding: Spacing.three, paddingBottom: Spacing.six },
  title: { fontSize: 24, fontWeight: '700', letterSpacing: -0.4, marginBottom: 6 },
  subtitle: { fontSize: 14, marginBottom: Spacing.three },
  section: { fontSize: 16, fontWeight: '700', marginTop: Spacing.four, marginBottom: Spacing.two },
  form: { gap: Spacing.two },
  input: {
    borderWidth: 1,
    paddingHorizontal: 12,
    paddingVertical: 12,
    borderRadius: Radius.md,
    fontSize: 15,
  },
  button: { paddingVertical: 14, borderRadius: Radius.md, alignItems: 'center' },
  smallButton: { paddingHorizontal: 12, paddingVertical: 8, borderRadius: Radius.md },
  disabled: { opacity: 0.6 },
  buttonText: { color: '#ffffff', fontWeight: '700' },
  card: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    padding: Spacing.three,
    borderRadius: Radius.lg,
    borderWidth: 1,
    marginBottom: Spacing.two,
  },
  switchRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
});
