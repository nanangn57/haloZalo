import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, Navigate } from 'react-router-dom'

import RegisterForm from '../components/auth/RegisterForm'
import { registerApi, resendVerificationApi } from '@core/authService'
import { hasSession } from '@core/authSession'

function RegisterPage() {
  const [displayName, setDisplayName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [pendingEmail, setPendingEmail] = useState('')
  const [resending, setResending] = useState(false)

  if (hasSession()) {
    return <Navigate to="/chat" replace />
  }

  const handleResend = async () => {
    if (!pendingEmail) return
    setError('')
    try {
      setResending(true)
      await resendVerificationApi(pendingEmail)
      setMessage(`Verification email sent again to ${pendingEmail}. The link expires in 2 minutes.`)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Could not resend email.')
    } finally {
      setResending(false)
    }
  }

  const handleRegister = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError('')
    setMessage('')

    if (!displayName.trim()) {
      setError('Display name is required.')
      return
    }

    if (password.length < 8 || password.length > 72) {
      setError('Password phải có từ 8 đến 72 ký tự.')
      return
    }

    if (password !== confirmPassword) {
      setError('Password xác nhận không khớp.')
      return
    }

    try {
      setIsLoading(true)

      const result = await registerApi({
        email: email.trim(),
        password,
        displayName: displayName.trim(),
      })

      setPendingEmail(result.status === 'PENDING_VERIFICATION' ? result.email : '')
      setMessage(
        result.status === 'ACTIVE'
          ? `Account created for ${result.email}. You can login now.`
          : `Verification link sent to ${result.email}. ` +
              'Open it within 2 minutes to finish signing up.',
      )
    } catch (err: unknown) {
      setError(
        err instanceof Error
          ? err.message
          : 'Register failed. Please try again.',
      )
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
        <h1 className="auth-title">Create account</h1>
        <p className="auth-description">
          Join haloZalo to message friends and groups.
        </p>

        <RegisterForm
          displayName={displayName}
          email={email}
          password={password}
          confirmPassword={confirmPassword}
          error={error}
          message={message}
          onDisplayNameChange={setDisplayName}
          onEmailChange={setEmail}
          onPasswordChange={setPassword}
          onConfirmPasswordChange={setConfirmPassword}
          onSubmit={handleRegister}
          isLoading={isLoading}
        />

        {pendingEmail ? (
          <div className="auth-footer">
            Link expired or missing?{' '}
            <button
              type="button"
              className="btn btn-link p-0 align-baseline"
              disabled={resending}
              onClick={() => void handleResend()}
            >
              {resending ? 'Sending…' : 'Resend email'}
            </button>
          </div>
        ) : null}

        <div className="auth-footer">
          Already have an account? <Link to="/login">Login</Link>
        </div>
      </div>
    </div>
  )
}

export default RegisterPage
