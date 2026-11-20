import type { FormEvent } from 'react'

interface RegisterFormProps {
  username: string
  email: string
  password: string
  confirmPassword: string
  error: string
  message: string
  isLoading: boolean
  onUsernameChange: (value: string) => void
  onEmailChange: (value: string) => void
  onPasswordChange: (value: string) => void
  onConfirmPasswordChange: (value: string) => void
  onSubmit: (event: FormEvent<HTMLFormElement>) => void
}

function RegisterForm({
  username,
  email,
  password,
  confirmPassword,
  error,
  message,
  isLoading,
  onUsernameChange,
  onEmailChange,
  onPasswordChange,
  onConfirmPasswordChange,
  onSubmit,
}: RegisterFormProps) {
  return (
    <form className="auth-form" onSubmit={onSubmit}>
      <div>
        <label htmlFor="username" className="app-form-label">
          Username
        </label>
        <input
          id="username"
          className="app-input"
          value={username}
          onChange={(e) => onUsernameChange(e.target.value)}
          minLength={3}
          maxLength={32}
          pattern="[a-zA-Z0-9_]+"
          autoComplete="username"
          required
        />
        <p className="auth-hint">
          3–32 characters: letters, numbers, and underscore only.
        </p>
      </div>

      <div>
        <label htmlFor="email" className="app-form-label">
          Email
        </label>
        <input
          id="email"
          type="email"
          className="app-input"
          value={email}
          onChange={(e) => onEmailChange(e.target.value)}
          maxLength={254}
          autoComplete="email"
          required
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
          value={password}
          onChange={(e) => onPasswordChange(e.target.value)}
          minLength={8}
          maxLength={72}
          autoComplete="new-password"
          required
        />
      </div>

      <div>
        <label htmlFor="confirmPassword" className="app-form-label">
          Confirm password
        </label>
        <input
          id="confirmPassword"
          type="password"
          className="app-input"
          value={confirmPassword}
          onChange={(e) => onConfirmPasswordChange(e.target.value)}
          autoComplete="new-password"
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
        disabled={isLoading}
      >
        {isLoading ? 'Creating account…' : 'Create account'}
      </button>
    </form>
  )
}

export default RegisterForm
