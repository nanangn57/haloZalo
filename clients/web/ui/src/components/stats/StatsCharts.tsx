import type {
  DirectGroupSplit,
  HeatmapCell,
  MediaDayTotal,
  MediaTextSplit,
  MessageDayTotal,
  DayTotal,
} from '@core/statsService'

const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

function EmptyChart({ message }: { message: string }) {
  return (
    <div className="activity-chart-empty">
      <div className="activity-chart-empty-plot" aria-hidden="true" />
      <p className="activity-empty">{message}</p>
    </div>
  )
}

export function LineChart({
  series,
  height = 240,
}: {
  series: MessageDayTotal[]
  height?: number
}) {
  const meaningful = series.filter((point) => point.sent > 0 || point.received > 0)
  if (series.length === 0 || meaningful.length === 0) {
    return <EmptyChart message="No message activity in this period." />
  }

  const width = 720
  const padL = 36
  const padR = 16
  const padT = 18
  const padB = 32
  const plotW = width - padL - padR
  const plotH = height - padT - padB
  const max = Math.max(
    1,
    ...series.flatMap((point) => [point.sent, point.received]),
  )
  const step = series.length === 1 ? 0 : plotW / (series.length - 1)

  function coords(values: number[]) {
    return values.map((value, index) => {
      const x = padL + index * step
      const y = padT + plotH - (value / max) * plotH
      return { x, y, value }
    })
  }

  function linePath(points: { x: number; y: number }[]) {
    return points
      .map((point, index) => `${index === 0 ? 'M' : 'L'}${point.x},${point.y}`)
      .join(' ')
  }

  function areaPath(points: { x: number; y: number }[]) {
    if (points.length === 0) return ''
    const base = padT + plotH
    const line = linePath(points)
    const last = points[points.length - 1]
    const first = points[0]
    return `${line} L${last.x},${base} L${first.x},${base} Z`
  }

  const sentPts = coords(series.map((point) => point.sent))
  const recvPts = coords(series.map((point) => point.received))
  const tickCount = max <= 4 ? max : 4
  const ticks = Array.from({ length: tickCount + 1 }, (_, index) =>
    Math.round((max / tickCount) * (tickCount - index)),
  )

  const sparse = meaningful.length <= 2

  return (
    <div className="activity-chart-frame">
      {sparse ? (
        <p className="activity-chart-note">
          Sparse period — {meaningful.length} active day
          {meaningful.length === 1 ? '' : 's'} in range.
        </p>
      ) : null}
      <svg
        className="activity-chart"
        viewBox={`0 0 ${width} ${height}`}
        role="img"
        aria-label="Messages sent and received by day"
      >
        <defs>
          <linearGradient id="sentArea" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="var(--app-primary)" stopOpacity="0.28" />
            <stop offset="100%" stopColor="var(--app-primary)" stopOpacity="0.02" />
          </linearGradient>
          <linearGradient id="recvArea" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="var(--app-success)" stopOpacity="0.22" />
            <stop offset="100%" stopColor="var(--app-success)" stopOpacity="0.02" />
          </linearGradient>
        </defs>

        {ticks.map((value, index) => {
          const y = padT + (plotH / tickCount) * index
          return (
            <g key={`${value}-${index}`}>
              <line
                x1={padL}
                x2={width - padR}
                y1={y}
                y2={y}
                className="activity-chart-grid"
              />
              <text x={padL - 8} y={y + 4} textAnchor="end" className="activity-chart-axis">
                {value}
              </text>
            </g>
          )
        })}

        <path d={areaPath(sentPts)} fill="url(#sentArea)" />
        <path d={areaPath(recvPts)} fill="url(#recvArea)" />
        <path d={linePath(sentPts)} className="activity-line activity-line-sent" fill="none" />
        <path
          d={linePath(recvPts)}
          className="activity-line activity-line-received"
          fill="none"
        />

        {sentPts.map((point, index) => (
          <circle
            key={`s-${series[index].day}`}
            cx={point.x}
            cy={point.y}
            r={3.5}
            className="activity-chart-dot sent"
          >
            <title>{`${series[index].day}: sent ${point.value}`}</title>
          </circle>
        ))}
        {recvPts.map((point, index) => (
          <circle
            key={`r-${series[index].day}`}
            cx={point.x}
            cy={point.y}
            r={3.5}
            className="activity-chart-dot received"
          >
            <title>{`${series[index].day}: received ${point.value}`}</title>
          </circle>
        ))}

        {series.map((point, index) => (
          <text
            key={point.day}
            x={padL + index * step}
            y={height - 10}
            textAnchor="middle"
            className="activity-chart-label"
          >
            {point.day.slice(5)}
          </text>
        ))}
      </svg>
    </div>
  )
}

