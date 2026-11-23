import React, { useCallback, useEffect, useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ActivityIndicator,
  ScrollView,
  TextInput,
  Pressable,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRouter } from 'expo-router';
import { getAuth, isPlatformAdmin } from '@core/authSession';
import {
  getActivityStatsApi,
  getAdminUserSummaryApi,
  getDashboardStatsApi,
  rangePreset,
  type AdminUserSummary,
  type DashboardStats,
  type DateRange,
} from '@core/statsService';
import { getGroupsApi } from '@core/groupService';
import { banUserApi, unbanUserApi } from '@core/friendService';
import { findUserApi } from '@core/userService';
import type { ActivityStats } from '@core/types/stat';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { confirm } from '@/lib/confirm';

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
  const [range, setRange] = useState<DateRange>(() => rangePreset('7d'));
  const [preset, setPreset] = useState<'today' | '7d' | '30d' | 'all'>('7d');
  const [banQuery, setBanQuery] = useState('');
  const [banBusy, setBanBusy] = useState(false);
  const [banMessage, setBanMessage] = useState('');
  const [userSummary, setUserSummary] = useState<AdminUserSummary | null>(null);

  const load = useCallback(() => {
    if (!auth?.accessToken) {
      router.replace('/login');
      return;
    }
    if (!isPlatformAdmin()) {
      router.replace('/dashboard');
      return;
    }

    setLoading(true);
    void Promise.all([
      getActivityStatsApi(range),
      getDashboardStatsApi(range),
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
        setError('');
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : 'Failed to load admin stats');
      })
      .finally(() => setLoading(false));
  }, [auth?.accessToken, range, router]);

  useEffect(() => {
    load();
  }, [load]);

  async function runBan(action: 'ban' | 'unban') {
    const q = banQuery.trim();
    if (!q) return;
    setBanBusy(true);
    setBanMessage('');
    try {
      const user = await findUserApi(q);
      try {
        setUserSummary(await getAdminUserSummaryApi(user.userId));
      } catch {
        setUserSummary(null);
      }
      const ok = await confirm(
        action === 'ban' ? 'Ban user' : 'Unban user',
        `${action === 'ban' ? 'Ban' : 'Unban'} ${user.displayName || user.username || user.email}?`,
      );
      if (!ok) return;
      if (action === 'ban') {
        await banUserApi(user.userId);
        setBanMessage(`Banned ${user.email}`);
      } else {
        await unbanUserApi(user.userId);
        setBanMessage(`Unbanned ${user.email}`);
      }
    } catch (err: unknown) {
      setBanMessage(err instanceof Error ? err.message : 'Ban action failed');
    } finally {
      setBanBusy(false);
    }
  }

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: theme.background }]}>
      <ScrollView contentContainerStyle={styles.scroll}>
        <Text style={[styles.title, { color: theme.text }]}>Platform stats</Text>
        <Text style={[styles.subtitle, { color: theme.textSecondary }]}>
          Admin-only aggregates across the whole platform.
        </Text>
        <View style={styles.presetRow}>
          {([
            ['today', 'Today'],
            ['7d', '7d'],
            ['30d', '30d'],
            ['all', 'All'],
          ] as const).map(([key, label]) => (
            <Pressable
              key={key}
              style={[
                styles.preset,
                {
                  backgroundColor: preset === key ? theme.primary : theme.backgroundElement,
                  borderColor: theme.border,
                },
              ]}
              onPress={() => {
                setPreset(key);
                setRange(key === 'all' ? {} : rangePreset(key));
              }}
            >
              <Text
                style={{
                  color: preset === key ? '#fff' : theme.text,
                  fontWeight: '600',
                  fontSize: 13,
                }}
              >
                {label}
              </Text>
            </Pressable>
          ))}
        </View>
        <View
          style={[
            styles.section,
            { borderColor: theme.border, backgroundColor: theme.backgroundElement },
          ]}
        >
          <Text style={[styles.sectionTitle, { color: theme.text }]}>Ban / unban</Text>
          <TextInput
            style={[styles.input, { borderColor: theme.border, color: theme.text }]}
            placeholder="Email or username"
            placeholderTextColor={theme.textMuted}
            value={banQuery}
            onChangeText={setBanQuery}
            autoCapitalize="none"
          />
          <View style={styles.banRow}>
            <Pressable
              style={[styles.btn, { backgroundColor: theme.danger, opacity: banBusy ? 0.6 : 1 }]}
              disabled={banBusy}
              onPress={() => void runBan('ban')}
            >
              <Text style={styles.btnText}>Ban</Text>
            </Pressable>
            <Pressable
              style={[styles.btn, { backgroundColor: theme.primary, opacity: banBusy ? 0.6 : 1 }]}
              disabled={banBusy}
              onPress={() => void runBan('unban')}
            >
              <Text style={styles.btnText}>Unban</Text>
            </Pressable>
          </View>
          {userSummary ? (
            <Text style={[styles.muted, { color: theme.textSecondary }]}>
              Summary — sent {userSummary.activity.messagesSent}, received{' '}
              {userSummary.activity.messagesReceived}, friends{' '}
              {userSummary.activity.friends.count}
            </Text>
          ) : null}
          {banMessage ? (
            <Text style={[styles.muted, { color: theme.textSecondary }]}>{banMessage}</Text>
          ) : null}
        </View>
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
            <Text style={[styles.sectionTitle, { color: theme.text }]}>
              DAU / WAU / MAU
            </Text>
            <Text style={[styles.big, { color: theme.primary }]}>
              {dashboard.dau} / {dashboard.wau} / {dashboard.mau}
            </Text>
            <Text style={[styles.line, { color: theme.textSecondary }]}>
              Active lifetime {dashboard.activeUsers} · msgs/active{' '}
              {dashboard.messagesPerActiveUser.toFixed(1)} · messengers{' '}
              {Math.round(dashboard.activeMessengersRate * 100)}%
            </Text>
            <Text style={[styles.sectionTitle, { color: theme.text }]}>Funnel</Text>
            <Text style={[styles.line, { color: theme.textSecondary }]}>
              Registered {dashboard.verifyFunnel.registered} · verified{' '}
              {dashboard.verifyFunnel.verified} · first msg{' '}
              {dashboard.verifyFunnel.firstMessage}
            </Text>
            <Text style={[styles.sectionTitle, { color: theme.text }]}>Social</Text>
            <Text style={[styles.line, { color: theme.textSecondary }]}>
              Friend requests {dashboard.friendRequests} · accept{' '}
              {Math.round(dashboard.friendAcceptRate * 100)}% · groups{' '}
              {dashboard.groupsCreated} · avg members{' '}
              {dashboard.avgMembersPerGroup.toFixed(1)}
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
            <Text style={[styles.sectionTitle, { color: theme.text }]}>Media by day</Text>
            {dashboard.mediaByDay.length === 0 ? (
              <Text style={[styles.muted, { color: theme.textMuted }]}>No media yet</Text>
            ) : (
              dashboard.mediaByDay.map((day) => (
                <Text
                  key={`${day.day}-${day.type}`}
                  style={[styles.line, { color: theme.textSecondary }]}
                >
                  {day.day} {day.type}: {day.total}
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
  presetRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: Spacing.three },
  preset: {
    borderWidth: 1,
    borderRadius: 999,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  banRow: { flexDirection: 'row', gap: 8, marginTop: 8 },
  input: {
    borderWidth: 1,
    borderRadius: Radius.md,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 14,
    marginBottom: 8,
  },
  btn: {
    borderRadius: Radius.md,
    paddingHorizontal: 14,
    paddingVertical: 10,
    alignSelf: 'flex-start',
  },
  btnText: { color: '#fff', fontWeight: '600', fontSize: 14 },
});
