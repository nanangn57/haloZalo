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
import { getAuth, isPlatformAdmin } from '@core/authSession';
import {
  getActivityStatsApi,
  getDashboardStatsApi,
  type DashboardStats,
} from '@core/statsService';
import { getGroupsApi } from '@core/groupService';
import type { ActivityStats } from '@core/types/stat';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

function groupLabel(groupId: string, namesById: Record<string, string>): string {
  const named = namesById[groupId]?.trim();
  if (named) {
    return named;
  }
  return `${groupId.slice(0, 8)}…`;
}

export default function AdminStatsPage() {
  const router = useRouter();
  const theme = useTheme();
  const auth = getAuth();
  const [stats, setStats] = useState<ActivityStats | null>(null);
  const [dashboard, setDashboard] = useState<DashboardStats | null>(null);
  const [groupNamesById, setGroupNamesById] = useState<Record<string, string>>({});
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!auth?.accessToken) {
      router.replace('/login');
      return;
    }
    if (!isPlatformAdmin()) {
      router.replace('/dashboard');
      return;
    }

    void Promise.all([
      getActivityStatsApi(),
      getDashboardStatsApi(),
      getGroupsApi().catch(() => []),
    ])
      .then(([totals, board, groups]) => {
        setStats(totals);
        setDashboard(board);
        const names: Record<string, string> = {};
        groups.forEach((group) => {
          const name = group.name?.trim();
          if (name) {
            names[group.groupId] = name;
          }
        });
        setGroupNamesById(names);
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : 'Failed to load admin stats');
      })
      .finally(() => setLoading(false));
  }, [auth?.accessToken, router]);

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: theme.background }]}>
      <ScrollView contentContainerStyle={styles.scroll}>
        <Text style={[styles.title, { color: theme.text }]}>Platform stats</Text>
        <Text style={[styles.subtitle, { color: theme.textSecondary }]}>
          Admin-only aggregates across the whole platform.
        </Text>
        {loading ? <ActivityIndicator color={theme.primary} /> : null}
        {error ? (
          <Text style={[styles.error, { color: theme.danger }]}>{error}</Text>
        ) : null}
        {stats ? (
          <View style={styles.grid}>
            <StatCard label="Logins" value={stats.logins} />
            <StatCard label="Groups" value={stats.groupsCreated} />
            <StatCard label="Messages" value={stats.messagesCreated} />
            <StatCard label="Media" value={stats.mediaUploads} />
          </View>
        ) : null}

        {dashboard ? (
          <View
            style={[
              styles.section,
              {
                borderColor: theme.border,
                backgroundColor: theme.backgroundElement,
              },
            ]}
          >
            <Text style={[styles.sectionTitle, { color: theme.text }]}>Active users</Text>
            <Text style={[styles.big, { color: theme.primary }]}>
              {dashboard.activeUsers}
            </Text>
            <Text style={[styles.sectionTitle, { color: theme.text }]}>
              Messages by day
            </Text>
            {dashboard.messagesByDay.length === 0 ? (
              <Text style={[styles.muted, { color: theme.textMuted }]}>No data yet</Text>
            ) : (
              dashboard.messagesByDay.map((day) => (
                <Text
                  key={day.day}
                  style={[styles.line, { color: theme.textSecondary }]}
                >
                  {day.day}: {day.total}
                </Text>
              ))
            )}
            <Text style={[styles.sectionTitle, { color: theme.text }]}>Top groups</Text>
            {dashboard.topGroups.length === 0 ? (
              <Text style={[styles.muted, { color: theme.textMuted }]}>No data yet</Text>
            ) : (
              dashboard.topGroups.map((group) => (
                <Text
                  key={group.groupId}
                  style={[styles.line, { color: theme.textSecondary }]}
                >
                  {groupLabel(group.groupId, groupNamesById)} · {group.total}
                </Text>
              ))
            )}
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
  section: {
    marginTop: Spacing.three,
    padding: 14,
    borderRadius: Radius.lg,
    borderWidth: 1,
  },
  sectionTitle: {
    fontWeight: '700',
    marginTop: 8,
    marginBottom: 6,
    fontSize: 15,
  },
  line: { marginBottom: 4 },
  big: { fontSize: 32, fontWeight: '700', marginBottom: 8, letterSpacing: -0.5 },
  muted: { fontSize: 13 },
});
