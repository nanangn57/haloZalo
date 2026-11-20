import { useEffect, useState } from 'react'
import { getMyActivityApi, type MyActivity } from '@core/statsService'

const personalMetrics = [
  {
    key: 'logins' as const,
    label: 'Your logins',
    description: 'Successful sign-ins',
    icon: 'bi bi-box-arrow-in-right',
    color: 'blue',
  },
  {
    key: 'messagesSent' as const,
    label: 'Messages sent',
    description: 'Messages you have sent',
    icon: 'bi bi-chat-dots',
    color: 'green',
  },
  {
    key: 'groupsCreated' as const,
    label: 'Groups created',
    description: 'Groups you own',
    icon: 'bi bi-people',
    color: 'sky',
  },
]

export default function DashboardPage() {
  const [mine, setMine] = useState<MyActivity | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    async function loadStats() {
      try {
        setLoading(true)
        setError(null)
        setMine(await getMyActivityApi())
      } catch (err) {
        setError(
          err instanceof Error
            ? err.message
            : 'Unable to load activity data',
        )
      } finally {
        setLoading(false)
      }
    }

    void loadStats()
  }, [])

  const metrics = mine
    ? personalMetrics.map((item) => ({
        ...item,
        value: mine[item.key],
      }))
    : []

  return (
    <main className="activity-page">
      <div className="activity-header">
        <div>
          <h1>Your activity</h1>
          <p>Personal stats for your account.</p>
        </div>
      </div>

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
        <section className="mb-4">
          <div className="activity-grid">
            {metrics.map((metric) => (
              <article className="activity-card" key={metric.key}>
                <div className="activity-card-top">
                  <div className={`activity-icon ${metric.color}`}>
                    <i className={metric.icon} />
                  </div>
                </div>
                <div className="activity-card-content">
                  <p className="activity-label">{metric.label}</p>
                  <h3>{metric.value.toLocaleString()}</h3>
                  <span className="activity-description">
                    {metric.description}
                  </span>
                </div>
              </article>
            ))}
          </div>
        </section>
      ) : null}
    </main>
  )
}
