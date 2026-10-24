
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
    <form onSubmit={onSubmit}>
      <div className="mb-3">
        <label htmlFor="username" className="form-label">
          Username
        </label>
        <input
          id="username"
          className="form-control"
          value={username}
          onChange={(e) => onUsernameChange(e.target.value)}
          minLength={3}
          maxLength={32}
          pattern="[a-zA-Z0-9_]+"
          required
        />
        <div className="form-text">
          3–32 ký tự, chỉ gồm chữ cái, số và dấu gạch dưới.
        </div>
      </div>

      <div className="mb-3">
        <label htmlFor="email" className="form-label">
          Email
        </label>
        <input
          id="email"
          type="email"
          className="form-control"
          value={email}
          onChange={(e) => onEmailChange(e.target.value)}
          maxLength={254}
          required
        />
      </div>

      <div className="mb-3">
        <label htmlFor="password" className="form-label">
          Password
        </label>
        <input
          id="password"
          type="password"
          className="form-control"
          value={password}
          onChange={(e) => onPasswordChange(e.target.value)}
          minLength={8}
          maxLength={72}
          required
        />
      </div>

      <div className="mb-3">
        <label htmlFor="confirmPassword" className="form-label">
          Confirm password
        </label>
        <input
          id="confirmPassword"
          type="password"
          className="form-control"
          value={confirmPassword}
          onChange={(e) => onConfirmPasswordChange(e.target.value)}
          required
        />
      </div>

      {error && (
        <div className="alert alert-danger" role="alert">
          {error}
        </div>
      )}

      {message && (
        <div className="alert alert-success" role="status">
          {message}
        </div>
      )}

      <button type="submit" 
      className="btn btn-primary w-100"
      disabled={isLoading}>
        {isLoading ? 'Registering...' : 'Register'}
      </button>
    </form>
  )
}

export default RegisterForm
