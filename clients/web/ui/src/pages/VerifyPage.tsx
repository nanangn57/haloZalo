import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'

import { verifyEmailApi } from '../../../../core/authService'

type Status = 'pending' | 'success' | 'error'

function VerifyPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const [status, setStatus] = useState<Status>('pending')
  const [message, setMessage] = useState('Verifying your email…')

  useEffect(() => {
    const token = searchParams.get('token')?.trim() ?? ''
    if (!token) {
      setStatus('error')
      setMessage('Verification link is invalid or expired')
      return
    }

    let cancelled = false

    verifyEmailApi(token)
      .then((user) => {
        if (cancelled) {
          return
        }
        setStatus('success')
        setMessage(
          `Email ${user.email} verified. You can sign in as ${user.username}.`,
        )
        window.setTimeout(() => {
          navigate('/login?verified=1', { replace: true })
        }, 1200)
      })
      .catch((err: unknown) => {
        if (cancelled) {
          return
        }
        setStatus('error')
        setMessage(
          err instanceof Error
            ? err.message
            : 'Verification link is invalid or expired',
        )
      })

    return () => {
      cancelled = true
    }
  }, [navigate, searchParams])

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
