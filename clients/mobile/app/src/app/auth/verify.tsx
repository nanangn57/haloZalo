import React, { useEffect, useState } from 'react';
import { View, Text, StyleSheet, ActivityIndicator, TextInput, Pressable } from 'react-native';
import { Link, useLocalSearchParams, useRouter } from 'expo-router';
import { resendVerificationApi } from '@core/authService';
import { verifyAndSaveSession } from '@core/authSession';
import { registerPushForCurrentUser } from '@/lib/pushRegistration';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

// Deep link: halozalo://auth/verify?token=...  (và /auth/verify?token= trên web).
// Verify thành công → server cấp TokenResponse → lưu session → vào tab chat.
export default function VerifyScreen() {
  const router = useRouter();
  const theme = useTheme();
  const params = useLocalSearchParams<{ token?: string | string[] }>();
  const rawToken = Array.isArray(params.token) ? params.token[0] : params.token;
  const token = rawToken?.trim() ?? '';

  const [failure, setFailure] = useState('');
  const [resendEmail, setResendEmail] = useState('');
  const [resendMessage, setResendMessage] = useState('');
  const [resending, setResending] = useState(false);

  useEffect(() => {
    if (!token) {
      return;
    }
    let cancelled = false;
    verifyAndSaveSession(token)
      .then(() => {
        if (cancelled) return;
        void registerPushForCurrentUser().catch(() => undefined);
        router.replace('/(tabs)/chat');
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        setFailure(
          err instanceof Error ? err.message : 'Verification link is invalid or expired',
        );
      });
    return () => {
      cancelled = true;
    };
  }, [token, router]);

  const failed = !token || failure !== '';
  const message = !token
    ? 'Verification link is invalid or expired'
    : failure || 'Verifying your email…';

  const handleResend = async () => {
    if (!resendEmail.trim() || resending) return;
    setResending(true);
    setResendMessage('');
    try {
      await resendVerificationApi(resendEmail);
      setResendMessage('A new verification link was sent. It expires in 2 minutes.');
    } catch (err: unknown) {
      setResendMessage(err instanceof Error ? err.message : 'Could not resend email.');
    } finally {
      setResending(false);
    }
  };

  return (
    <View style={[styles.container, { backgroundColor: theme.backgroundElement }]}>
      <View
        style={[
          styles.card,
          { backgroundColor: theme.background, borderColor: theme.border },
        ]}
      >
        <Text style={[styles.title, { color: theme.text }]}>Email verification</Text>
        <Text
          style={[styles.message, { color: failed ? theme.danger : theme.textSecondary }]}
        >
          {message}
        </Text>
        {!failed ? <ActivityIndicator color={theme.primary} /> : null}

        {failed ? (
          <View style={styles.form}>
            <TextInput
              style={[
                styles.input,
                {
                  borderColor: theme.inputBorder,
                  backgroundColor: theme.inputBg,
                  color: theme.text,
                },
              ]}
              placeholder="Email to send a new link"
              placeholderTextColor={theme.textMuted}
              value={resendEmail}
              onChangeText={setResendEmail}
              keyboardType="email-address"
              autoCapitalize="none"
              autoCorrect={false}
            />
            {resendMessage ? (
              <Text style={{ color: theme.textSecondary }}>{resendMessage}</Text>
            ) : null}
            <Pressable
              style={[
                styles.button,
                { backgroundColor: theme.primary },
                (resending || !resendEmail.trim()) && styles.buttonDisabled,
              ]}
              onPress={() => void handleResend()}
              disabled={resending || !resendEmail.trim()}
            >
              <Text style={styles.buttonText}>
                {resending ? 'Sending…' : 'Resend verification email'}
              </Text>
            </Pressable>
            <Link href="/login" style={[styles.link, { color: theme.primary }]}>
              Back to login
            </Link>
          </View>
        ) : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, justifyContent: 'center', padding: Spacing.four },
  card: {
    padding: Spacing.four,
    borderRadius: Radius.xl,
    borderWidth: 1,
    gap: Spacing.three,
  },
  title: { fontSize: 24, fontWeight: '700', textAlign: 'center', letterSpacing: -0.4 },
  message: { textAlign: 'center', fontSize: 14, lineHeight: 20 },
  form: { gap: Spacing.three },
  input: {
    borderWidth: 1,
    paddingHorizontal: 12,
    paddingVertical: 12,
    borderRadius: Radius.md,
    fontSize: 15,
  },
  button: { paddingVertical: 14, borderRadius: Radius.md, alignItems: 'center' },
  buttonDisabled: { opacity: 0.6 },
  buttonText: { color: '#ffffff', fontWeight: '700', fontSize: 16 },
  link: { textAlign: 'center', fontWeight: '600', fontSize: 14 },
});
