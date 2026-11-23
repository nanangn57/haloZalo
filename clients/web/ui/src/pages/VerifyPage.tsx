import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'

import { resendVerificationApi } from '@core/authService'
import { verifyAndSaveSession } from '@core/authSession'

type Status = 'pending' | 'success' | 'error'

type VerifyResult = {
  status: Exclude<Status, 'pending'>
  message: string
}

function VerifyPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const token = searchParams.get('token')?.trim() ?? ''
  const [result, setResult] = useState<VerifyResult | null>(null)
  const [resendEmail, setResendEmail] = useState('')
  const [resendMessage, setResendMessage] = useState('')
  const [resending, setResending] = useState(false)

  useEffect(() => {
    if (!token) {
      return
    }

    let cancelled = false

    // Server cấp TokenResponse khi verify: lưu session rồi vào app luôn.
    verifyAndSaveSession(token)
      .then((auth) => {
        if (cancelled) {
          return
        }
        setResult({
          status: 'success',
          message: `Email ${auth.user.email} verified. Signing you in…`,
        })
        navigate('/chat', { replace: true })
      })
      .catch((err: unknown) => {
        if (cancelled) {
          return
        }
        setResult({
          status: 'error',
          message:
            err instanceof Error
              ? err.message
              : 'Verification link is invalid or expired',
        })
      })

    return () => {
      cancelled = true
    }
  }, [navigate, token])

  async function handleResend(event: React.FormEvent) {
    event.preventDefault()
    if (!resendEmail.trim()) return
    setResending(true)
    setResendMessage('')
    try {
      await resendVerificationApi(resendEmail)
      setResendMessage('A new verification link was sent. It expires in 2 minutes.')
    } catch (err: unknown) {
      setResendMessage(err instanceof Error ? err.message : 'Could not resend email.')
    } finally {
      setResending(false)
    }
  }

  const status: Status = !token ? 'error' : (result?.status ?? 'pending')
  const message = !token
    ? 'Verification link is invalid or expired'
    : (result?.message ?? 'Verifying your email…')

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="auth-brand">
          <span className="auth-brand-mark" aria-hidden="true">
            h
          </span>
        </div>
        <h1 className="auth-title">Email verification</h1>
        <p className="auth-description">{message}</p>

        {status === 'error' ? (
          <form className="auth-form" onSubmit={(event) => void handleResend(event)}>
            <div>
              <label htmlFor="resend-email" className="app-form-label">
                Send a new link to
              </label>
              <input
                id="resend-email"
                type="email"
                className="app-input"
                placeholder="you@example.com"
                value={resendEmail}
                onChange={(event) => setResendEmail(event.target.value)}
                autoComplete="email"
              />
            </div>
            {resendMessage ? (
              <div className="auth-alert auth-alert-success" role="status">
                {resendMessage}
              </div>
            ) : null}
            <button
              type="submit"
              className="app-button app-button-primary w-100"
              disabled={resending || !resendEmail.trim()}
            >
              {resending ? 'Sending…' : 'Resend verification email'}
            </button>
          </form>
        ) : null}

        {status !== 'pending' && (
          <div className="auth-footer">
            <Link to="/login">Back to login</Link>
          </div>
        )}
      </div>
    </div>
  )
}

export default VerifyPage
