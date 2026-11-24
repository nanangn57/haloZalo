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
import { Ionicons } from '@expo/vector-icons';
import { getAuth, isPlatformAdmin } from '@core/authSession';
import {
  getActivityStatsApi,
  getAdminUserSummaryApi,
  getDashboardStatsApi,
  rangePreset,
  type AdminUserSummary,
  type DashboardStats,
  type DateRange,
  type MyActivity,
} from '@core/statsService';
import { getGroupsApi } from '@core/groupService';
import { banUserApi, unbanUserApi } from '@core/friendService';
import { findUserApi } from '@core/userService';
import type { AuthUser } from '@core/types/auth';
import type { ActivityStats } from '@core/types/stat';
import {
  ChatHeatmap,
  MediaByDayStack,
  SimpleDayChart,
} from '@/components/stats/StatsCharts';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { confirm } from '@/lib/confirm';

const KPI = [
  {
    key: 'logins' as const,
    label: 'Total Logins',
    description: 'Successful sign-ins',
    icon: 'log-in-outline' as const,
    color: '#0b6bcb',
  },
  {
    key: 'groupsCreated' as const,
    label: 'Groups Created',
    description: 'New groups established',
    icon: 'people-outline' as const,
    color: '#0b9edb',
  },
  {
    key: 'messagesCreated' as const,
    label: 'Messages Created',
    description: 'Messages sent',
    icon: 'chatbubbles-outline' as const,
    color: '#22a06b',
  },
  {
    key: 'mediaUploads' as const,
    label: 'Media Uploads',
    description: 'Files and images uploaded',
    icon: 'cloud-upload-outline' as const,
    color: '#d9480f',
  },
];

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
  const [banTarget, setBanTarget] = useState<AuthUser | null>(null);
  const [banBusy, setBanBusy] = useState(false);
  const [banMessage, setBanMessage] = useState('');
  const [banError, setBanError] = useState('');
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

  async function handleFind() {
    const q = banQuery.trim();
    if (!q) return;
    setBanBusy(true);
    setBanError('');
    setBanMessage('');
    setUserSummary(null);
    try {
      const user = await findUserApi(q);
      setBanTarget(user);
      try {
        setUserSummary(await getAdminUserSummaryApi(user.userId));
      } catch {
        setUserSummary(null);
      }
    } catch (err: unknown) {
      setBanTarget(null);
      setBanError(err instanceof Error ? err.message : 'Cannot find user');
    } finally {
      setBanBusy(false);
    }
  }

  async function runBan(action: 'ban' | 'unban') {
    if (!banTarget) return;
    const name = banTarget.displayName || banTarget.username;
    const ok = await confirm(
      action === 'ban' ? 'Ban user' : 'Unban user',
      action === 'ban'
        ? `Ban ${name}? They will be signed out and cannot log in.`
        : `Unban ${name}?`,
    );
    if (!ok) return;
    setBanBusy(true);
    setBanError('');
    setBanMessage('');
    try {
      if (action === 'ban') {
        await banUserApi(banTarget.userId);
        setBanMessage(`${name} was banned.`);
      } else {
        await unbanUserApi(banTarget.userId);
        setBanMessage(`${name} was unbanned.`);
      }
    } catch (err: unknown) {
      setBanError(err instanceof Error ? err.message : 'Action failed');
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
            styles.panel,
            { borderColor: theme.border, backgroundColor: theme.backgroundElement },
          ]}
        >
          <Text style={[styles.panelTitle, { color: theme.text }]}>User moderation</Text>
          <Text style={[styles.panelSubtitle, { color: theme.textSecondary }]}>
            Look up a user, then ban or unban.
          </Text>
          <View style={styles.findRow}>
            <TextInput
              style={[
                styles.input,
                { flex: 1, borderColor: theme.border, color: theme.text },
              ]}
              placeholder="Username or email"
              placeholderTextColor={theme.textMuted}
              value={banQuery}
              onChangeText={setBanQuery}
              autoCapitalize="none"
            />
            <Pressable
              style={[
                styles.btn,
                { backgroundColor: theme.primary, opacity: banBusy || !banQuery.trim() ? 0.6 : 1 },
              ]}
              disabled={banBusy || !banQuery.trim()}
              onPress={() => void handleFind()}
            >
              <Text style={styles.btnText}>Find</Text>
            </Pressable>
          </View>

          {banTarget ? (
            <View style={styles.targetRow}>
              <View style={{ flex: 1 }}>
                <Text style={[styles.targetName, { color: theme.text }]} numberOfLines={1}>
                  {banTarget.displayName || banTarget.username}
                </Text>
                <Text style={[styles.muted, { color: theme.textMuted }]} numberOfLines={1}>
                  {banTarget.email}
                </Text>
              </View>
              <Pressable
                style={[styles.btnOutline, { borderColor: theme.danger }]}
                disabled={banBusy}
                onPress={() => void runBan('ban')}
              >
                <Text style={{ color: theme.danger, fontWeight: '600', fontSize: 13 }}>Ban</Text>
              </Pressable>
              <Pressable
                style={[styles.btnOutline, { borderColor: theme.border }]}
                disabled={banBusy}
                onPress={() => void runBan('unban')}
              >
                <Text style={{ color: theme.text, fontWeight: '600', fontSize: 13 }}>Unban</Text>
              </Pressable>
            </View>
          ) : null}

          {userSummary ? <UserActivitySummary activity={userSummary.activity} /> : null}
          {banError ? (
            <Text style={[styles.feedback, { color: theme.danger }]}>{banError}</Text>
          ) : null}
          {banMessage ? (
            <Text style={[styles.feedback, { color: theme.success }]}>{banMessage}</Text>
          ) : null}
        </View>

        {loading ? <ActivityIndicator color={theme.primary} style={{ marginVertical: 16 }} /> : null}
        {error ? <Text style={[styles.error, { color: theme.danger }]}>{error}</Text> : null}

        {!loading && !error && stats && dashboard ? (
          <>
            <View style={styles.grid}>
              {KPI.map((metric) => (
                <MetricCard
                  key={metric.key}
                  label={metric.label}
                  value={stats[metric.key].toLocaleString()}
                  description={metric.description}
                  icon={metric.icon}
                  color={metric.color}
                />
              ))}
              <MetricCard
                label="DAU / WAU / MAU"
                value={`${dashboard.dau} / ${dashboard.wau} / ${dashboard.mau}`}
                description="Active users anchored to range end"
                icon="people-outline"
                color="#22a06b"
              />
              <MetricCard
                label="Engagement"
                value={dashboard.messagesPerActiveUser.toFixed(1)}
                description={`Msgs / active · ${Math.round(dashboard.activeMessengersRate * 100)}% messengers`}
                icon="trending-up-outline"
                color="#d9480f"
              />
            </View>

            <View style={styles.splitGrid}>
              <View
                style={[
                  styles.panel,
                  styles.splitPanel,
                  { borderColor: theme.border, backgroundColor: theme.backgroundElement },
                ]}
              >
                <Text style={[styles.panelTitle, { color: theme.text }]}>New users</Text>
                <Text style={[styles.panelSubtitle, { color: theme.textSecondary, marginBottom: 12 }]}>
                  Registrations by day.
                </Text>
                <SimpleDayChart
                  days={dashboard.newUsersByDay}
                  emptyMessage="No new users in this period."
                />
              </View>
              <View
                style={[
                  styles.panel,
                  styles.splitPanel,
                  { borderColor: theme.border, backgroundColor: theme.backgroundElement },
                ]}
              >
                <Text style={[styles.panelTitle, { color: theme.text }]}>Verify funnel</Text>
                <Text style={[styles.panelSubtitle, { color: theme.textSecondary, marginBottom: 12 }]}>
                  Cohort by registration day → verified → sent a message.
                </Text>
                <ListRow label="Registered" value={dashboard.verifyFunnel.registered} />
                <ListRow label="Verified" value={dashboard.verifyFunnel.verified} />
                <ListRow label="First message" value={dashboard.verifyFunnel.firstMessage} />
              </View>
            </View>

            <View style={styles.splitGrid}>
              <View
                style={[
                  styles.panel,
                  styles.splitPanel,
                  { borderColor: theme.border, backgroundColor: theme.backgroundElement },
                ]}
              >
                <Text style={[styles.panelTitle, { color: theme.text }]}>Social</Text>
                <Text style={[styles.panelSubtitle, { color: theme.textSecondary, marginBottom: 12 }]}>
                  Friend requests in range.
                </Text>
                <ListRow label="Requests" value={dashboard.friendRequests} />
                <ListRow
                  label="Accept rate"
                  value={`${Math.round(dashboard.friendAcceptRate * 100)}%`}
                />
                <ListRow label="Groups created" value={dashboard.groupsCreated} />
                <ListRow
                  label="Avg members / group"
                  value={dashboard.avgMembersPerGroup.toFixed(1)}
                />
              </View>
              <View
                style={[
                  styles.panel,
                  styles.splitPanel,
                  { borderColor: theme.border, backgroundColor: theme.backgroundElement },
                ]}
              >
                <Text style={[styles.panelTitle, { color: theme.text }]}>Top groups</Text>
                <Text style={[styles.panelSubtitle, { color: theme.textSecondary, marginBottom: 12 }]}>
                  Lifetime by message volume.
                </Text>
                {dashboard.topGroups.length === 0 ? (
                  <Text style={{ color: theme.textMuted }}>No group traffic yet.</Text>
                ) : (
                  dashboard.topGroups.map((group) => (
                    <ListRow
                      key={group.groupId}
                      label={groupLabel(group.groupId, groupNamesById)}
                      value={group.total}
                    />
                  ))
                )}
              </View>
            </View>

            <View
              style={[
                styles.panel,
                { borderColor: theme.border, backgroundColor: theme.backgroundElement },
              ]}
            >
              <Text style={[styles.panelTitle, { color: theme.text }]}>Messages by day</Text>
              <Text style={[styles.panelSubtitle, { color: theme.textSecondary, marginBottom: 12 }]}>
                Platform-wide outbound messages.
              </Text>
              <SimpleDayChart
                days={dashboard.messagesByDay}
                emptyMessage="No messages in this period."
              />
            </View>

            <View
              style={[
                styles.panel,
                { borderColor: theme.border, backgroundColor: theme.backgroundElement },
              ]}
            >
              <Text style={[styles.panelTitle, { color: theme.text }]}>Media uploads</Text>
              <Text style={[styles.panelSubtitle, { color: theme.textSecondary, marginBottom: 12 }]}>
                By day and type.
              </Text>
              <MediaByDayStack days={dashboard.mediaByDay} />
            </View>

            <View
              style={[
                styles.panel,
                { borderColor: theme.border, backgroundColor: theme.backgroundElement },
              ]}
            >
              <Text style={[styles.panelTitle, { color: theme.text }]}>
                When the platform chats
              </Text>
              <Text style={[styles.panelSubtitle, { color: theme.textSecondary, marginBottom: 12 }]}>
                Hour × weekday heatmap (UTC) — all users.
              </Text>
              <ChatHeatmap cells={dashboard.heatmap} />
            </View>
          </>
        ) : null}
      </ScrollView>
    </SafeAreaView>
  );
}

