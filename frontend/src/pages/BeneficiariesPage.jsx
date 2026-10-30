import { useState, useEffect, useRef } from 'react'
import { useAuth } from '../context/AuthContext'
import { beneficiaries as beneApi, accounts as accsApi } from '../services/api'

function fmtDate(iso) {
  return new Date(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}

export default function BeneficiariesPage() {
  const { user } = useAuth()
  const [benes, setBenes]   = useState([])
  const [form, setForm]     = useState({ beneficiaryAccountNumber: '', nickname: '' })
  const [error, setError]   = useState('')
  const [success, setSuccess] = useState('')
  const [loading, setLoading] = useState(false)
  const [listLoading, setListLoading] = useState(true)
  const isSubmittingRef = useRef(false)

  async function loadBeneficiaries() {
    try {
      const data = await beneApi.getAll(user.customerId)
      setBenes(data || [])
    } catch (err) {
      setError(err.message)
    } finally {
      setListLoading(false)
    }
  }

  useEffect(() => { loadBeneficiaries() }, [user.customerId])

  async function handleSubmit(e) {
    e.preventDefault()
    if (loading || isSubmittingRef.current) return
    isSubmittingRef.current = true
    setError(''); setSuccess('')
    setLoading(true)
    try {
      await beneApi.add(user.customerId, form)
      setSuccess(`Beneficiary "${form.nickname || form.beneficiaryAccountNumber}" added successfully.`)
      setForm({ beneficiaryAccountNumber: '', nickname: '' })
      await loadBeneficiaries()
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
      isSubmittingRef.current = false
    }
  }

  return (
    <>
      <div className="page-header">
        <div>
          <h3 style={{ margin: 0 }}>Beneficiaries</h3>
          <p style={{ margin: 0, fontSize: '.85rem' }}>Manage your saved payees</p>
        </div>
      </div>

      <div className="page-body">
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1.75fr', gap: '1.5rem', alignItems: 'start' }}>

          {/* Add form */}
          <div className="card fade-up">
            <h4 style={{ marginBottom: '1.5rem' }}>Add beneficiary</h4>
            {error   && <div className="alert alert-danger"  style={{ marginBottom: '1rem' }}>{error}</div>}
            {success && <div className="alert alert-success" style={{ marginBottom: '1rem' }}>{success}</div>}
            <form onSubmit={handleSubmit} className="flex-col gap-4">
              <div className="form-group">
                <label htmlFor="bene-account">Account number</label>
                <input id="bene-account" type="text" className="input"
                  placeholder="e.g. ACC-00000003"
                  value={form.beneficiaryAccountNumber}
                  onChange={e => setForm(f => ({ ...f, beneficiaryAccountNumber: e.target.value }))}
                  required />
              </div>
              <div className="form-group">
                <label htmlFor="bene-nick">Nickname (optional)</label>
                <input id="bene-nick" type="text" className="input"
                  placeholder="e.g. Mom's Account"
                  value={form.nickname}
                  onChange={e => setForm(f => ({ ...f, nickname: e.target.value }))} />
              </div>
              <button id="btn-add-beneficiary" type="submit"
                className="btn btn-primary btn-full" disabled={loading}
                style={{ marginTop: '.5rem' }}>
                {loading ? <span className="spinner" /> : '⊕ Add beneficiary'}
              </button>
            </form>
          </div>

          {/* List */}
          <div>
            <h4 style={{ marginBottom: '1rem' }}>Saved beneficiaries
              <span style={{ marginLeft: '.5rem', fontWeight: 400, fontSize: '.9rem', color: 'var(--text-muted)' }}>
                ({benes.length})
              </span>
            </h4>
            {listLoading ? (
              <div className="loading-center"><div className="spinner" /></div>
            ) : benes.length === 0 ? (
              <div className="empty-state card">
                <div className="empty-icon" style={{ fontSize: '1.5rem', opacity: 0.3 }}>—</div>
                <h4>No beneficiaries yet</h4>
                <p>Add a payee to send them money quickly.</p>
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '.75rem' }}>
                {benes.map(b => (
                  <div key={b.beneficiaryId}
                    id={`bene-card-${b.beneficiaryId}`}
                    className="card flex items-center gap-4 fade-up"
                    style={{ padding: '1rem 1.25rem' }}>
                    <div style={{
                      width: 42, height: 42, borderRadius: '50%',
                      background: 'var(--accent-light)',
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      fontSize: '1.1rem', fontWeight: 700, color: 'var(--accent-dark)',
                      flexShrink: 0
                    }}>
                      {(b.nickname || b.beneficiaryAccountNumber)?.[0]?.toUpperCase()}
                    </div>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontWeight: 600, marginBottom: '.15rem' }}>
                        {b.nickname || '—'}
                      </div>
                      <div style={{ fontFamily: 'monospace', fontSize: '.8rem', color: 'var(--text-second)' }}>
                        {b.beneficiaryAccountNumber}
                      </div>
                    </div>
                    <div style={{ fontSize: '.75rem', color: 'var(--text-muted)', whiteSpace: 'nowrap' }}>
                      Added {fmtDate(b.createdAt)}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </>
  )
}
