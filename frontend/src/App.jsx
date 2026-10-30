import { useState, useEffect } from 'react'
import { BrowserRouter, Routes, Route, Navigate, useLocation, useParams, useNavigate } from 'react-router-dom'
import { AuthProvider, useAuth } from './context/AuthContext'
import { shortener } from './services/api'
import AppShell from './components/AppShell'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import DashboardPage from './pages/DashboardPage'
import TransferPage from './pages/TransferPage'
import TransactionsPage from './pages/TransactionsPage'
import BillPayPage from './pages/BillPayPage'
import BeneficiariesPage from './pages/BeneficiariesPage'
import ReceiptViewPage from './pages/ReceiptViewPage'
import NotificationsPage from './pages/NotificationsPage'
import MarketDataPage from './pages/MarketDataPage'
import AdminLoginPage from './pages/AdminLoginPage'
import AdminDashboardPage from './pages/AdminDashboardPage'

function ProtectedRoute({ children }) {
  const { user, loading } = useAuth()
  if (loading) return <div className="loading-center"><div className="spinner spinner-lg" /></div>
  return user ? children : <Navigate to="/login" replace />
}

function PublicRoute({ children }) {
  const { user, loading } = useAuth()
  const location = useLocation()
  if (loading) return null
  const destination = location.state?.from || '/dashboard'
  return user ? <Navigate to={destination} replace /> : children
}

function AdminProtectedRoute({ children }) {
  const token = typeof window !== 'undefined' ? localStorage.getItem('adminToken') : null
  return token ? children : <Navigate to="/admin/login" replace />
}

function AdminPublicRoute({ children }) {
  const token = typeof window !== 'undefined' ? localStorage.getItem('adminToken') : null
  return token ? <Navigate to="/admin/dashboard" replace /> : children
}

function ShortUrlRedirect() {
  const { shortCode } = useParams()
  const navigate = useNavigate()
  const [error, setError] = useState(null)

  useEffect(() => {
    if (!shortCode) return
    shortener.getMetadata(shortCode)
      .then((meta) => {
        if (meta?.targetUrl) {
          if (meta.targetUrl.startsWith('/')) {
            navigate(meta.targetUrl, { replace: true })
          } else {
            window.location.replace(meta.targetUrl)
          }
        } else if (meta?.transactionId) {
          navigate(`/receipts/${meta.transactionId}`, { replace: true })
        } else {
          navigate('/dashboard', { replace: true })
        }
      })
      .catch((err) => {
        setError(err.message || 'Short link not found or expired')
      })
  }, [shortCode, navigate])

  if (error) {
    return (
      <div className="auth-page">
        <div className="auth-card fade-up" style={{ textAlign: 'center', maxWidth: '440px', margin: '3rem auto', padding: '2rem' }}>
          <div style={{ fontSize: '1.5rem', marginBottom: '1rem', opacity: 0.3 }}>—</div>
          <h3 style={{ marginBottom: '.5rem', color: 'var(--text-main)' }}>Link Unavailable</h3>
          <p style={{ color: 'var(--danger)', fontSize: '.875rem', marginBottom: '1.5rem' }}>{error}</p>
          <button onClick={() => navigate('/dashboard')} className="btn btn-primary btn-full">
            Return to Dashboard
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="loading-center" style={{ minHeight: '60vh', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
      <div className="spinner spinner-lg" />
      <p style={{ marginTop: '1rem', color: 'var(--text-second)', fontSize: '.9rem' }}>Resolving secure link…</p>
    </div>
  )
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login"    element={<PublicRoute><LoginPage /></PublicRoute>} />
          <Route path="/register" element={<PublicRoute><RegisterPage /></PublicRoute>} />
          <Route path="/admin/login" element={<AdminPublicRoute><AdminLoginPage /></AdminPublicRoute>} />
          <Route path="/admin/dashboard" element={<AdminProtectedRoute><AdminDashboardPage /></AdminProtectedRoute>} />
          <Route path="/admin" element={<Navigate to="/admin/dashboard" replace />} />
          <Route path="/receipts/:transactionId" element={<ReceiptViewPage />} />
          <Route path="/r/:shortCode" element={<ShortUrlRedirect />} />
          <Route path="/" element={<ProtectedRoute><AppShell /></ProtectedRoute>}>
            <Route index element={<Navigate to="/dashboard" replace />} />
            <Route path="dashboard"     element={<DashboardPage />} />
            <Route path="transfer"      element={<TransferPage />} />
            <Route path="transactions"  element={<TransactionsPage />} />
            <Route path="bills"         element={<BillPayPage />} />
            <Route path="beneficiaries" element={<BeneficiariesPage />} />
            <Route path="notifications"  element={<NotificationsPage />} />
            <Route path="market"         element={<MarketDataPage />} />
          </Route>
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
