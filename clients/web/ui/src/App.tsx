
import {
  BrowserRouter,
  Routes,
  Route,
  Navigate,
  Outlet,
} from 'react-router-dom'

import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import VerifyPage from './pages/VerifyPage'
import ChatPage from './pages/ChatPage'
import ContactPage from './pages/ContactPage'
import GroupPage from './pages/GroupPage'
import DashboardPage from './pages/DashboardPage'
import AdminStatsPage from './pages/AdminStatsPage'
import FriendRequestPage from './pages/FriendRequestPage'
import SettingsPage from './pages/SettingsPage'
import ForgotPasswordPage from './pages/ForgotPasswordPage'

import MainLayout from './layouts/MainLayout'
import ContactLayout from './layouts/ContactLayout'

import { hasSession } from '@core/authSession'

function ProtectedLayout() {
  if (!hasSession()) {
    return <Navigate to="/login" replace />
  }

  return (
    <MainLayout>
      <Outlet />
    </MainLayout>
  )
}

// Có token thì vào /chat, chưa có thì /login.
function RootRedirect() {
  return <Navigate to={hasSession() ? '/chat' : '/login'} replace />
}

function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* Public routes */}
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/auth/verify" element={<VerifyPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        {/* Link trong email đặt lại mật khẩu: /reset-password?token= */}
        <Route path="/reset-password" element={<ForgotPasswordPage />} />

        {/* Protected routes */}
        <Route element={<ProtectedLayout />}>
          {/* Personal activity */}
          <Route path="/dashboard" element={<DashboardPage />} />

          {/* Platform admin stats */}
          <Route path="/admin" element={<AdminStatsPage />} />

          {/* Sessions, password, notifications */}
          <Route path="/settings" element={<SettingsPage />} />

          {/* Normal chat */}
          <Route path="/chat" element={<ChatPage />} />

          {/* Contact layout */}
          <Route path="/contacts" element={<ContactLayout />}>
            <Route
              index
              element={<Navigate to="friends" replace />}
            />

            <Route path="friends" element={<ContactPage />} />
            <Route path="groups" element={<GroupPage />} />

            {/* Chat opened from GroupPage */}
            <Route
              path="groups/:groupId/chat"
              element={<ChatPage />}
            />

            <Route path="requests" element={<FriendRequestPage />} />
          </Route>
        </Route>

        {/* Default routes */}
        <Route path="/" element={<RootRedirect />} />
        <Route path="*" element={<RootRedirect />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
