import type { LoginFormProps } from "../../types/auth"

function LoginForm({
  email,
  password,
  error,
  onEmailChange,
  onPasswordChange,
  onSubmit,
}: LoginFormProps) {
  return (
    <form onSubmit={onSubmit}>
      {/* Email */}
      <div className="mb-3">
        <label
          htmlFor="email"
          className="form-label"
        >
          Email
        </label>

        <input
          id="email"
          type="email"
          className="form-control input-placeholder"
          placeholder="Enter your email"
          value={email}
          onChange={(event) => onEmailChange(event.target.value)}
        />
      </div>

      {/* Password */}
      <div className="mb-3">
        <label
          htmlFor="password"
          className="form-label"
        >
          Password
        </label>

        <input
          id="password"
          type="password"
          className="form-control input-placeholder"
          placeholder="Enter your password"
          value={password}
          onChange={(event) => onPasswordChange(event.target.value)}
        />
      </div>

      {/* Error */}
      {error && (
        <div className="text-danger mb-3">
          {error}
        </div>
      )}

      {/* Forgot password */}
      <div className="text-end mb-3">
        <a href="#" className="text-decoration-none">
          Forgot password?
        </a>
      </div>

      {/* Login button */}
      <button
        type="submit"
        className="btn btn-primary w-100"
      >
        Login
      </button>
    </form>
  )
}

export default LoginForm