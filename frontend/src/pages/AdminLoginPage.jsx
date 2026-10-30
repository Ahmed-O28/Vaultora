import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { adminApi } from '../services/api'

export default function AdminLoginPage() {
  const navigate = useNavigate()
  const [form, setForm] = useState({ username: '', password: '' })
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function handleAdminLogin(username, password) {
    setError('')
    setLoading(true)
    try {
      const data = await adminApi.login({ username, password })
      localStorage.setItem('adminToken', data.token)
      localStorage.setItem('adminUser', JSON.stringify(data))
      navigate('/admin/dashboard')
    } catch (err) {
      setError(err.message || 'Administrator authentication failed. Access denied.')
    } finally {
      setLoading(false)
    }
  }

  function handleSubmit(e) {
    e.preventDefault()
    handleAdminLogin(form.username.trim(), form.password)
  }

  return (
    <div className="auth-page">
      <div className="auth-card fade-up">
        <div className="auth-logo" style={{ color: '#0F0F0F' }}>
          Vault<span style={{ color: 'var(--accent)' }}>ora</span>
          <span style={{ fontSize: '.75rem', marginLeft: '.5rem', padding: '.15rem .5rem', background: '#0F0F0F', color: '#FFF', borderRadius: '4px', textTransform: 'uppercase', letterSpacing: '.05em' }}>
            Admin Portal
          </span>
        </div>
        <h2 style={{ marginBottom: '.35rem', fontSize: '1.5rem', fontWeight: 700 }}>Administrator Sign In</h2>
        <p style={{ marginBottom: '1.5rem', color: 'var(--text-second)', fontSize: '.9rem' }}>
          Authorized security checkpoint for distributed bank operations
        </p>

        {error && <div className="alert alert-danger" style={{ marginBottom: '1.25rem' }}>{error}</div>}

        <form onSubmit={handleSubmit} className="flex-col gap-4">
          <div className="form-group">
            <label htmlFor="admin-username">Administrator Username or Email</label>
            <input
              id="admin-username"
              name="username"
              type="text"
              autoComplete="username"
              className="input"
              placeholder="admin username"
              value={form.username}
              onChange={(e) => setForm(f => ({ ...f, username: e.target.value }))}
              required
              autoFocus
            />
          </div>
          <div className="form-group">
            <div className="flex justify-between items-center">
              <label htmlFor="admin-password" style={{ margin: 0 }}>Password</label>
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
              id="admin-password"
              name="password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="current-password"
              className="input"
              placeholder="Enter administrator password"
              value={form.password}
              onChange={(e) => setForm(f => ({ ...f, password: e.target.value }))}
              required
            />
          </div>
          <button
            id="btn-admin-login-submit"
            type="submit"
            className="btn btn-primary btn-full btn-lg"
            disabled={loading}
            style={{ marginTop: '.35rem' }}
          >
            {loading ? <span className="spinner" /> : 'Authenticate'}
          </button>
        </form>

        <div style={{ marginTop: '1.75rem', paddingTop: '1.25rem', borderTop: '1px solid var(--border)' }}>
          <p style={{ textAlign: 'center', margin: 0, color: 'var(--text-second)', fontSize: '.9rem' }}>
            Customer looking to bank?{' '}
            <Link to="/login" id="link-customer-portal" style={{ color: 'var(--accent)', fontWeight: 600 }}>
              Return to Customer Sign In
            </Link>
          </p>
        </div>
      </div>
    </div>
  )
}

