import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export default function LoginPage() {
  const { login } = useAuth()
  const [form, setForm] = useState({ email: '', password: '' })
  const [showPassword, setShowPassword] = useState(false)
  const [rememberEmail, setRememberEmail] = useState(() => {
    return localStorage.getItem('vaultora_saved_email') ? true : false
  })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  // Initialize saved email if remembered
  useState(() => {
    const saved = localStorage.getItem('vaultora_saved_email')
    if (saved) setForm(f => ({ ...f, email: saved }))
  })

  async function handleLogin(email, password) {
    setError('')
    setLoading(true)
    try {
      if (rememberEmail) {
        localStorage.setItem('vaultora_saved_email', email)
      } else {
        localStorage.removeItem('vaultora_saved_email')
      }
      await login(email, password)
    } catch (err) {
      setError(err.message || 'Authentication failed. Please verify your email and password.')
    } finally {
      setLoading(false)
    }
  }

  function handleSubmit(e) {
    e.preventDefault()
    handleLogin(form.email.trim(), form.password)
  }

  return (
    <div className="auth-page">
      <div className="auth-card fade-up">
        <div className="auth-logo">Vault<span>ora</span></div>
        <h2 style={{ marginBottom: '.35rem', fontSize: '1.5rem', fontWeight: 700 }}>Sign In</h2>
        <p style={{ marginBottom: '1.5rem', color: 'var(--text-second)', fontSize: '.9rem' }}>
          Enter your registered email and password to access your accounts
        </p>

        {error && <div className="alert alert-danger" style={{ marginBottom: '1.25rem' }}>{error}</div>}

        <form onSubmit={handleSubmit} className="flex-col gap-4">
          <div className="form-group">
            <label htmlFor="login-email">Email Address</label>
            <input
              id="login-email"
              name="email"
              type="email"
              autoComplete="email"
              className="input"
              placeholder="name@example.com"
              value={form.email}
              onChange={(e) => setForm(f => ({ ...f, email: e.target.value }))}
              required
              autoFocus
            />
          </div>

          <div className="form-group">
            <div className="flex justify-between items-center">
              <label htmlFor="login-password" style={{ margin: 0 }}>Password</label>
              <button
                type="button"
                onClick={() => setShowPassword(s => !s)}
                style={{
                  background: 'none',
                  border: 'none',
                  color: 'var(--accent)',
                  fontSize: '.75rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                  padding: 0
                }}
              >
                {showPassword ? 'Hide' : 'Show'}
              </button>
            </div>
            <input
              id="login-password"
              name="password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="current-password"
              className="input"
              placeholder="Enter your password"
              value={form.password}
              onChange={(e) => setForm(f => ({ ...f, password: e.target.value }))}
              required
            />
          </div>

          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', fontSize: '.825rem' }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: '.45rem', cursor: 'pointer', color: 'var(--text-second)', fontWeight: 400 }}>
              <input
                type="checkbox"
                checked={rememberEmail}
                onChange={(e) => setRememberEmail(e.target.checked)}
                style={{ cursor: 'pointer', accentColor: 'var(--accent)' }}
              />
              Remember my email
            </label>
          </div>

          <button
            id="btn-login-submit"
            type="submit"
            className="btn btn-primary btn-full btn-lg"
            disabled={loading}
            style={{ marginTop: '.35rem' }}
          >
            {loading ? <span className="spinner" /> : 'Sign In'}
          </button>
        </form>

        <div style={{ marginTop: '1.75rem', paddingTop: '1.25rem', borderTop: '1px solid var(--border)' }}>
          <p style={{ textAlign: 'center', margin: 0, color: 'var(--text-second)', fontSize: '.9rem' }}>
            New to Vaultora?{' '}
            <Link to="/register" id="link-register" style={{ color: 'var(--accent)', fontWeight: 600 }}>
              Open an Account
            </Link>
          </p>

          <p style={{ textAlign: 'center', marginTop: '.75rem', marginBottom: 0, fontSize: '.8rem' }}>
            <Link to="/admin/login" id="link-admin-portal" style={{ color: 'var(--text-muted)' }}>
              Bank Staff? Access Administrator Portal →
            </Link>
          </p>
        </div>
      </div>
    </div>
  )
}

