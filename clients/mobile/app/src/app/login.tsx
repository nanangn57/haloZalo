import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
} from 'react-native';
import { useRouter, Link, Redirect } from 'expo-router';
import LoginForm from '../components/auth/LoginForm';
import { loginApi } from '@core/authService';
import { hasSession, saveAuth } from '@core/authSession';
import { registerPushForCurrentUser } from '../lib/pushRegistration';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export default function LoginPage() {
  const router = useRouter();
  const theme = useTheme();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  if (hasSession()) {
    return <Redirect href="/(tabs)/chat" />;
  }

  const handleLogin = async () => {
    setError('');

    if (password.length < 8 || password.length > 72) {
      setError('Password must be between 8 and 72 characters');
      return;
    }

    try {
      setIsLoading(true);
      const auth = await loginApi({
        login: email.trim(),
        password,
      });

      if (!auth.accessToken) {
        throw new Error('Login response does not contain an access token');
      }

      saveAuth(auth);
      void registerPushForCurrentUser().catch(() => undefined);
      router.replace('/(tabs)/chat');
    } catch (err: unknown) {
      setError(
        err instanceof Error
          ? err.message
          : 'Unable to log in. Please try again.',
      );
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      style={[styles.container, { backgroundColor: theme.backgroundElement }]}
    >
      <ScrollView contentContainerStyle={styles.scroll}>
        <View
          style={[
            styles.card,
            {
              backgroundColor: theme.background,
              borderColor: theme.border,
              shadowColor: theme.text,
            },
          ]}
        >
          <View style={[styles.mark, { backgroundColor: theme.primary }]}>
            <Text style={styles.markText}>h</Text>
          </View>
          <Text style={[styles.title, { color: theme.text }]}>Welcome back</Text>
          <Text style={[styles.subtitle, { color: theme.textSecondary }]}>
            Sign in to continue chatting on haloZalo.
          </Text>
          <LoginForm
            email={email}
            password={password}
            error={error}
            onEmailChange={setEmail}
            onPasswordChange={setPassword}
            onSubmit={handleLogin}
            isLoading={isLoading}
          />
          <View style={styles.footer}>
            <Text style={[styles.footerText, { color: theme.textSecondary }]}>
              Don&apos;t have an account?{' '}
            </Text>
            <Link href="/register" style={[styles.link, { color: theme.primary }]}>
              Register
            </Link>
          </View>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  scroll: {
    flexGrow: 1,
    justifyContent: 'center',
    padding: Spacing.four,
  },
  card: {
    padding: Spacing.four,
    borderRadius: Radius.xl,
    borderWidth: 1,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.08,
    shadowRadius: 16,
    elevation: 3,
  },
  mark: {
    alignSelf: 'center',
    width: 48,
    height: 48,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: Spacing.two,
  },
  markText: {
    color: '#ffffff',
    fontSize: 20,
    fontWeight: '700',
  },
  title: {
    fontSize: 26,
    fontWeight: '700',
    textAlign: 'center',
    letterSpacing: -0.4,
    marginBottom: 6,
  },
  subtitle: {
    textAlign: 'center',
    marginBottom: Spacing.four,
    fontSize: 14,
    lineHeight: 20,
  },
  footer: {
    flexDirection: 'row',
    justifyContent: 'center',
    marginTop: Spacing.three,
    flexWrap: 'wrap',
  },
  footerText: { fontSize: 14 },
  link: { fontWeight: '600', fontSize: 14 },
});
