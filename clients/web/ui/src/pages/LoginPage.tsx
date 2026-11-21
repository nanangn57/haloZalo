import { useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate, Link, useSearchParams } from 'react-router-dom'

import LoginForm from '../components/auth/LoginForm'
import { loginApi } from '../../../../core/authService'
import { saveAuth } from '../../../../core/authSession'

function LoginPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const verified = searchParams.get('verified') === '1'

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [isLoading, setIsLoading] = useState(false)

  const handleLogin = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError('')

    if (password.length < 8 || password.length > 72) {
      setError('Password must be between 8 and 72 characters')
      return
    }

    try {
      setIsLoading(true)

      const auth = await loginApi({
        login: email.trim(),
        password,
      })

      if (!auth.accessToken) {
        throw new Error('Login response does not contain an access token')
      }

      saveAuth(auth)
      navigate('/chat', { replace: true })
    } catch (err: unknown) {
      setError(
        err instanceof Error
          ? err.message
          : 'Unable to log in. Please try again.',
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
        <h1 className="auth-title">Welcome back</h1>
        <p className="auth-description">
          Sign in to continue chatting on haloZalo.
        </p>
        {verified && (
          <p className="auth-description" role="status">
            Email verified. Sign in with your password.
          </p>
        )}

        <LoginForm
          email={email}
          password={password}
          error={error}
          onUsernameChange={setEmail}
          onPasswordChange={setPassword}
          onSubmit={handleLogin}
          isLoading={isLoading}
        />

        <div className="auth-footer">
          Don&apos;t have an account?{' '}
          <Link to="/register">Register</Link>
        </div>
      </div>
    </div>
  )
}

export default LoginPage
