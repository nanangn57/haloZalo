import { useEffect, useState } from 'react'
import { Navigate } from 'react-router-dom'
import {
  getActivityStatsApi,
  getDashboardStatsApi,
  type DashboardStats,
} from '@core/statsService'
import { getGroupsApi } from '@core/groupService'
import { isPlatformAdmin } from '@core/authSession'
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
    color: 'sky',
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
    color: 'amber',
  },
] as const

function groupLabel(
  groupId: string,
  namesById: Record<string, string>,
): string {
  const named = namesById[groupId]?.trim()
  if (named) {
    return named
  }
  return `${groupId.slice(0, 8)}…`
}

export default function AdminStatsPage() {
  const allowed = isPlatformAdmin()
  const [stats, setStats] = useState<ActivityStats | null>(null)
  const [dashboard, setDashboard] = useState<DashboardStats | null>(null)
  const [groupNamesById, setGroupNamesById] = useState<
    Record<string, string>
  >({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!allowed) {
      return
    }

    async function loadStats() {
      try {
        setLoading(true)
        setError(null)
        const [totals, board, groups] = await Promise.all([
          getActivityStatsApi(),
          getDashboardStatsApi(),
          getGroupsApi().catch(() => []),
        ])
        setStats(totals)
        setDashboard(board)
        const names: Record<string, string> = {}
        groups.forEach((group) => {
          const name = group.name?.trim()
          if (name) {
            names[group.groupId] = name
          }
        })
        setGroupNamesById(names)
      } catch (err) {
        setError(
          err instanceof Error
            ? err.message
            : 'Unable to load admin stats',
        )
      } finally {
        setLoading(false)
      }
    }

    void loadStats()
  }, [allowed])

  if (!allowed) {
    return <Navigate to="/dashboard" replace />
  }

  const metrics = stats
    ? metricsConfig.map((item) => ({
        ...item,
        value: stats[item.key],
      }))
    : []

  const maxDayTotal = Math.max(
    1,
    ...(dashboard?.messagesByDay.map((day) => day.total) ?? [1]),
  )

  return (
    <main className="activity-page">
      <div className="activity-header">
        <div>
          <h1>Platform stats</h1>
          <p>Admin-only aggregates across the whole platform.</p>
        </div>
      </div>

      {loading ? (
        <div className="app-loading">
          <div className="app-spinner" />
          <p>Loading platform stats…</p>
        </div>
      ) : null}
      {error ? (
        <div className="activity-error" role="alert">
          <i className="bi bi-exclamation-triangle" aria-hidden="true" />
          <div>
            <strong>Could not load platform stats</strong>
            <p>{error}</p>
          </div>
        </div>
      ) : null}

      {!loading && !error ? (
        <>
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

          <section className="row g-3 mb-4">
            <div className="col-md-6">
              <div className="app-card h-100">
                <div className="app-card-body">
                  <h2 style={{ fontSize: 16, marginBottom: 12 }}>
                    Active users
                  </h2>
                  <div
                    style={{
                      fontSize: 36,
                      fontWeight: 700,
                      letterSpacing: '-0.04em',
                    }}
                  >
                    {dashboard?.activeUsers?.toLocaleString() ?? 0}
                  </div>
                  <p className="app-text-muted mb-0" style={{ fontSize: 13 }}>
                    Users with recent recorded activity
                  </p>
                </div>
              </div>
            </div>

            <div className="col-md-6">
              <div className="app-card h-100">
                <div className="app-card-body">
                  <h2 style={{ fontSize: 16, marginBottom: 12 }}>Top groups</h2>
                  {(dashboard?.topGroups?.length ?? 0) === 0 ? (
                    <p className="app-text-muted mb-0" style={{ fontSize: 13 }}>
                      No group traffic yet.
                    </p>
                  ) : (
                    <ul className="list-unstyled mb-0">
                      {dashboard?.topGroups.map((group) => {
                        const label = groupLabel(
                          group.groupId,
                          groupNamesById,
                        )
                        return (
                          <li
                            key={group.groupId}
                            className="d-flex justify-content-between py-1 gap-2"
                          >
                            <span className="text-truncate" title={label}>
                              {label}
                            </span>
                            <strong>{group.total}</strong>
                          </li>
                        )
                      })}
                    </ul>
                  )}
                </div>
              </div>
            </div>
          </section>

          <section className="mb-4">
            <div className="app-card">
              <div className="app-card-body">
                <h2 style={{ fontSize: 16, marginBottom: 16 }}>
                  Messages by day
                </h2>
                {(dashboard?.messagesByDay?.length ?? 0) === 0 ? (
                  <p className="app-text-muted mb-0" style={{ fontSize: 13 }}>
                    No message history yet.
                  </p>
                ) : (
                  <div
                    className="d-flex align-items-end gap-2"
                    style={{ minHeight: 140 }}
                  >
                    {dashboard?.messagesByDay.map((day) => (
                      <div
                        key={day.day}
                        className="d-flex flex-column align-items-center flex-grow-1"
                        title={`${day.day}: ${day.total}`}
                      >
                        <div
                          className="w-100 rounded-top"
                          style={{
                            height: `${Math.max(8, (day.total / maxDayTotal) * 100)}px`,
                            background: 'var(--app-primary)',
                            opacity: 0.88,
                          }}
                        />
                        <small className="app-text-muted mt-1">
                          {day.day.slice(5)}
                        </small>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </section>
        </>
      ) : null}
    </main>
  )
}
