import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export default function RegisterPage() {
  const { register } = useAuth()
  const [form, setForm] = useState({ fullName: '', email: '', password: '', confirm: '', mpin: '' })
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    if (form.password !== form.confirm) {
      setError('Passwords do not match.')
      return
    }
    if (form.password.length < 6) {
      setError('Password must be at least 6 characters.')
      return
    }
    if (!/^\d{4,6}$/.test(form.mpin)) {
      setError('MPIN must be 4 to 6 numeric digits.')
      return
    }
    setLoading(true)
    try {
      await register(form.fullName.trim(), form.email.trim(), form.password, form.mpin.trim())
    } catch (err) {
      setError(err.message || 'Registration failed. Please check the information provided.')
    } finally {
      setLoading(false)
    }
  }

  const update = (field) => (e) => setForm(f => ({ ...f, [field]: e.target.value }))

  return (
    <div className="auth-page">
      <div className="auth-card fade-up">
        <div className="auth-logo">Vault<span>ora</span></div>
        <h2 style={{ marginBottom: '.35rem', fontSize: '1.5rem', fontWeight: 700 }}>Open an Account</h2>
        <p style={{ marginBottom: '1.5rem', color: 'var(--text-second)', fontSize: '.9rem' }}>
          Create your digital banking profile with instant account provisioning
        </p>

        {error && <div className="alert alert-danger" style={{ marginBottom: '1.25rem' }}>{error}</div>}

        <form onSubmit={handleSubmit} className="flex-col gap-4">
          <div className="form-group">
            <label htmlFor="reg-name">Full Legal Name</label>
            <input
              id="reg-name"
              name="fullName"
              type="text"
              autoComplete="name"
              className="input"
              placeholder="e.g. Johnathan Smith"
              value={form.fullName}
              onChange={update('fullName')}
              required
              autoFocus
            />
          </div>

          <div className="form-group">
            <label htmlFor="reg-email">Email Address</label>
            <input
              id="reg-email"
              name="email"
              type="email"
              autoComplete="email"
              className="input"
              placeholder="name@example.com"
              value={form.email}
              onChange={update('email')}
              required
            />
          </div>

          <div className="form-group">
            <div className="flex justify-between items-center">
              <label htmlFor="reg-password" style={{ margin: 0 }}>Password</label>
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
              id="reg-password"
              name="password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="new-password"
              className="input"
              placeholder="Minimum 6 characters"
              value={form.password}
              onChange={update('password')}
              required
            />
          </div>

          <div className="form-group">
            <label htmlFor="reg-confirm">Confirm Password</label>
            <input
              id="reg-confirm"
              name="confirmPassword"
              type={showPassword ? 'text' : 'password'}
              autoComplete="new-password"
              className="input"
              placeholder="Re-enter password"
              value={form.confirm}
              onChange={update('confirm')}
              required
            />
          </div>

          {/* 4-Digit Security MPIN */}
          <div className="form-group">
            <div className="flex justify-between items-center" style={{ marginBottom: '.35rem' }}>
              <label htmlFor="reg-mpin" style={{ margin: 0 }}>4-Digit Transaction MPIN</label>
              <span style={{ fontSize: '.75rem', color: 'var(--text-muted)' }}>4 to 6 numbers</span>
            </div>
            <input
              id="reg-mpin"
              name="mpin"
              type="password"
              inputMode="numeric"
              maxLength={6}
              className="input"
              placeholder="Set 4-digit PIN"
              value={form.mpin}
              onChange={(e) => {
                const val = e.target.value.replace(/\D/g, '')
                setForm(f => ({ ...f, mpin: val }))
              }}
              required
            />
            <small style={{ color: 'var(--text-muted)', fontSize: '.75rem', marginTop: '.25rem', display: 'block' }}>
              Used to verify your identity when checking balances and transferring money.
            </small>
          </div>

          <button
            id="btn-register-submit"
            type="submit"
            className="btn btn-primary btn-full btn-lg"
            disabled={loading}
            style={{ marginTop: '.35rem' }}
          >
            {loading ? <span className="spinner" /> : 'Create Account'}
          </button>
        </form>

        <div style={{ marginTop: '1.75rem', paddingTop: '1.25rem', borderTop: '1px solid var(--border)' }}>
          <p style={{ textAlign: 'center', margin: 0, color: 'var(--text-second)', fontSize: '.9rem' }}>
            Already have an account?{' '}
            <Link to="/login" id="link-login" style={{ color: 'var(--accent)', fontWeight: 600 }}>
              Sign In
            </Link>
          </p>
        </div>
      </div>
    </div>
  )
}

