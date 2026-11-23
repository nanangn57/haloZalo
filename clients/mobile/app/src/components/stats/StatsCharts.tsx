import React, { useMemo } from 'react';
import { View, Text, StyleSheet } from 'react-native';
import type {
  DirectGroupSplit,
  HeatmapCell,
  MediaTextSplit,
  MessageDayTotal,
} from '@core/statsService';
import { useTheme } from '@/hooks/use-theme';
import { Radius, Spacing } from '@/constants/theme';

const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

function EmptyChart({ message }: { message: string }) {
  const theme = useTheme();
  return (
    <View style={[styles.empty, { borderColor: theme.border, backgroundColor: theme.background }]}>
      <Text style={{ color: theme.textMuted, fontSize: 13, textAlign: 'center' }}>{message}</Text>
    </View>
  );
}

/** Sent/received by day — bar pairs (không cần SVG). */
export function MessagesOverTime({ series }: { series: MessageDayTotal[] }) {
  const theme = useTheme();
  const meaningful = series.filter((point) => point.sent > 0 || point.received > 0);
  if (series.length === 0 || meaningful.length === 0) {
    return <EmptyChart message="No message activity in this period." />;
  }

  const max = Math.max(1, ...series.flatMap((point) => [point.sent, point.received]));
  const sparse = meaningful.length <= 2;

  return (
    <View>
      {sparse ? (
        <Text style={[styles.note, { color: theme.textMuted }]}>
          Sparse period — {meaningful.length} active day{meaningful.length === 1 ? '' : 's'} in range.
        </Text>
      ) : null}
      <View style={styles.lineLegend}>
        <View style={styles.legendItem}>
          <View style={[styles.swatch, { backgroundColor: theme.primary }]} />
          <Text style={{ color: theme.textSecondary, fontSize: 12 }}>Sent</Text>
        </View>
        <View style={styles.legendItem}>
          <View style={[styles.swatch, { backgroundColor: theme.success }]} />
          <Text style={{ color: theme.textSecondary, fontSize: 12 }}>Received</Text>
        </View>
      </View>
      <View style={styles.barChart}>
        {series.map((day) => (
          <View key={day.day} style={styles.barCol}>
            <View style={styles.barPair}>
              <View
                style={[
                  styles.bar,
                  {
                    height: Math.max(4, (day.sent / max) * 120),
                    backgroundColor: theme.primary,
                  },
                ]}
              />
              <View
                style={[
                  styles.bar,
                  {
                    height: Math.max(4, (day.received / max) * 120),
                    backgroundColor: theme.success,
                    opacity: 0.9,
                  },
                ]}
              />
            </View>
            <Text style={[styles.barLabel, { color: theme.textMuted }]}>{day.day.slice(5)}</Text>
          </View>
        ))}
      </View>
    </View>
  );
}

export function MediaVsText({ split }: { split: MediaTextSplit }) {
  const theme = useTheme();
  const total = split.text + split.media;
  if (total === 0) {
    return <EmptyChart message="No messages to split yet." />;
  }
  const textPct = split.text / total;
  const mediaPct = split.media / total;

  return (
    <View>
      <View style={styles.donutCenter}>
        <Text style={[styles.donutTotal, { color: theme.text }]}>{total}</Text>
        <Text style={{ color: theme.textMuted, fontSize: 12 }}>sent</Text>
      </View>
      <View style={[styles.stackTrack, { backgroundColor: theme.border }]}>
        {textPct > 0 ? (
          <View style={{ width: `${textPct * 100}%`, backgroundColor: theme.primary, height: '100%' }} />
        ) : null}
        {mediaPct > 0 ? (
          <View style={{ width: `${mediaPct * 100}%`, backgroundColor: theme.success, height: '100%' }} />
        ) : null}
      </View>
      <View style={styles.legendList}>
        <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
          Text {split.text} · {Math.round(textPct * 100)}%
        </Text>
        <Text style={{ color: theme.textSecondary, fontSize: 13 }}>
          Media {split.media} · {Math.round(mediaPct * 100)}%
        </Text>
      </View>
    </View>
  );
}

export function DirectVsGroup({ split }: { split: DirectGroupSplit }) {
  const theme = useTheme();
  const total = split.direct + split.group;
  if (total === 0) {
    return <EmptyChart message="No direct/group split yet." />;
  }
  const directPct = (split.direct / total) * 100;
  const groupPct = (split.group / total) * 100;

  return (
    <View>
      <View style={styles.stackMeta}>
        <Text style={{ color: theme.text, fontSize: 13 }}>
          <Text style={{ fontWeight: '700' }}>{Math.round(directPct)}%</Text> direct
        </Text>
        <Text style={{ color: theme.text, fontSize: 13 }}>
          <Text style={{ fontWeight: '700' }}>{Math.round(groupPct)}%</Text> group
        </Text>
      </View>
      <View style={[styles.stackTrack, { backgroundColor: theme.border }]}>
        {directPct > 0 ? (
          <View style={{ width: `${directPct}%`, backgroundColor: '#0b6bcb', height: '100%' }} />
        ) : null}
        {groupPct > 0 ? (
          <View style={{ width: `${groupPct}%`, backgroundColor: '#7c5cbf', height: '100%' }} />
        ) : null}
      </View>
      <View style={styles.legendList}>
        <Text style={{ color: theme.textSecondary, fontSize: 13 }}>Direct {split.direct}</Text>
        <Text style={{ color: theme.textSecondary, fontSize: 13 }}>Group {split.group}</Text>
      </View>
    </View>
  );
}