function MetricCard({
  label,
  value,
  description,
  icon,
  color,
}: {
  label: string
  value: string
  description: string
  icon: keyof typeof Ionicons.glyphMap
  color: string
}) {
  const theme = useTheme();
  return (
    <View
      style={[
        styles.metric,
        { backgroundColor: theme.backgroundElement, borderColor: theme.border },
      ]}
    >
      <View style={[styles.metricIcon, { backgroundColor: color + '22' }]}>
        <Ionicons name={icon} size={18} color={color} />
      </View>
      <View style={styles.metricBody}>
        <Text style={[styles.metricLabel, { color: theme.textSecondary }]} numberOfLines={1}>
          {label}
        </Text>
        <Text style={[styles.metricValue, { color: theme.text }]} numberOfLines={1}>
          {value}
        </Text>
        <Text style={[styles.metricDesc, { color: theme.textMuted }]} numberOfLines={2}>
          {description}
        </Text>
      </View>
    </View>
  );
}

function ListRow({ label, value }: { label: string; value: string | number }) {
  const theme = useTheme();
  return (
    <View style={styles.listRow}>
      <Text style={{ color: theme.textSecondary, flex: 1 }} numberOfLines={1}>
        {label}
      </Text>
      <Text style={{ color: theme.text, fontWeight: '700' }}>{value}</Text>
    </View>
  );
}

