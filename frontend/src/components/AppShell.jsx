import { Outlet, NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import NotificationCenter, { NavBadge } from './NotificationCenter'

const NAV = [
  { to: '/dashboard',     icon: '◈', label: 'Dashboard' },
  { to: '/transfer',      icon: '⇄', label: 'Transfer Funds' },
  { to: '/transactions',  icon: '≡', label: 'Transactions' },
  { to: '/bills',         icon: '◎', label: 'Pay Bills' },
  { to: '/beneficiaries', icon: '⊕', label: 'Beneficiaries' },
  { to: '/notifications', icon: '◬', label: 'Notifications' },
  { to: '/market',        icon: '◐', label: 'Rates & Billers' },
]

export default function AppShell() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  function handleLogout() {
    logout()
    navigate('/login')
  }

  return (
    <div className="app-shell">
      {/* ── Sidebar ── */}
      <aside className="sidebar">
        <div className="sidebar-logo">
          <div className="logo-text">Vault<span style={{ color: 'var(--accent)' }}>ora</span></div>
          <div className="logo-sub">Scalable Banking System</div>
        </div>

        <nav className="sidebar-nav">
          <div className="nav-section-title">Banking</div>
          {NAV.map(({ to, icon, label }) => (
            <NavLink
              key={to}
              to={to}
              id={`nav-${label.toLowerCase().replace(/\s+/g, '-')}`}
              className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}
            >
              <span className="nav-icon">{icon}</span>
              {label}
              {to === '/notifications' && <NavBadge />}
            </NavLink>
          ))}
        </nav>

        <div className="sidebar-footer">
          <div style={{ marginBottom: '.75rem' }}>
            <div style={{ fontSize: '.875rem', fontWeight: 600, color: '#fff' }}>{user?.fullName}</div>
            <div style={{ fontSize: '.75rem', color: 'rgba(255,255,255,.4)', marginTop: '.1rem' }}>{user?.email}</div>
          </div>
          <button
            id="btn-logout"
            className="btn btn-outline btn-sm btn-full"
            style={{ borderColor: 'rgba(255,255,255,.2)', color: 'rgba(255,255,255,.7)' }}
            onClick={handleLogout}
          >
            Sign out
          </button>
        </div>
      </aside>

      {/* ── Main area ── */}
      <main className="main-content">
        <Outlet />
      </main>
      <NotificationCenter />
    </div>
  )
}
