
import {
  BrowserRouter,
  Routes,
  Route,
  Navigate,
  Outlet,
} from 'react-router-dom'

import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import ChatPage from './pages/ChatPage'
import ContactPage from './pages/ContactPage'
import GroupPage from './pages/GroupPage'

import MainLayout from './layouts/MainLayout'
import ContactLayout from './layouts/ContactLayout'

import { getAuth } from '@core/authSession'

function ProtectedLayout() {
  const auth = getAuth()

  if (!auth?.user?.userId) {
    return <Navigate to="/login" replace />
  }

  return (
    <MainLayout>
      <Outlet />
    </MainLayout>
  )
}

function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* Public routes */}
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />

        {/* Protected routes */}
        <Route element={<ProtectedLayout />}>
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

            {/* <Route path="requests" element={<FriendRequestPage />} /> */}
          </Route>
        </Route>

        {/* Default routes */}
        <Route path="/" element={<Navigate to="/chat" replace />} />
        <Route path="*" element={<Navigate to="/chat" replace />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
