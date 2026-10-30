import { useState, useEffect, useCallback } from 'react'
import { billerApi } from '../services/api'

function fmtDate(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('en-US', {
    month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit',
  })
}

export default function MarketDataPage() {
  const [billers, setBillers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setError('')
    try {
      const b = await billerApi.getBillers()
      setBillers(b.billers || [])
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { load() }, [load])

  return (
    <>
      <div className="page-header">
        <div>
          <h3 style={{ margin: 0 }}>Partner Billers Directory</h3>
          <p style={{ margin: 0, fontSize: '.85rem' }}>
            Official directory of verified billers and their real-time payment acceptance status
          </p>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '.75rem' }}>
          <button id="btn-refresh-billers" className="btn btn-secondary btn-sm"
            onClick={load} disabled={loading}>
            ↻ Refresh Directory
          </button>
        </div>
      </div>

      <div className="page-body">
        {error && <div className="alert alert-danger" style={{ marginBottom: '1rem' }}>{error}</div>}

        {loading ? (
          <div className="loading-center"><div className="spinner spinner-lg" /></div>
        ) : (
          <div className="card fade-up">
            <h4 style={{ marginBottom: '.75rem' }}>Registered Partner Billers ({billers.length})</h4>
            <div className="table-wrap">
              <table id="billers-table" className="table">
                <thead><tr><th>Biller ID</th><th>Biller Name</th><th>Category</th><th>Payment Status</th><th>Last Updated</th></tr></thead>
                <tbody>
                  {billers.length === 0 && (
                    <tr><td colSpan={5} style={{ color: 'var(--text-muted)', textAlign: 'center', padding: '1.5rem' }}>No billers found.</td></tr>
                  )}
                  {billers.map((b) => {
                    const isActive = b.status === 'ACTIVE'
                    return (
                      <tr key={b.billerId}>
                        <td>
                          <code>{b.billerId}</code>
                        </td>
                        <td>
                          <strong>{b.name}</strong>
                        </td>
                        <td><span className="badge badge-neutral">{b.category}</span></td>
                        <td>
                          <span className={`badge ${isActive ? 'badge-success' : 'badge-danger'}`}>
                            {isActive ? 'ACTIVE' : 'INACTIVE'}
                          </span>
                        </td>
                        <td style={{ fontSize: '.8rem', color: 'var(--text-muted)' }}>
                          {fmtDate(b.lastUpdated)}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>
    </>
  )
}
