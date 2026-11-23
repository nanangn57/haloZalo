import { Link } from 'react-router-dom'
import type { LoginFormProps } from '../../types/auth'

function LoginForm({
  email,
  password,
  error,
  onEmailChange,
  onPasswordChange,
  onSubmit,
  isLoading,
}: LoginFormProps) {
  return (
    <form className="auth-form" onSubmit={onSubmit}>
      <div>
        <label htmlFor="email" className="app-form-label">
          Email
        </label>
        <input
          id="email"
          type="email"
          className="app-input"
          placeholder="you@example.com"
          value={email}
          autoComplete="email"
          onChange={(event) => onEmailChange(event.target.value)}
        />
      </div>

      <div>
        <label htmlFor="password" className="app-form-label">
          Password
        </label>
        <input
          id="password"
          type="password"
          className="app-input"
          placeholder="At least 8 characters"
          value={password}
          autoComplete="current-password"
          onChange={(event) => onPasswordChange(event.target.value)}
        />
      </div>

      <div className="text-end">
        <Link to="/forgot-password" className="small">
          Forgot password?
        </Link>
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
        {isLoading ? 'Signing in…' : 'Sign in'}
      </button>
    </form>
  )
}

export default LoginForm
