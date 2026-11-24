import { useEffect, useState } from 'react'
import { Navigate } from 'react-router-dom'
import {
  getActivityStatsApi,
  getAdminUserSummaryApi,
  getDashboardStatsApi,
  rangePreset,
  type AdminUserSummary,
  type DashboardStats,
  type DateRange,
  type MyActivity,
} from '@core/statsService'
import DateRangeFilter from '../components/common/DateRangeFilter'
import Avatar from '../components/common/Avatar'
import { Heatmap, MediaStack, SimpleLine } from '../components/stats/StatsCharts'
import { findUserApi } from '@core/userService'
import { banUserApi, unbanUserApi } from '@core/friendService'
import type { AuthUser } from '@core/types/auth'
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
  const [range, setRange] = useState<DateRange>(() => rangePreset('7d'))

  const [banQuery, setBanQuery] = useState('')
  const [banTarget, setBanTarget] = useState<AuthUser | null>(null)
  const [userSummary, setUserSummary] = useState<AdminUserSummary | null>(null)
  const [banBusy, setBanBusy] = useState(false)
  const [banMessage, setBanMessage] = useState<string | null>(null)
  const [banError, setBanError] = useState<string | null>(null)

  useEffect(() => {
    if (!allowed) {
      return
    }

    async function loadStats() {
      try {
        setLoading(true)
        setError(null)
        const [totals, board, groups] = await Promise.all([
          getActivityStatsApi(range),
          getDashboardStatsApi(range),
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
  }, [allowed, range])

  if (!allowed) {
    return <Navigate to="/dashboard" replace />
  }

  async function handleFindBanTarget(event: React.FormEvent) {
    event.preventDefault()
    if (!banQuery.trim()) return
    setBanBusy(true)
    setBanError(null)
    setBanMessage(null)
    setUserSummary(null)
    try {
      const user = await findUserApi(banQuery)
      setBanTarget(user)
      try {
        setUserSummary(await getAdminUserSummaryApi(user.userId))
      } catch {
        setUserSummary(null)
      }
    } catch (err) {
      setBanTarget(null)
      setBanError(err instanceof Error ? err.message : 'Cannot find user')
    } finally {
      setBanBusy(false)
    }
  }

  async function handleBan(ban: boolean) {
    if (!banTarget) return
    const name = banTarget.displayName || banTarget.username
    if (
      ban &&
      !window.confirm(`Ban ${name}? They will be signed out and cannot log in.`)
    ) {
      return
    }
    setBanBusy(true)
    setBanError(null)
    setBanMessage(null)
    try {
      if (ban) {
        await banUserApi(banTarget.userId)
      } else {
        await unbanUserApi(banTarget.userId)
      }
      setBanMessage(ban ? `${name} was banned.` : `${name} was unbanned.`)
    } catch (err) {
      setBanError(err instanceof Error ? err.message : 'Action failed')
    } finally {
      setBanBusy(false)
    }
  }

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
          <h1>Platform stats</h1>
          <p>Admin-only aggregates across the whole platform.</p>
        </div>
      </div>

      <DateRangeFilter value={range} onApply={setRange} disabled={loading} />

      <section className="mb-4">
        <div className="activity-panel">
          <h2 className="activity-panel-title">User moderation</h2>
          <form
            className="d-flex gap-2"
            onSubmit={(event) => void handleFindBanTarget(event)}
          >
            <input
              className="app-input"
              placeholder="Username or email"
              value={banQuery}
              onChange={(event) => setBanQuery(event.target.value)}
              aria-label="Find user to moderate"
            />
            <button
              type="submit"
              className="app-button"
              disabled={banBusy || !banQuery.trim()}
            >
              Find
            </button>
          </form>

          {banTarget ? (
            <div className="d-flex align-items-center gap-3 mt-3">
              <Avatar
                name={banTarget.displayName || banTarget.username}
                src={banTarget.avatar}
                size={40}
              />
              <div className="flex-grow-1 min-w-0">
                <div className="fw-semibold text-truncate">
                  {banTarget.displayName || banTarget.username}
                </div>
                <div className="app-text-muted small">{banTarget.email}</div>
              </div>
              <button
                type="button"
                className="btn btn-outline-danger btn-sm"
                disabled={banBusy}
                onClick={() => void handleBan(true)}
              >
                Ban
              </button>
              <button
                type="button"
                className="btn btn-outline-secondary btn-sm"
                disabled={banBusy}
                onClick={() => void handleBan(false)}
              >
                Unban
              </button>
            </div>
          ) : null}

          {userSummary ? <UserActivitySummary activity={userSummary.activity} /> : null}

          {banError ? (
            <div className="auth-alert auth-alert-error mt-3" role="alert">
              {banError}
            </div>
          ) : null}
          {banMessage ? (
            <div className="auth-alert auth-alert-success mt-3" role="status">
              {banMessage}
            </div>
          ) : null}
        </div>
      </section>

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

      {!loading && !error && dashboard ? (
        <>
          <section className="mb-4">
            <div className="activity-kpi-primary">
              {metrics.slice(0, 3).map((metric) => (
                <article className="activity-metric activity-metric-primary" key={metric.key}>
                  <div className={`activity-icon ${metric.color}`}>
                    <i className={metric.icon} aria-hidden="true" />
                  </div>
                  <div className="activity-metric-body">
                    <p className="activity-label">{metric.label}</p>
                    <p className="activity-metric-value">
                      {metric.value.toLocaleString()}
                    </p>
                    <span className="activity-description">{metric.description}</span>
                  </div>
                </article>
              ))}
            </div>
            <div className="activity-kpi-secondary">
              {metrics.slice(3).map((metric) => (
                <article className="activity-metric activity-metric-secondary" key={metric.key}>
                  <div className={`activity-icon ${metric.color}`}>
                    <i className={metric.icon} aria-hidden="true" />
                  </div>
                  <div className="activity-metric-body">
                    <p className="activity-label">{metric.label}</p>
                    <p className="activity-metric-value">
                      {metric.value.toLocaleString()}
                    </p>
                    <span className="activity-description">{metric.description}</span>
                  </div>
                </article>
              ))}
              <article className="activity-metric activity-metric-secondary">
                <div className="activity-icon green">
                  <i className="bi bi-people" aria-hidden="true" />
                </div>
                <div className="activity-metric-body">
                  <p className="activity-label">DAU / WAU / MAU</p>
                  <p className="activity-metric-value">
                    {dashboard.dau} / {dashboard.wau} / {dashboard.mau}
                  </p>
                  <span className="activity-description">
                    Active users anchored to range end
                  </span>
                </div>
              </article>
              <article className="activity-metric activity-metric-secondary">
                <div className="activity-icon amber">
                  <i className="bi bi-graph-up" aria-hidden="true" />
                </div>
                <div className="activity-metric-body">
                  <p className="activity-label">Engagement</p>
                  <p className="activity-metric-value">
                    {dashboard.messagesPerActiveUser.toFixed(1)}
                  </p>
                  <span className="activity-description">
                    Msgs / active ·{' '}
                    {Math.round(dashboard.activeMessengersRate * 100)}% messengers
                  </span>
                </div>
              </article>
            </div>
          </section>

          <section className="activity-split-grid mb-4">
            <div className="activity-panel">
              <div className="activity-section-heading">
                <div>
                  <h2>New users</h2>
                  <p>Registrations by day.</p>
                </div>
              </div>
              <SimpleLine days={dashboard.newUsersByDay} label="new users" />
            </div>
            <div className="activity-panel">
              <div className="activity-section-heading">
                <div>
                  <h2>Verify funnel</h2>
                  <p>Cohort by registration day → verified → sent a message.</p>
                </div>
              </div>
              <ul className="activity-list">
                <li>
                  <span>Registered</span>
                  <strong>{dashboard.verifyFunnel.registered}</strong>
                </li>
                <li>
                  <span>Verified</span>
                  <strong>{dashboard.verifyFunnel.verified}</strong>
                </li>
                <li>
                  <span>First message</span>
                  <strong>{dashboard.verifyFunnel.firstMessage}</strong>
                </li>
              </ul>
            </div>
          </section>

          <section className="activity-split-grid mb-4">
            <div className="activity-panel">
              <div className="activity-section-heading">
                <div>
                  <h2>Social</h2>
                  <p>Friend requests in range.</p>
                </div>
              </div>
              <ul className="activity-list">
                <li>
                  <span>Requests</span>
                  <strong>{dashboard.friendRequests}</strong>
                </li>
                <li>
                  <span>Accept rate</span>
                  <strong>
                    {Math.round(dashboard.friendAcceptRate * 100)}%
                  </strong>
                </li>
                <li>
                  <span>Groups created</span>
                  <strong>{dashboard.groupsCreated}</strong>
                </li>
                <li>
                  <span>Avg members / group</span>
                  <strong>{dashboard.avgMembersPerGroup.toFixed(1)}</strong>
                </li>
              </ul>
            </div>
            <div className="activity-panel">
              <div className="activity-section-heading">
                <div>
                  <h2>Top groups</h2>
                  <p>Lifetime by message volume.</p>
                </div>
              </div>
              {(dashboard.topGroups.length ?? 0) === 0 ? (
                <p className="activity-empty">No group traffic yet.</p>
              ) : (
                <ul className="activity-list">
                  {dashboard.topGroups.map((group) => (
                    <li key={group.groupId}>
                      <span title={groupLabel(group.groupId, groupNamesById)}>
                        {groupLabel(group.groupId, groupNamesById)}
                      </span>
                      <strong>{group.total}</strong>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </section>

          <section className="activity-panel mb-4">
            <div className="activity-section-heading">
              <div>
                <h2>Messages by day</h2>
                <p>Platform-wide outbound messages.</p>
              </div>
            </div>
            <SimpleLine days={dashboard.messagesByDay} label="Messages" />
          </section>

          <section className="activity-panel mb-4">
            <div className="activity-section-heading">
              <div>
                <h2>Media uploads</h2>
                <p>By day and type.</p>
              </div>
            </div>
            <MediaStack days={dashboard.mediaByDay} />
          </section>

          <section className="activity-panel mb-4">
            <div className="activity-section-heading">
              <div>
                <h2>When the platform chats</h2>
                <p>Hour × weekday heatmap (UTC) — all users.</p>
              </div>
            </div>
            <Heatmap cells={dashboard.heatmap} />
          </section>
        </>
      ) : null}
    </main>
  )
}

function UserActivitySummary({ activity }: { activity: MyActivity }) {
  return (
    <div className="activity-user-summary mt-3">
      <h3>Activity summary</h3>
      <div className="activity-user-summary-grid">
        <span>Sent {activity.messagesSent}</span>
        <span>Received {activity.messagesReceived}</span>
        <span>Logins {activity.logins}</span>
        <span>Groups {activity.groupsCreated}</span>
        <span>Friends {activity.friends.count}</span>
      </div>
    </div>
  )
}
