import React, { useEffect, useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ActivityIndicator,
  ScrollView,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import { getAuth } from '@core/authSession';
import { getMyActivityApi, type MyActivity } from '@core/statsService';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export default function DashboardPage() {
  const router = useRouter();
  const theme = useTheme();
  const auth = getAuth();
  const [mine, setMine] = useState<MyActivity | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!auth?.accessToken) {
      router.replace('/login');
      return;
    }

    void getMyActivityApi()
      .then((activity) => {
        setMine(activity);
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : 'Failed to load activity');
      })
      .finally(() => setLoading(false));
  }, [auth?.accessToken, router]);

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: theme.background }]}>
      <ScrollView contentContainerStyle={styles.scroll}>
        <Text style={[styles.title, { color: theme.text }]}>Your activity</Text>
        <Text style={[styles.subtitle, { color: theme.textSecondary }]}>
          Personal stats for your account.
        </Text>
        {loading ? <ActivityIndicator color={theme.primary} /> : null}
        {error ? (
          <Text style={[styles.error, { color: theme.danger }]}>{error}</Text>
        ) : null}
        {mine ? (
          <View style={styles.grid}>
            <StatCard label="Logins" value={mine.logins} />
            <StatCard label="Messages" value={mine.messagesSent} />
            <StatCard label="Groups" value={mine.groupsCreated} />
          </View>
        ) : null}
      </ScrollView>
    </SafeAreaView>
  );
}

function StatCard({ label, value }: { label: string; value: number }) {
  const theme = useTheme();
  return (
    <View
      style={[
        styles.card,
        {
          backgroundColor: theme.backgroundElement,
          borderColor: theme.border,
        },
      ]}
    >
      <Text style={[styles.label, { color: theme.textSecondary }]}>{label}</Text>
      <Text style={[styles.value, { color: theme.text }]}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  scroll: { padding: Spacing.three, paddingBottom: Spacing.five },
  title: {
    fontSize: 24,
    fontWeight: '700',
    letterSpacing: -0.4,
    marginBottom: 6,
  },
  subtitle: { fontSize: 14, marginBottom: Spacing.three, lineHeight: 20 },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: 12 },
  card: {
    width: '47%',
    borderRadius: Radius.lg,
    padding: Spacing.three,
    borderWidth: 1,
  },
  label: { marginBottom: 8, fontSize: 13, fontWeight: '500' },
  value: { fontSize: 28, fontWeight: '700', letterSpacing: -0.5 },
  error: { marginBottom: 12 },
});
