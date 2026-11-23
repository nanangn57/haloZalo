import React, { useEffect, useState } from 'react';
import {
  View,
  Text,
  Pressable,
  StyleSheet,
  ActivityIndicator,
  ScrollView,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { getAuth } from '@core/authSession';
import {
  emptyMyActivity,
  getMyActivityApi,
  rangePreset,
  type DateRange,
  type MyActivity,
} from '@core/statsService';
import {
  ChatHeatmap,
  DirectVsGroup,
  MediaVsText,
  MessagesOverTime,
} from '@/components/stats/StatsCharts';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

type Preset = 'today' | '7d' | '30d' | 'all';

type IconName = React.ComponentProps<typeof Ionicons>['name'];

const PRIMARY_KPIS: {
  key: 'messagesSent' | 'messagesReceived' | 'activeConversations';
  label: string;
  description: string;
  icon: IconName;
  color: string;
  drill?: boolean;
}[] = [
  {
    key: 'messagesSent',
    label: 'Messages sent',
    description: 'Text and media you sent',
    icon: 'chatbubbles-outline',
    color: '#15803d',
    drill: true,
  },
  {
    key: 'messagesReceived',
    label: 'Messages received',
    description: 'Messages addressed to you',
    icon: 'mail-open-outline',
    color: '#0284c7',
  },
  {
    key: 'activeConversations',
    label: 'Active chats',
    description: 'Conversations you messaged in',
    icon: 'flash-outline',
    color: '#d97706',
  },
];

const SECONDARY_KPIS: {
  key: 'logins' | 'groupsCreated';
  label: string;
  description: string;
  icon: IconName;
  color: string;
}[] = [
  {
    key: 'logins',
    label: 'Logins',
    description: 'Successful sign-ins',
    icon: 'log-in-outline',
    color: '#0068ff',
  },
  {
    key: 'groupsCreated',
    label: 'Groups',
    description: 'Groups you own',
    icon: 'people-outline',
    color: '#0284c7',
  },
];

export default function DashboardPage() {
  const router = useRouter();
  const theme = useTheme();
  const auth = getAuth();
  const [mine, setMine] = useState<MyActivity>(emptyMyActivity());
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [preset, setPreset] = useState<Preset>('7d');
  const [range, setRange] = useState<DateRange>(() => rangePreset('7d'));
  const [showDrill, setShowDrill] = useState(false);

  function applyPreset(next: Preset) {
    setPreset(next);
    setLoading(true);
    setError('');
    setRange(next === 'all' ? {} : rangePreset(next));
  }

  useEffect(() => {
    if (!auth?.accessToken) {
      router.replace('/login');
      return;
    }

    void getMyActivityApi(range)
      .then((activity) => {
        setMine(activity);
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : 'Unable to load activity data');
      })
      .finally(() => setLoading(false));
  }, [auth?.accessToken, router, range]);

  const hasActivity =
    mine.messagesSent > 0 ||
    mine.messagesReceived > 0 ||
    mine.logins > 0 ||
    mine.groupsCreated > 0;

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: theme.background }]}>
      <ScrollView contentContainerStyle={styles.scroll}>
        <Text style={[styles.title, { color: theme.text }]}>Your activity</Text>
        <Text style={[styles.subtitle, { color: theme.textSecondary }]}>
          Personal stats for your account.
        </Text>

        <View style={styles.presetRow} accessibilityRole="tablist">
          {(
            [
              ['today', 'Today'],
              ['7d', '7 days'],
              ['30d', '30 days'],
              ['all', 'All time'],
            ] as const
          ).map(([key, label]) => (
            <Pressable
              key={key}
              style={[
                styles.preset,
                {
                  backgroundColor: preset === key ? theme.primary : theme.backgroundElement,
                  borderColor: theme.border,
                },
              ]}
              onPress={() => applyPreset(key)}
              disabled={loading}
              accessibilityRole="tab"
              accessibilityState={{ selected: preset === key }}
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

        {loading ? (
          <View style={styles.loading}>
            <ActivityIndicator color={theme.primary} />
            <Text style={{ color: theme.textSecondary, marginTop: 8 }}>Loading activity…</Text>
          </View>
        ) : null}

        {error ? (
          <View
            style={[
              styles.errorBox,
              { borderColor: theme.danger, backgroundColor: theme.backgroundElement },
            ]}
            accessibilityRole="alert"
          >
            <Ionicons name="warning-outline" size={18} color={theme.danger} />
            <View style={{ flex: 1 }}>
              <Text style={{ color: theme.text, fontWeight: '700' }}>Could not load activity</Text>
              <Text style={{ color: theme.textSecondary, marginTop: 2 }}>{error}</Text>
            </View>
          </View>
        ) : null}

        {!loading && !error ? (
          <>
            {!hasActivity ? (
              <Text style={[styles.emptyBanner, { color: theme.textMuted }]}>
                No activity in this period.
              </Text>
            ) : null}

            <View style={styles.kpiGrid}>
              {PRIMARY_KPIS.map((metric) => (
                <Pressable
                  key={metric.key}
                  onPress={metric.drill ? () => setShowDrill((open) => !open) : undefined}
                  style={[
                    styles.metric,
                    styles.metricPrimary,
                    {
                      backgroundColor: theme.backgroundElement,
                      borderColor: theme.border,
                    },
                  ]}
                  accessibilityRole={metric.drill ? 'button' : undefined}
                >
                  <View style={[styles.metricIcon, { backgroundColor: metric.color + '22' }]}>
                    <Ionicons name={metric.icon} size={18} color={metric.color} />
                  </View>
                  <View style={styles.metricBody}>
                    <View style={styles.metricLabelRow}>
                      <Text
                        style={[styles.metricLabel, { color: theme.textSecondary }]}
                        numberOfLines={1}
                      >
                        {metric.label}
                      </Text>
                      {metric.drill ? (
                        <Text style={[styles.metricAction, { color: theme.primary }]}>Top</Text>
                      ) : null}
                    </View>
                    <Text style={[styles.metricValue, styles.metricValuePrimary, { color: theme.text }]}>
                      {mine[metric.key].toLocaleString()}
                    </Text>
                    <Text style={[styles.metricDesc, { color: theme.textMuted }]} numberOfLines={2}>
                      {metric.description}
                    </Text>
                  </View>
                </Pressable>
              ))}
              {SECONDARY_KPIS.map((metric) => (
                <View
                  key={metric.key}
                  style={[
                    styles.metric,
                    {
                      backgroundColor: theme.backgroundElement,
                      borderColor: theme.border,
                    },
                  ]}
                >
                  <View style={[styles.metricIcon, { backgroundColor: metric.color + '22' }]}>
                    <Ionicons name={metric.icon} size={18} color={metric.color} />
                  </View>
                  <View style={styles.metricBody}>
                    <Text style={[styles.metricLabel, { color: theme.textSecondary }]} numberOfLines={1}>
                      {metric.label}
                    </Text>
                    <Text style={[styles.metricValue, { color: theme.text }]}>
                      {mine[metric.key].toLocaleString()}
                    </Text>
                    <Text style={[styles.metricDesc, { color: theme.textMuted }]} numberOfLines={2}>
                      {metric.description}
                    </Text>
                  </View>
                </View>
              ))}
              <View
                style={[
                  styles.metric,
                  {
                    backgroundColor: theme.backgroundElement,
                    borderColor: theme.border,
                  },
                ]}
              >
                <View style={[styles.metricIcon, { backgroundColor: '#0068ff22' }]}>
                  <Ionicons name="heart-outline" size={18} color="#0068ff" />
                </View>
                <View style={styles.metricBody}>
                  <Text style={[styles.metricLabel, { color: theme.textSecondary }]}>Friends</Text>
                  <Text style={[styles.metricValue, { color: theme.text }]}>
                    {mine.friends.count.toLocaleString()}
                  </Text>
                  <Text style={[styles.metricDesc, { color: theme.textMuted }]} numberOfLines={2}>
                    Sent {mine.friends.requestsSent} · received {mine.friends.requestsReceived} ·
                    accept {Math.round(mine.friends.acceptRate * 100)}%
                  </Text>
                </View>
              </View>
            </View>

            {showDrill ? (
              <View
                style={[
                  styles.panel,
                  { borderColor: theme.border, backgroundColor: theme.backgroundElement },
                ]}
              >
                <View style={styles.panelHeading}>
                  <View style={{ flex: 1 }}>
                    <Text style={[styles.panelTitle, { color: theme.text }]}>Top conversations</Text>
                    <Text style={[styles.panelSubtitle, { color: theme.textSecondary }]}>
                      Where you sent the most messages (lifetime totals).
                    </Text>
                  </View>
                  <Pressable
                    onPress={() => setShowDrill(false)}
                    style={[styles.closeBtn, { borderColor: theme.border }]}
                  >
                    <Text style={{ color: theme.text, fontWeight: '600', fontSize: 13 }}>Close</Text>
                  </Pressable>
                </View>
                {mine.topConversations.length === 0 ? (
                  <Text style={{ color: theme.textMuted }}>No conversations yet.</Text>
                ) : (
                  mine.topConversations.map((row) => (
                    <View key={row.conversationId} style={styles.listRow}>
                      <Text style={{ color: theme.textSecondary, fontFamily: 'monospace' }}>
                        {row.conversationId.slice(0, 8)}…
                      </Text>
                      <Text style={{ color: theme.text, fontWeight: '700' }}>
                        {row.total.toLocaleString()}
                      </Text>
                    </View>
                  ))
                )}
              </View>
            ) : null}

            <View
              style={[
                styles.panel,
                { borderColor: theme.border, backgroundColor: theme.backgroundElement },
              ]}
            >
              <View style={styles.panelHeading}>
                <View style={{ flex: 1 }}>
                  <Text style={[styles.panelTitle, { color: theme.text }]}>Messages over time</Text>
                  <Text style={[styles.panelSubtitle, { color: theme.textSecondary }]}>
                    Sent vs received by day.
                  </Text>
                </View>
              </View>
              <MessagesOverTime series={mine.messagesByDay} />
            </View>

            <View style={styles.splitGrid}>
              <View
                style={[
                  styles.panel,
                  styles.splitPanel,
                  { borderColor: theme.border, backgroundColor: theme.backgroundElement },
                ]}
              >
                <Text style={[styles.panelTitle, { color: theme.text }]}>Media vs text</Text>
                <Text style={[styles.panelSubtitle, { color: theme.textSecondary, marginBottom: 12 }]}>
                  Share of message types you sent.
                </Text>
                <MediaVsText split={mine.mediaVsText} />
              </View>
              <View
                style={[
                  styles.panel,
                  styles.splitPanel,
                  { borderColor: theme.border, backgroundColor: theme.backgroundElement },
                ]}
              >
                <Text style={[styles.panelTitle, { color: theme.text }]}>Direct vs group</Text>
                <Text style={[styles.panelSubtitle, { color: theme.textSecondary, marginBottom: 12 }]}>
                  Where your outbound messages went.
                </Text>
                <DirectVsGroup split={mine.directVsGroup} />
              </View>
            </View>

            <View
              style={[
                styles.panel,
                { borderColor: theme.border, backgroundColor: theme.backgroundElement },
              ]}
            >
              <Text style={[styles.panelTitle, { color: theme.text }]}>When you chat</Text>
              <Text style={[styles.panelSubtitle, { color: theme.textSecondary, marginBottom: 12 }]}>
                Hour × weekday heatmap (UTC).
              </Text>
              <ChatHeatmap cells={mine.heatmap} />
            </View>
          </>
        ) : null}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  scroll: { padding: Spacing.three, paddingBottom: Spacing.six },
  title: {
    fontSize: 28,
    fontWeight: '700',
    letterSpacing: -0.6,
    marginBottom: 6,
  },
  subtitle: { fontSize: 14, marginBottom: Spacing.three, lineHeight: 20 },
  presetRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: Spacing.three },
  preset: {
    borderWidth: 1,
    borderRadius: 999,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  loading: { alignItems: 'center', paddingVertical: 24 },
  errorBox: {
    flexDirection: 'row',
    gap: 10,
    padding: 12,
    borderRadius: Radius.md,
    borderWidth: 1,
    marginBottom: 12,
  },
  emptyBanner: { marginBottom: 12, fontSize: 13 },
  kpiGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 10,
    marginBottom: 4,
  },
  metric: {
    width: '48%',
    flexGrow: 1,
    flexBasis: '47%',
    flexDirection: 'column',
    gap: 10,
    borderWidth: 1,
    borderRadius: Radius.lg,
    padding: 12,
  },
  metricPrimary: {},
  metricIcon: {
    width: 36,
    height: 36,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
  },
  metricBody: { flex: 1, minWidth: 0 },
  metricLabelRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 4,
  },
  metricLabel: { fontSize: 11, fontWeight: '600', flexShrink: 1 },
  metricAction: { fontSize: 10, fontWeight: '700' },
  metricValue: { fontSize: 22, fontWeight: '700', letterSpacing: -0.4, marginTop: 2 },
  metricValuePrimary: { fontSize: 24 },
  metricDesc: { fontSize: 11, marginTop: 2, lineHeight: 15 },
  panel: {
    marginTop: Spacing.three,
    padding: 14,
    borderRadius: Radius.lg,
    borderWidth: 1,
  },
  panelHeading: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 12,
    marginBottom: 12,
  },
  panelTitle: { fontSize: 18, fontWeight: '700', letterSpacing: -0.3 },
  panelSubtitle: { fontSize: 13, marginTop: 2, lineHeight: 18 },
  closeBtn: {
    borderWidth: 1,
    borderRadius: Radius.md,
    paddingHorizontal: 10,
    paddingVertical: 6,
  },
  listRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 8,
  },
  splitGrid: { gap: 0 },
  splitPanel: { marginTop: Spacing.three },
});
