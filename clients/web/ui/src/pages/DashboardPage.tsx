
import { useEffect, useState } from 'react'
import { getActivityStatsApi } from '@core/statsService'
// import type { ActivityStats } from '@core/statsService'
import type { ActivityStats } from '@core/types/stat'

const metricsConfig = [
  {
    key: 'logins',
    label: 'Total Logins',
    description: 'Successful sign-ins',
    icon: 'bi bi-box-arrow-in-right',
    color: 'blue',
  },
  {
    key: 'groupsCreated',
    label: 'Groups Created',
    description: 'New groups established',
    icon: 'bi bi-people',
    color: 'purple',
  },
  {
    key: 'messagesCreated',
    label: 'Messages Created',
    description: 'Messages sent',
    icon: 'bi bi-chat-dots',
    color: 'green',
  },
  {
    key: 'mediaUploads',
    label: 'Media Uploads',
    description: 'Files and images uploaded',
    icon: 'bi bi-cloud-upload',
    color: 'orange',
  },
] as const

export default function DashboardPage() {
  const [stats, setStats] = useState<ActivityStats | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    async function loadStats() {
      try {
        setLoading(true)
        setError(null)

        const data = await getActivityStatsApi()
        setStats(data)
      } catch (err) {
        setError(
          err instanceof Error
            ? err.message
            : 'Unable to load activity data'
        )
      } finally {
        setLoading(false)
      }
    }

    loadStats()
  }, [])

  const metrics = stats
    ? metricsConfig.map((item) => ({
        ...item,
        value: stats[item.key],
      }))
    : []

  return (
    <main className="activity-page">
      <div className="activity-header">
        <div>
          <div className="activity-eyebrow">
            HALOZALO / ANALYTICS
          </div>
          <h1>Activity Overview</h1>
          <p>
            A summary of activity across the platform.
          </p>
        </div>

        <div className="activity-status">
          <span className="activity-status-dot" />
          Activity Statistics
        </div>
      </div>

      {loading ? (
        <div className="activity-grid">
          {metricsConfig.map((item) => (
            <div className="activity-skeleton" key={item.key}>
              <div className="skeleton-line skeleton-icon" />
              <div className="skeleton-line skeleton-title" />
              <div className="skeleton-line skeleton-value" />
            </div>
          ))}
        </div>
      ) : error ? (
        <div className="activity-error">
          <i className="bi bi-exclamation-circle" />
          <div>
            <strong>Unable to load activity statistics</strong>
            <p>{error}</p>
          </div>
        </div>
      ) : stats ? (
        <>
          <section className="activity-section">
            <div className="activity-section-heading">
              <div>
                <h2>Platform Statistics</h2>
                <p>Key activity metrics</p>
              </div>
              <span className="activity-count">04 METRICS</span>
            </div>

            <div className="activity-grid">
              {metrics.map((metric) => (
                <article
                  className="activity-card"
                  key={metric.key}
                >
                  <div className="activity-card-top">
                    <div
                      className={`activity-icon ${metric.color}`}
                    >
                      <i className={metric.icon} />
                    </div>
                    <i className="bi bi-three-dots activity-more" />
                  </div>

                  <div className="activity-card-content">
                    <p className="activity-label">
                      {metric.label}
                    </p>
                    <h3>
                      {metric.value.toLocaleString()}
                    </h3>
                    <span className="activity-description">
                      {metric.description}
                    </span>
                  </div>
                </article>
              ))}
            </div>
          </section>

          <section className="activity-note">
            <div className="activity-note-icon">
              <i className="bi bi-info-circle" />
            </div>
            <div>
              <strong>About these statistics</strong>
              <p>
                These figures represent aggregated activity
                statistics provided by the analytics service.
              </p>
            </div>
          </section>
        </>
      ) : null}
    </main>
  )
}
