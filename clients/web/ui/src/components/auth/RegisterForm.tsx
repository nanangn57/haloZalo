import type { FormEvent } from 'react'

interface RegisterFormProps {
  displayName: string
  email: string
  password: string
  confirmPassword: string
  error: string
  message: string
  isLoading: boolean
  onDisplayNameChange: (value: string) => void
  onEmailChange: (value: string) => void
  onPasswordChange: (value: string) => void
  onConfirmPasswordChange: (value: string) => void
  onSubmit: (event: FormEvent<HTMLFormElement>) => void
}

function RegisterForm({
  displayName,
  email,
  password,
  confirmPassword,
  error,
  message,
  isLoading,
  onDisplayNameChange,
  onEmailChange,
  onPasswordChange,
  onConfirmPasswordChange,
  onSubmit,
}: RegisterFormProps) {
  return (
    <form className="auth-form" method="post" onSubmit={onSubmit}>
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
        <label htmlFor="displayName" className="app-form-label">
          Display name
        </label>
        <input
          id="displayName"
          className="app-input"
          value={displayName}
          onChange={(e) => onDisplayNameChange(e.target.value)}
          minLength={1}
          maxLength={64}
          autoComplete="name"
          required
        />
        <p className="auth-hint">Name your friends will see (1–64 characters).</p>
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
