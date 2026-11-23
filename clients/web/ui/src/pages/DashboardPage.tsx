import { useEffect, useState } from 'react'
import {
  emptyMyActivity,
  getMyActivityApi,
  rangePreset,
  type DateRange,
  type MyActivity,
} from '@core/statsService'
import DateRangeFilter from '../components/common/DateRangeFilter'
import {
  DonutChart,
  Heatmap,
  LineChart,
  StackedBar,
} from '../components/stats/StatsCharts'

const primaryKpis = [
  {
    key: 'messagesSent' as const,
    label: 'Messages sent',
    description: 'Text and media you sent',
    icon: 'bi bi-chat-dots',
    color: 'green',
    drill: true,
  },
  {
    key: 'messagesReceived' as const,
    label: 'Messages received',
    description: 'Messages addressed to you',
    icon: 'bi bi-inbox',
    color: 'sky',
  },
  {
    key: 'activeConversations' as const,
    label: 'Active chats',
    description: 'Conversations you messaged in',
    icon: 'bi bi-lightning',
    color: 'amber',
  },
]

const secondaryKpis = [
  {
    key: 'logins' as const,
    label: 'Logins',
    description: 'Successful sign-ins',
    icon: 'bi bi-box-arrow-in-right',
    color: 'blue',
  },
  {
    key: 'groupsCreated' as const,
    label: 'Groups',
    description: 'Groups you own',
    icon: 'bi bi-people',
    color: 'sky',
  },
]

export default function DashboardPage() {
  const [mine, setMine] = useState<MyActivity>(emptyMyActivity())
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [range, setRange] = useState<DateRange>(() => rangePreset('7d'))
  const [showDrill, setShowDrill] = useState(false)

  useEffect(() => {
    async function loadStats() {
      try {
        setLoading(true)
        setError(null)
        setMine(await getMyActivityApi(range))
      } catch (err) {
        setError(
          err instanceof Error ? err.message : 'Unable to load activity data',
        )
      } finally {
        setLoading(false)
      }
    }

    void loadStats()
  }, [range])

  const hasActivity =
    mine.messagesSent > 0 ||
    mine.messagesReceived > 0 ||
    mine.logins > 0 ||
    mine.groupsCreated > 0

  return (
    <main className="activity-page">
      <div className="activity-header">
        <div>
          <h1>Your activity</h1>
          <p>Personal stats for your account.</p>
        </div>
      </div>

      <DateRangeFilter value={range} onApply={setRange} disabled={loading} />

      {loading ? (
        <div className="app-loading">
          <div className="app-spinner" />
          <p>Loading activity…</p>
        </div>
      ) : null}
      {error ? (
        <div className="activity-error" role="alert">
          <i className="bi bi-exclamation-triangle" aria-hidden="true" />
          <div>
            <strong>Could not load activity</strong>
            <p>{error}</p>
          </div>
        </div>
      ) : null}

      {!loading && !error ? (
        <>
          {!hasActivity ? (
            <p className="activity-empty activity-empty-banner">
              No activity in this period.
            </p>
          ) : null}

          <section className="mb-4">
            <div className="activity-kpi-primary">
              {primaryKpis.map((metric) => (
                <article
                  className={`activity-metric activity-metric-primary${metric.drill ? ' is-clickable' : ''}`}
                  key={metric.key}
                  onClick={
                    metric.drill
                      ? () => setShowDrill((open) => !open)
                      : undefined
                  }
                  onKeyDown={
                    metric.drill
                      ? (event) => {
                          if (event.key === 'Enter' || event.key === ' ') {
                            event.preventDefault()
                            setShowDrill((open) => !open)
                          }
                        }
                      : undefined
                  }
                  role={metric.drill ? 'button' : undefined}
                  tabIndex={metric.drill ? 0 : undefined}
                >
                  <div className={`activity-icon ${metric.color}`}>
                    <i className={metric.icon} aria-hidden="true" />
                  </div>
                  <div className="activity-metric-body">
                    <p className="activity-label">
                      {metric.label}
                      {metric.drill ? (
                        <span className="activity-metric-action">Top chats</span>
                      ) : null}
                    </p>
                    <p className="activity-metric-value">
                      {mine[metric.key].toLocaleString()}
                    </p>
                    <span className="activity-description">{metric.description}</span>
                  </div>
                </article>
              ))}
            </div>

            <div className="activity-kpi-secondary">
              {secondaryKpis.map((metric) => (
                <article className="activity-metric activity-metric-secondary" key={metric.key}>
                  <div className={`activity-icon ${metric.color}`}>
                    <i className={metric.icon} aria-hidden="true" />
                  </div>
                  <div className="activity-metric-body">
                    <p className="activity-label">{metric.label}</p>
                    <p className="activity-metric-value">
                      {mine[metric.key].toLocaleString()}
                    </p>
                    <span className="activity-description">{metric.description}</span>
                  </div>
                </article>
              ))}
              <article className="activity-metric activity-metric-secondary">
                <div className="activity-icon blue">
                  <i className="bi bi-person-hearts" aria-hidden="true" />
                </div>
                <div className="activity-metric-body">
                  <p className="activity-label">Friends</p>
                  <p className="activity-metric-value">
                    {mine.friends.count.toLocaleString()}
                  </p>
                  <span className="activity-description">
                    Sent {mine.friends.requestsSent} · received{' '}
                    {mine.friends.requestsReceived} · accept{' '}
                    {Math.round(mine.friends.acceptRate * 100)}%
                  </span>
                </div>
              </article>
            </div>
          </section>

          {showDrill ? (
            <section className="activity-panel mb-4">
              <div className="activity-section-heading">
                <div>
                  <h2>Top conversations</h2>
                  <p>Where you sent the most messages (lifetime totals).</p>
                </div>
                <button
                  type="button"
                  className="app-button"
                  onClick={() => setShowDrill(false)}
                >
                  Close
                </button>
              </div>
              {mine.topConversations.length === 0 ? (
                <p className="activity-empty">No conversations yet.</p>
              ) : (
                <ul className="activity-list">
                  {mine.topConversations.map((row) => (
                    <li key={row.conversationId}>
                      <code>{row.conversationId.slice(0, 8)}…</code>
                      <strong>{row.total.toLocaleString()}</strong>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          ) : null}

          <section className="activity-panel mb-4">
            <div className="activity-section-heading">
              <div>
                <h2>Messages over time</h2>
                <p>Sent vs received by day.</p>
              </div>
              <div className="activity-legend-inline">
                <span>
                  <i className="swatch sent" /> Sent
                </span>
                <span>
                  <i className="swatch received" /> Received
                </span>
              </div>
            </div>
            <LineChart series={mine.messagesByDay} />
          </section>

          <section className="activity-split-grid mb-4">
            <div className="activity-panel">
              <div className="activity-section-heading">
                <div>
                  <h2>Media vs text</h2>
                  <p>Share of message types you sent.</p>
                </div>
              </div>
              <DonutChart split={mine.mediaVsText} />
            </div>
            <div className="activity-panel">
              <div className="activity-section-heading">
                <div>
                  <h2>Direct vs group</h2>
                  <p>Where your outbound messages went.</p>
                </div>
              </div>
              <StackedBar split={mine.directVsGroup} />
            </div>
          </section>

          <section className="activity-panel mb-4">
            <div className="activity-section-heading">
              <div>
                <h2>When you chat</h2>
                <p>Hour × weekday heatmap (UTC).</p>
              </div>
            </div>
            <Heatmap cells={mine.heatmap} />
          </section>
        </>
      ) : null}
    </main>
  )
}
