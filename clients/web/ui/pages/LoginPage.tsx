import LoginForm from '../components/auth/LoginForm'

import { useState } from "react";
import { useNavigate } from "react-router-dom";
import type { FormEvent } from "react";

function LoginPage() {
  const navigate = useNavigate();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");

  const handleLogin = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const dummyEmail = "admin@gmail.com";
    const dummyPassword = "123456";
    
    if (
      email === dummyEmail &&
      password === dummyPassword
    ) {
      navigate("/contacts");
      return;
    }

    setError("Invalid username or password");
  };

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
                onEmailChange={setEmail}
                onPasswordChange={setPassword}
                onSubmit={handleLogin}
              />

              <div className="text-center mt-3">
                <span className="text-decoration-none">
                  Don't have an account?
                </span>{' '}

                <a
                  href="#"
                  className="text-decoration-none"
                >
                  Register
                </a>
              </div>

            </div>
          </div>

        </div>
      </div>
    </div>
  )
}

export default LoginPage