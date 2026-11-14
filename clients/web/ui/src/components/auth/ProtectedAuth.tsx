
import { Navigate, Outlet } from 'react-router-dom'
import { getAuth } from '@core/authSession'

function ProtectedRoute() {
  const auth = getAuth()

  if (!auth?.user?.userId) {
    return <Navigate to="/login" replace />
  }

  return <Outlet />
}

export default ProtectedRoute