function UserActivitySummary({ activity }: { activity: MyActivity }) {
  const theme = useTheme();
  return (
    <View style={[styles.summary, { borderTopColor: theme.border }]}>
      <Text style={[styles.panelTitle, { color: theme.text, fontSize: 14 }]}>
        Activity summary
      </Text>
      <Text style={[styles.muted, { color: theme.textSecondary }]}>
        Sent {activity.messagesSent} · Received {activity.messagesReceived} · Logins{' '}
        {activity.logins} · Groups {activity.groupsCreated} · Friends {activity.friends.count}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  scroll: { padding: Spacing.three, paddingBottom: Spacing.six, gap: Spacing.three },
  title: {
    fontSize: 28,
    fontWeight: '700',
    letterSpacing: -0.6,
    marginBottom: 6,
  },
  subtitle: { fontSize: 14, marginBottom: Spacing.two, lineHeight: 20 },
  presetRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  preset: {
    borderWidth: 1,
    borderRadius: 999,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  panel: {
    padding: 14,
    borderRadius: Radius.lg,
    borderWidth: 1,
  },
  panelTitle: {
    fontWeight: '700',
    fontSize: 16,
    letterSpacing: -0.2,
    marginBottom: 4,
  },
  panelSubtitle: { fontSize: 13, lineHeight: 18 },
  findRow: { flexDirection: 'row', gap: 8, alignItems: 'center', marginTop: 10 },
  input: {
    borderWidth: 1,
    borderRadius: Radius.md,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 14,
  },
  btn: {
    borderRadius: Radius.md,
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  btnText: { color: '#fff', fontWeight: '600', fontSize: 14 },
  btnOutline: {
    borderWidth: 1,
    borderRadius: Radius.md,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  targetRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginTop: 12,
  },
  targetName: { fontWeight: '600', fontSize: 15 },
  muted: { fontSize: 13 },
  feedback: { marginTop: 10, fontSize: 13, fontWeight: '500' },
  summary: {
    marginTop: 12,
    paddingTop: 12,
    borderTopWidth: StyleSheet.hairlineWidth,
    gap: 4,
  },
  error: { marginBottom: 4 },
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
    justifyContent: 'space-between',
  },
  metric: {
    width: '48%',
    maxWidth: '48%',
    borderRadius: Radius.lg,
    padding: Spacing.three,
    borderWidth: 1,
    flexDirection: 'row',
    gap: 10,
  },
  metricIcon: {
    width: 36,
    height: 36,
    borderRadius: Radius.md,
    alignItems: 'center',
    justifyContent: 'center',
  },
  metricBody: { flex: 1, minWidth: 0 },
  metricLabel: { fontSize: 12, fontWeight: '500', marginBottom: 4 },
  metricValue: { fontSize: 20, fontWeight: '700', letterSpacing: -0.4 },
  metricDesc: { fontSize: 12, marginTop: 4, lineHeight: 16 },
  splitGrid: { gap: 12 },
  splitPanel: { width: '100%' },
  listRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 8,
    gap: 12,
  },
});
