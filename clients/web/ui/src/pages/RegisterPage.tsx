import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'

import RegisterForm from '../components/auth/RegisterForm'
import { registerApi } from '../../../../core/authService'

function RegisterPage() {
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [isLoading, setIsLoading] = useState(false)

  const handleRegister = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError('')
    setMessage('')

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
        username: username.trim(),
        email: email.trim(),
        password,
      })

      setMessage(
        `Register request sent to ${result.email}. ` +
          'Please check your email and complete verification before login',
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
          username={username}
          email={email}
          password={password}
          confirmPassword={confirmPassword}
          error={error}
          message={message}
          onUsernameChange={setUsername}
          onEmailChange={setEmail}
          onPasswordChange={setPassword}
          onConfirmPasswordChange={setConfirmPassword}
          onSubmit={handleRegister}
          isLoading={isLoading}
        />

        <div className="auth-footer">
          Already have an account? <Link to="/login">Login</Link>
        </div>
      </div>
    </div>
  )
}

export default RegisterPage
