import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'

import { forgotPasswordApi, resetPasswordApi } from '@core/authService'

// /forgot-password        → nhập email nhận link
// /forgot-password?token= → đặt mật khẩu mới (link trong email)
function ForgotPasswordPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')?.trim() ?? ''

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [isLoading, setIsLoading] = useState(false)

  async function handleForgot(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setMessage('')
    try {
      setIsLoading(true)
      await forgotPasswordApi(email)
      // Server luôn 202 và không lộ email có tồn tại hay không.
      setMessage('If that email has an account, a reset link is on its way.')
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Could not send reset email.')
    } finally {
      setIsLoading(false)
    }
  }

  async function handleReset(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setMessage('')

    if (password.length < 8 || password.length > 72) {
      setError('Password must be between 8 and 72 characters')
      return
    }
    if (password !== confirmPassword) {
      setError('Passwords do not match.')
      return
    }

    try {
      setIsLoading(true)
      await resetPasswordApi(token, password)
      navigate('/login?reset=1', { replace: true })
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Could not reset password.')
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="auth-brand">
          <span className="auth-brand-mark" aria-hidden="true">
            h
          </span>
        </div>
        <h1 className="auth-title">{token ? 'Set a new password' : 'Forgot password'}</h1>
        <p className="auth-description">
          {token
            ? 'Choose a new password for your account.'
            : 'Enter your email and we will send you a reset link.'}
        </p>

        {token ? (
          <form className="auth-form" onSubmit={(event) => void handleReset(event)}>
            <div>
              <label htmlFor="new-password" className="app-form-label">
                New password
              </label>
              <input
                id="new-password"
                type="password"
                className="app-input"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                minLength={8}
                maxLength={72}
                autoComplete="new-password"
                required
              />
            </div>
            <div>
              <label htmlFor="confirm-new-password" className="app-form-label">
                Confirm password
              </label>
              <input
                id="confirm-new-password"
                type="password"
                className="app-input"
                value={confirmPassword}
                onChange={(event) => setConfirmPassword(event.target.value)}
                autoComplete="new-password"
                required
              />
            </div>
            {error ? (
              <div className="auth-alert auth-alert-error" role="alert">
                {error}
              </div>
            ) : null}
            <button
              type="submit"
              className="app-button app-button-primary w-100"
              disabled={isLoading}
            >
              {isLoading ? 'Saving…' : 'Update password'}
            </button>
          </form>
        ) : (
          <form className="auth-form" onSubmit={(event) => void handleForgot(event)}>
            <div>
              <label htmlFor="forgot-email" className="app-form-label">
                Email
              </label>
              <input
                id="forgot-email"
                type="email"
                className="app-input"
                placeholder="you@example.com"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                maxLength={254}
                autoComplete="email"
                required
              />
            </div>
            {error ? (
              <div className="auth-alert auth-alert-error" role="alert">
                {error}
              </div>
            ) : null}
            {message ? (
              <div className="auth-alert auth-alert-success" role="status">
                {message}
              </div>
            ) : null}
            <button
              type="submit"
              className="app-button app-button-primary w-100"
              disabled={isLoading || !email.trim()}
            >
              {isLoading ? 'Sending…' : 'Send reset link'}
            </button>
          </form>
        )}

        <div className="auth-footer">
          <Link to="/login">Back to login</Link>
        </div>
      </div>
    </div>
  )
}

export default ForgotPasswordPage