export function DonutChart({ split }: { split: MediaTextSplit }) {
  const total = split.text + split.media
  if (total === 0) {
    return <EmptyChart message="No messages to split yet." />
  }
  const textPct = split.text / total
  const mediaPct = split.media / total
  const r = 46
  const c = 2 * Math.PI * r
  return (
    <div className="activity-donut-wrap">
      <div className="activity-donut-stage">
        <svg viewBox="0 0 120 120" className="activity-donut" aria-hidden="true">
          <circle cx="60" cy="60" r={r} className="activity-donut-track" />
          {textPct > 0 ? (
            <circle
              cx="60"
              cy="60"
              r={r}
              className={`activity-donut-text${textPct >= 1 ? ' is-full' : ''}`}
              strokeDasharray={`${textPct * c} ${c}`}
              strokeDashoffset={c * 0.25}
            />
          ) : null}
          {mediaPct > 0 ? (
            <circle
              cx="60"
              cy="60"
              r={r}
              className={`activity-donut-media${mediaPct >= 1 ? ' is-full' : ''}`}
              strokeDasharray={`${mediaPct * c} ${c}`}
              strokeDashoffset={c * 0.25 - textPct * c}
            />
          ) : null}
        </svg>
        <div className="activity-donut-center">
          <strong>{total}</strong>
          <span>sent</span>
        </div>
      </div>
      <ul className="activity-legend">
        <li>
          <span className="swatch text" /> Text {split.text}
          <em>{Math.round(textPct * 100)}%</em>
        </li>
        <li>
          <span className="swatch media" /> Media {split.media}
          <em>{Math.round(mediaPct * 100)}%</em>
        </li>
      </ul>
    </div>
  )
}

export function StackedBar({ split }: { split: DirectGroupSplit }) {
  const total = split.direct + split.group
  if (total === 0) {
    return <EmptyChart message="No direct/group split yet." />
  }
  const directPct = (split.direct / total) * 100
  const groupPct = (split.group / total) * 100
  return (
    <div className="activity-stack">
      <div className="activity-stack-meta">
        <span>
          <strong>{Math.round(directPct)}%</strong> direct
        </span>
        <span>
          <strong>{Math.round(groupPct)}%</strong> group
        </span>
      </div>
      <div className="activity-stack-bar" aria-hidden="true">
        {directPct > 0 ? (
          <span style={{ width: `${directPct}%` }} className="direct" />
        ) : null}
        {groupPct > 0 ? (
          <span style={{ width: `${groupPct}%` }} className="group" />
        ) : null}
      </div>
      <ul className="activity-legend">
        <li>
          <span className="swatch direct" /> Direct {split.direct}
        </li>
        <li>
          <span className="swatch group" /> Group {split.group}
        </li>
      </ul>
    </div>
  )
}

export function Heatmap({ cells }: { cells: HeatmapCell[] }) {
  const lookup = new Map(
    cells.map((cell) => [`${cell.weekday}-${cell.hour}`, cell.total]),
  )
  const max = Math.max(1, ...cells.map((cell) => cell.total), 0)
  const hasData = cells.some((cell) => cell.total > 0)
  if (!hasData) {
    return <EmptyChart message="No timing pattern yet — send a few messages first." />
  }
  return (
    <div className="activity-heatmap-wrap">
      <div className="activity-heatmap" role="img" aria-label="Messaging heatmap by hour">
        <div className="activity-heatmap-hours">
          <span />
          {Array.from({ length: 24 }, (_, hour) => (
            <span key={hour}>{hour % 6 === 0 ? hour : ''}</span>
          ))}
        </div>
        {WEEKDAYS.map((label, weekday) => (
          <div className="activity-heatmap-row" key={label}>
            <span className="activity-heatmap-day">{label}</span>
            {Array.from({ length: 24 }, (_, hour) => {
              const total = lookup.get(`${weekday}-${hour}`) ?? 0
              const fill =
                total === 0
                  ? undefined
                  : `color-mix(in srgb, var(--app-primary) ${Math.round(
                      22 + (total / max) * 78,
                    )}%, var(--app-border))`
              return (
                <span
                  key={hour}
                  title={`${label} ${hour}:00 — ${total}`}
                  style={fill ? { background: fill } : undefined}
                  className={`activity-heatmap-cell${total > 0 ? ' has-value' : ''}`}
                />
              )
            })}
          </div>
        ))}
      </div>
      <div className="activity-heatmap-scale" aria-hidden="true">
        <span>Less</span>
        <span className="activity-heatmap-scale-bar" />
        <span>More</span>
      </div>
    </div>
  )
}

export function SimpleLine({
  days,
  label,
}: {
  days: DayTotal[]
  label: string
}) {
  if (days.length === 0 || days.every((day) => day.total === 0)) {
    return <EmptyChart message={`No ${label} in this period.`} />
  }
  const asMessages: MessageDayTotal[] = days.map((day) => ({
    day: day.day,
    sent: day.total,
    received: 0,
  }))
  return <LineChart series={asMessages} height={200} />
}

export function MediaStack({ days }: { days: MediaDayTotal[] }) {
  if (days.length === 0) {
    return <EmptyChart message="No media uploads in this period." />
  }
  const byDay = new Map<string, Record<string, number>>()
  for (const row of days) {
    const bucket = byDay.get(row.day) ?? {}
    bucket[row.type] = (bucket[row.type] ?? 0) + row.total
    byDay.set(row.day, bucket)
  }
  const labels = [...byDay.keys()].sort()
  const max = Math.max(
    1,
    ...labels.map((day) =>
      Object.values(byDay.get(day) ?? {}).reduce((sum, value) => sum + value, 0),
    ),
  )
  return (
    <div className="activity-media-bars">
      {labels.map((day) => {
        const bucket = byDay.get(day) ?? {}
        const total = Object.values(bucket).reduce((sum, value) => sum + value, 0)
        return (
          <div key={day} className="activity-media-col">
            <div
              className="activity-media-stack"
              style={{ height: `${Math.max(12, (total / max) * 140)}px` }}
            >
              {Object.entries(bucket).map(([type, value]) => (
                <span
                  key={type}
                  className={`media-${type.toLowerCase()}`}
                  style={{ flex: value }}
                  title={`${type}: ${value}`}
                />
              ))}
            </div>
            <span>{day.slice(5)}</span>
          </div>
        )
      })}
    </div>
  )
}
