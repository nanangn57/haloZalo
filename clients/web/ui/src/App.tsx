import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";

import LoginPage from './pages/LoginPage'
import ChatPage from './pages/ChatPage'
import ContactPage from './pages/ContactPage'
import RegisterPage from './pages/RegisterPage'
import GroupPage from "./pages/GroupPage";
import './App.css'


function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* Login Page */}
        <Route path="/login" element={<LoginPage />} />

        <Route path="/chat" element={<ChatPage />} />
        <Route path="/contacts" element={<ContactPage />} />
        {/* <Route path="/settings" element={<SettingsPage />} /> */}

        <Route path="/" element={<Navigate to="/login" replace />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/groups" element={<GroupPage />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App