export function ChatHeatmap({ cells }: { cells: HeatmapCell[] }) {
  const theme = useTheme();
  const lookup = useMemo(
    () => new Map(cells.map((cell) => [`${cell.weekday}-${cell.hour}`, cell.total])),
    [cells],
  );
  const max = Math.max(1, ...cells.map((cell) => cell.total), 0);
  const hasData = cells.some((cell) => cell.total > 0);
  if (!hasData) {
    return <EmptyChart message="No timing pattern yet — send a few messages first." />;
  }

  return (
    <View>
      <View style={styles.heatmapHours}>
        <Text style={[styles.heatmapDayLabel, { color: theme.textMuted }]} />
        {Array.from({ length: 24 }, (_, hour) => (
          <Text key={hour} style={[styles.heatmapHour, { color: theme.textMuted }]}>
            {hour % 6 === 0 ? String(hour) : ''}
          </Text>
        ))}
      </View>
      {WEEKDAYS.map((label, weekday) => (
        <View key={label} style={styles.heatmapRow}>
          <Text style={[styles.heatmapDayLabel, { color: theme.textSecondary }]}>{label}</Text>
          {Array.from({ length: 24 }, (_, hour) => {
            const total = lookup.get(`${weekday}-${hour}`) ?? 0;
            const intensity = total === 0 ? 0 : 0.22 + (total / max) * 0.78;
            return (
              <View
                key={hour}
                style={[
                  styles.heatmapCell,
                  {
                    backgroundColor:
                      total === 0 ? theme.border : mixPrimary(theme.primary, theme.border, intensity),
                  },
                ]}
                accessibilityLabel={`${label} ${hour}:00 — ${total}`}
              />
            );
          })}
        </View>
      ))}
      <View style={styles.heatmapScale}>
        <Text style={{ color: theme.textMuted, fontSize: 11 }}>Less</Text>
        <View style={[styles.heatmapScaleBar, { backgroundColor: theme.primary }]} />
        <Text style={{ color: theme.textMuted, fontSize: 11 }}>More</Text>
      </View>
    </View>
  );
}

/** Blend primary onto border for heatmap fill without color-mix. */
function mixPrimary(primary: string, border: string, amount: number): string {
  const a = Math.max(0, Math.min(1, amount));
  const p = hexToRgb(primary);
  const b = hexToRgb(border);
  if (!p || !b) return primary;
  const r = Math.round(b.r + (p.r - b.r) * a);
  const g = Math.round(b.g + (p.g - b.g) * a);
  const bl = Math.round(b.b + (p.b - b.b) * a);
  return `rgb(${r},${g},${bl})`;
}

function hexToRgb(hex: string): { r: number; g: number; b: number } | null {
  const raw = hex.replace('#', '');
  if (raw.length !== 6) return null;
  return {
    r: parseInt(raw.slice(0, 2), 16),
    g: parseInt(raw.slice(2, 4), 16),
    b: parseInt(raw.slice(4, 6), 16),
  };
}

const styles = StyleSheet.create({
  empty: {
    minHeight: 96,
    borderWidth: 1,
    borderRadius: Radius.md,
    borderStyle: 'dashed',
    alignItems: 'center',
    justifyContent: 'center',
    padding: Spacing.three,
  },
  note: { fontSize: 12, marginBottom: 8 },
  lineLegend: { flexDirection: 'row', gap: 16, marginBottom: 10 },
  legendItem: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  swatch: { width: 10, height: 10, borderRadius: 2 },
  barChart: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: 4,
    minHeight: 148,
    paddingTop: 8,
  },
  barCol: { flex: 1, alignItems: 'center', gap: 6 },
  barPair: { flexDirection: 'row', alignItems: 'flex-end', gap: 2, height: 120 },
  bar: { width: 5, borderRadius: 2 },
  barLabel: { fontSize: 9, fontVariant: ['tabular-nums'] },
  donutCenter: { alignItems: 'center', marginBottom: 12 },
  donutTotal: { fontSize: 28, fontWeight: '700', letterSpacing: -0.5 },
  stackTrack: {
    height: 12,
    borderRadius: 999,
    overflow: 'hidden',
    flexDirection: 'row',
    marginBottom: 10,
  },
  stackMeta: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
  legendList: { gap: 4 },
  heatmapHours: { flexDirection: 'row', alignItems: 'center', marginBottom: 2 },
  heatmapRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 2 },
  heatmapDayLabel: { width: 32, fontSize: 10, fontWeight: '600' },
  heatmapHour: { flex: 1, fontSize: 8, textAlign: 'center' },
  heatmapCell: {
    flex: 1,
    aspectRatio: 1,
    marginHorizontal: 0.5,
    borderRadius: 2,
    maxHeight: 12,
  },
  heatmapScale: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginTop: 10,
  },
  heatmapScaleBar: {
    flex: 1,
    height: 6,
    borderRadius: 999,
    opacity: 0.7,
  },
});
