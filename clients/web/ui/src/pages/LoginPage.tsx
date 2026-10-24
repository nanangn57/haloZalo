import { useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate, Link } from 'react-router-dom'

import LoginForm from '../components/auth/LoginForm'
import { loginApi } from '../../core/authService'
import { saveAuth } from '../../core/authSession'

function LoginPage() {
  const navigate = useNavigate()

  // Dữ liệu người dùng nhập vào form
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  // Trạng thái lỗi và loading
  const [error, setError] = useState('')
  const [isLoading, setIsLoading] = useState(false)

  const handleLogin = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError('')

    // API quy định password từ 8 đến 72 ký tự
    if (password.length < 8 || password.length > 72) {
      setError('Password must be between 8 and 72 characters')
      return
    }

    try {
      setIsLoading(true)

      // API nhận login là username hoặc email
      const auth = await loginApi({
        login: email.trim(),
        password,
      })

      // Kiểm tra API có trả về access token không
      console.log('Login response:', {
        hasAccessToken: Boolean(auth.accessToken),
        tokenType: auth.tokenType,
        expiresIn: auth.expiresIn,
        user: auth.user,
      })

      if (!auth.accessToken) {
        throw new Error('Login response does not contain an access token')
      }

      // Chỉ lưu phiên đăng nhập sau khi API thành công
      saveAuth(auth)

      // Chuyển trang sau khi đăng nhập thành công
      navigate('/contacts')
    } catch (err: unknown) {
      setError(
        err instanceof Error
          ? err.message
          : 'Unable to log in. Please try again.'
      )
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="container">
      <div className="row justify-content-center mt-5">
        <div className="col-12 col-sm-8 col-md-6 col-lg-4">

          <div className="card shadow-sm">
            <div className="card-body p-4">

              <h2 className="text-center mb-4">
                Login
              </h2>

              <LoginForm
                email={email}
                password={password}
                error={error}
                onUsernameChange={setEmail}
                onPasswordChange={setPassword}
                onSubmit={handleLogin}
                isLoading={isLoading}
              />

              <div className="text-center mt-3">
                <span className="text-decoration-none">
                  Don't have an account?
                </span>{' '}

                <Link
                  to="/register"
                  className="text-decoration-none fw-semibold"
                >
                  Register
                </Link>
              </div>

            </div>
          </div>

        </div>
      </div>
    </div>
  )
}

export default LoginPage