import { useState, useEffect, useRef } from 'react'
import { useAuth } from '../context/AuthContext'
import { accounts as accsApi, bills, billerApi, autocomplete as autoApi, shortener } from '../services/api'
import AutocompleteInput from '../components/AutocompleteInput'
import ReceiptModal from '../components/ReceiptModal'

function fmtMoney(v) {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(v ?? 0)
}
function fmtDate(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}
function fmtTime(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' })
}

const DEFAULT_BILLERS = [
  { id: 'ELEC-001', name: 'City Electric Co.', category: 'Electricity', status: 'ACTIVE' },
  { id: 'WATER-001', name: 'Metro Water Authority', category: 'Water', status: 'ACTIVE' },
  { id: 'GAS-001', name: 'National Gas Ltd.', category: 'Gas', status: 'ACTIVE' },
  { id: 'INTERNET-001', name: 'Fiber Internet Inc.', category: 'Internet', status: 'ACTIVE' },
  { id: 'INSURANCE-001', name: 'SafeGuard Insurance', category: 'Insurance', status: 'ACTIVE' },
  { id: 'PHONE-001', name: 'Mobile Network Corp.', category: 'Mobile', status: 'INACTIVE' },
]

export default function BillPayPage() {
  const { user } = useAuth()
  const [accs, setAccs]     = useState([])
  const [billers, setBillers] = useState(DEFAULT_BILLERS)
  const [history, setHistory] = useState([])
  const [form, setForm]     = useState({ accountNumber: '', billerId: '', amount: '' })
  const [billerMode, setBillerMode] = useState('list') // 'list' | 'search'
  const [billerQuery, setBillerQuery] = useState('')
  const [result, setResult] = useState(null)
  const [error, setError]   = useState('')
  const [loading, setLoading] = useState(false)
  const [accsLoading, setAccsLoading] = useState(true)
  const isSubmittingRef = useRef(false)

  // Receipt Modal state
  const [selectedPayment, setSelectedPayment] = useState(null)
  const [receiptTxId, setReceiptTxId] = useState('')
  const [receiptShortUrl, setReceiptShortUrl] = useState('')
  const [receiptModalOpen, setReceiptModalOpen] = useState(false)

  useEffect(() => {
    if (!user?.customerId) {
      setAccsLoading(false)
      return
    }

    accsApi.getForCustomer(user.customerId)
      .then(data => {
        setAccs(data || [])
        if (data?.[0]) {
          setForm(f => ({ ...f, accountNumber: data[0].accountNumber }))
          return bills.getForAccount(data[0].accountNumber)
        }
      })
      .then(h => h && setHistory(h))
      .catch(err => setError(err.message))
      .finally(() => setAccsLoading(false))

    // Fetch dynamic billers from Biller API
    billerApi.getBillers()
      .then(res => {
        const list = Array.isArray(res) ? res : res?.billers || []
        if (list.length > 0) {
          setBillers(list.map(b => ({
            id: b.billerId || b.id,
            name: b.name,
            category: b.category,
            status: b.status || 'ACTIVE'
          })))
        }
      })
      .catch(() => {})
  }, [user?.customerId])

  async function handleAccountChange(num) {
    setForm(f => ({ ...f, accountNumber: num }))
    try {
      const h = await bills.getForAccount(num)
      setHistory(h || [])
    } catch (_) {}
  }

  async function handleSubmit(e) {
    e.preventDefault()
    if (loading || isSubmittingRef.current) return
    setError(''); setResult(null)
    const amount = parseFloat(form.amount)
    if (!amount || amount <= 0) { setError('Enter a valid amount.'); return }

    const selectedBiller = billers.find(b => b.id === form.billerId)
    if (selectedBiller && selectedBiller.status === 'INACTIVE') {
      setError(`Cannot process payment: ${selectedBiller.name} is currently marked as INACTIVE. Please select an active partner biller.`)
      return
    }

    setLoading(true)
    try {
      const payment = await bills.pay({ ...form, amount })
      setResult(payment)
      setForm(f => ({ ...f, amount: '', billerId: '' }))
      setBillerQuery('')

      // Auto-shorten receipt link if transactionId is present
      if (payment.transactionId) {
        try {
          const shortResp = await shortener.shorten({ transactionId: payment.transactionId })
          if (shortResp && shortResp.shortUrl) {
            setReceiptShortUrl(shortResp.shortUrl)
          }
        } catch (_) {}
      }

      // Refresh history for paying account
      const h = await bills.getForAccount(form.accountNumber)
      setHistory(h || [])
      // Refresh accounts list to reflect debited balance immediately
      if (user?.customerId) {
        const freshAccs = await accsApi.getForCustomer(user.customerId)
        if (freshAccs) setAccs(freshAccs)
      }
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
      isSubmittingRef.current = false
    }
  }

  const selectedBiller = billers.find(b => b.id === form.billerId)

  const openReceiptForPayment = async (payment) => {
    const txId = typeof payment === 'string' ? payment : payment?.transactionId
    const billerObj = typeof payment === 'object' ? billers.find(b => b.id === payment.billerId) : null
    const paymentObj = typeof payment === 'object' ? {
      transactionId: payment.transactionId,
      amount: payment.amount,
      sourceAccount: payment.accountNumber,
      maskedSourceAccount: payment.accountNumber,
      destinationAccount: payment.billerId,
      maskedDestinationAccount: billerObj?.name ? `${billerObj.name} (${payment.billerId})` : payment.billerId,
      status: payment.status || 'SUCCESS',
      description: `Bill payment to ${billerObj?.name || payment.billerId}`,
      timestamp: payment.createdAt
    } : null

    setReceiptTxId(txId)
    setSelectedPayment(paymentObj)
    setReceiptShortUrl('')
    setReceiptModalOpen(true)
    if (txId) {
      try {
        const shortData = await shortener.shorten({ transactionId: txId }).catch(() => null)
        if (shortData?.shortUrl) {
          setReceiptShortUrl(shortData.shortUrl)
        }
      } catch (_) {}
    }
  }

  return (
    <>
      <div className="page-header">
        <div>
          <h3 style={{ margin: 0 }}>Pay Bills</h3>
          <p style={{ margin: 0, fontSize: '.85rem' }}>
            Pay utility and service bills directly from your account · Partner biller directory
          </p>
        </div>
      </div>

      <div className="page-body">
        {/* Prominent Transaction Successful Receipt */}
        {result && (
          <div
            id="bill-payment-success-card"
            className="card fade-up"
            style={{
              marginBottom: '1.5rem',
              background: 'linear-gradient(135deg, rgba(46, 125, 50, 0.08) 0%, rgba(46, 125, 50, 0.02) 100%)',
              border: '2px solid var(--success)',
              borderRadius: '12px',
              padding: '1.5rem',
              boxShadow: '0 4px 14px rgba(46, 125, 50, 0.12)'
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '1rem', marginBottom: '1.25rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.85rem' }}>
                <div style={{
                  width: '44px',
                  height: '44px',
                  borderRadius: '50%',
                  background: 'var(--success)',
                  color: '#ffffff',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: '1.5rem',
                  fontWeight: 'bold',
                  boxShadow: '0 2px 8px rgba(46, 125, 50, 0.3)'
                }}>
                  ✓
                </div>
                <div>
                  <h3 style={{ margin: 0, color: 'var(--success)', fontSize: '1.25rem', fontWeight: 700 }}>
                    Transaction Successful!
                  </h3>
                  <p style={{ margin: '0.2rem 0 0', color: 'var(--text-second)', fontSize: '0.875rem' }}>
                    Your bill payment has been confirmed and debited from your account.
                  </p>
                </div>
              </div>

              <div style={{ display: 'flex', alignItems: 'center', gap: '.5rem' }}>
                <span className="badge badge-success" style={{ fontSize: '0.85rem', padding: '0.35rem 0.75rem' }}>
                  {result.status || 'SUCCESS'}
                </span>
                {result.transactionId && (
                  <button
                    type="button"
                    className="btn btn-secondary btn-sm"
                    onClick={() => openReceiptForPayment(result)}
                    style={{ fontSize: '.8rem', padding: '6px 12px', display: 'flex', alignItems: 'center', gap: '.35rem' }}
                  >
                    View Receipt & Link
                  </button>
                )}
              </div>
            </div>

            {/* Receipt Summary Grid */}
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
              gap: '1rem',
              background: '#FFFFFF',
              padding: '1.2rem',
              borderRadius: '8px',
              border: '1px solid rgba(46, 125, 50, 0.25)',
              marginBottom: '1rem'
            }}>
              <div>
                <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em', display: 'block', marginBottom: '.25rem' }}>
                  Amount Paid
                </span>
                <strong style={{ fontSize: '1.35rem', color: 'var(--success)' }}>
                  {fmtMoney(result.amount)}
                </strong>
              </div>

              <div>
                <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em', display: 'block', marginBottom: '.25rem' }}>
                  Biller Partner
                </span>
                <strong style={{ fontSize: '1rem', color: 'var(--text-main)', display: 'block' }}>
                  {billers.find(b => b.id === result.billerId)?.name || result.billerId}
                </strong>
                <small style={{ color: 'var(--text-second)', fontSize: '.75rem' }}>
                  ID: {result.billerId}
                </small>
              </div>

              <div>
                <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em', display: 'block', marginBottom: '.25rem' }}>
                  Debited Account
                </span>
                <code style={{ fontSize: '0.9rem', fontWeight: 600, color: 'var(--text-main)' }}>
                  {result.accountNumber}
                </code>
              </div>

              <div>
                <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em', display: 'block', marginBottom: '.25rem' }}>
                  Transaction Ledger ID
                </span>
                <code style={{ fontSize: '0.8rem', color: 'var(--accent)', fontWeight: 600 }}>
                  {result.transactionId || '—'}
                </code>
              </div>

              <div>
                <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em', display: 'block', marginBottom: '.25rem' }}>
                  Payment Reference
                </span>
                <code style={{ fontSize: '0.8rem', color: 'var(--text-second)' }}>
                  {result.paymentId || '—'}
                </code>
              </div>

              <div>
                <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em', display: 'block', marginBottom: '.25rem' }}>
                  Processed Date & Time
                </span>
                <span style={{ fontSize: '0.85rem', color: 'var(--text-second)' }}>
                  {fmtDate(result.createdAt)} · {fmtTime(result.createdAt)}
                </span>
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
              <button
                type="button"
                id="btn-dismiss-payment-success"
                className="btn btn-secondary btn-sm"
                onClick={() => setResult(null)}
                style={{ fontSize: '0.85rem' }}
              >
                Dismiss Banner
              </button>
            </div>
          </div>
        )}

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1.5fr', gap: '1.5rem', alignItems: 'start' }}>

          {/* Form */}
          <div className="card fade-up">
            <h4 style={{ marginBottom: '1.5rem' }}>New payment</h4>
            {error && <div className="alert alert-danger" style={{ marginBottom: '1rem' }}>{error}</div>}
            {accsLoading ? (
              <div className="loading-center"><div className="spinner" /></div>
            ) : (
              <form onSubmit={handleSubmit} className="flex-col gap-4">
                <div className="form-group">
                  <label htmlFor="bill-account">From account</label>
                  <select id="bill-account" className="input"
                    value={form.accountNumber}
                    onChange={e => handleAccountChange(e.target.value)}
                    required>
                    {accs.map(a => (
                      <option key={a.accountNumber} value={a.accountNumber}>
                        {a.accountNumber} — {fmtMoney(a.balance)}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="form-group">
                  <div className="flex justify-between items-center" style={{ marginBottom: '.25rem' }}>
                    <label htmlFor="bill-biller" style={{ margin: 0 }}>Biller Partner</label>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '.5rem' }}>
                      <button
                        type="button"
                        className="btn btn-ghost btn-sm"
                        style={{ padding: '0 .25rem', fontSize: '.75rem', color: 'var(--accent)' }}
                        onClick={() => {
                          setBillerMode(m => m === 'list' ? 'search' : 'list')
                          setBillerQuery('')
                        }}
                      >
                        {billerMode === 'list' ? 'Search with autocomplete' : 'Choose from list'}
                      </button>
                      <small style={{ color: 'var(--text-muted)', fontSize: '.75rem' }}>
                        {billers.filter(b => b.status === 'ACTIVE').length} active partners
                      </small>
                    </div>
                  </div>

                  {billerMode === 'search' ? (
                    <div>
                      <AutocompleteInput
                        id="bill-biller-search"
                        value={billerQuery}
                        onChange={(text) => {
                          setBillerQuery(text)
                          const exact = billers.find(b => b.name.toLowerCase() === text.trim().toLowerCase())
                          if (exact) setForm(f => ({ ...f, billerId: exact.id }))
                        }}
                        onSelect={(item) => {
                          const billerId = item.referenceId || item.term
                          const matchedBiller = billers.find(b => b.id === billerId || b.name.toLowerCase() === item.term.toLowerCase())
                          const finalId = matchedBiller ? matchedBiller.id : billerId
                          setForm(f => ({ ...f, billerId: finalId }))
                          setBillerQuery(matchedBiller ? matchedBiller.name : item.term)
                        }}
                        fetchSuggestions={(q) => autoApi.getBillers(q, 6)}
                        placeholder="Type biller name (e.g. Electric, Water, Gas)…"
                      />
                      {selectedBiller && (
                        <div style={{ marginTop: '.4rem', fontSize: '.8rem', color: 'var(--text-main)', display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '.4rem .6rem', background: 'var(--bg-warm)', borderRadius: '6px' }}>
                          <span>
                            Selected: <strong>{selectedBiller.name}</strong> ({selectedBiller.id})
                            <span className={`badge ${selectedBiller.status === 'ACTIVE' ? 'badge-success' : 'badge-danger'}`} style={{ marginLeft: '.5rem', fontSize: '.7rem' }}>
                              {selectedBiller.status}
                            </span>
                          </span>
                          <button
                            type="button"
                            onClick={() => {
                              setForm(f => ({ ...f, billerId: '' }))
                              setBillerQuery('')
                            }}
                            style={{ border: 'none', background: 'none', cursor: 'pointer', color: 'var(--text-muted)', fontSize: '.9rem' }}
                          >
                            ×
                          </button>
                        </div>
                      )}
                    </div>
                  ) : (
                    <select id="bill-biller" className="input"
                      value={form.billerId}
                      onChange={e => {
                        const id = e.target.value
                        setForm(f => ({ ...f, billerId: id }))
                        const bObj = billers.find(b => b.id === id)
                        if (bObj) setBillerQuery(bObj.name)
                      }}
                      required>
                      <option value="">Select biller…</option>
                      {billers.map(b => {
                        const isInactive = b.status === 'INACTIVE'
                        return (
                          <option
                            key={b.id}
                            value={b.id}
                            disabled={isInactive}
                            style={isInactive ? { color: '#888', fontStyle: 'italic' } : {}}
                          >
                            {b.name} ({b.category || 'General'}) — {isInactive ? '[INACTIVE] Unavailable' : 'Active'}
                          </option>
                        )
                      })}
                    </select>
                  )}

                  {selectedBiller && selectedBiller.status === 'INACTIVE' && (
                    <div style={{ color: 'var(--danger)', fontSize: '.8rem', marginTop: '.35rem', fontWeight: 500 }}>
                      {selectedBiller.name} is currently inactive. Payments to this biller are paused.
                    </div>
                  )}
                </div>

                <div className="form-group">
                  <label htmlFor="bill-amount">Amount (USD)</label>
                  <input id="bill-amount" type="number" className="input"
                    placeholder="0.00" min="0.01" step="0.01"
                    value={form.amount}
                    onChange={e => setForm(f => ({ ...f, amount: e.target.value }))}
                    required />
                </div>

                <button id="btn-bill-submit" type="submit"
                  className="btn btn-accent btn-full btn-lg" disabled={loading}
                  style={{ marginTop: '.5rem' }}>
                  {loading ? <span className="spinner" /> : 'Pay Bill'}
                </button>
              </form>
            )}
          </div>

          {/* History */}
          <div>
            <h4 style={{ marginBottom: '1rem' }}>Payment history</h4>
            {history.length === 0 ? (
              <div className="empty-state card">
                <div className="empty-icon" style={{ fontSize: '1.5rem', opacity: 0.3 }}>—</div>
                <p>No payments yet for this account.</p>
              </div>
            ) : (
              <div className="table-wrap fade-up">
                <table>
                  <thead>
                    <tr>
                      <th>Date</th>
                      <th>Biller</th>
                      <th>Amount</th>
                      <th>Transaction ID</th>
                      <th>Status</th>
                      <th>Receipt</th>
                    </tr>
                  </thead>
                  <tbody>
                    {history.map(p => {
                      const billerObj = billers.find(b => b.id === p.billerId)
                      return (
                        <tr key={p.id || p.paymentId}>
                          <td><small>{fmtDate(p.createdAt)}</small></td>
                          <td style={{ fontSize: '.875rem' }}>
                            <strong>{billerObj?.name || p.billerId}</strong>
                            {billerObj?.category && (
                              <span style={{ fontSize: '.75rem', color: 'var(--text-muted)', marginLeft: '.4rem' }}>
                                ({billerObj.category})
                              </span>
                            )}
                          </td>
                          <td style={{ fontWeight: 600 }}>{fmtMoney(p.amount)}</td>
                          <td>
                            <code style={{ fontSize: '0.75rem', color: 'var(--text-second)' }}>
                              {p.transactionId ? p.transactionId.slice(0, 16) + '…' : p.paymentId ? p.paymentId.slice(0, 16) + '…' : '—'}
                            </code>
                          </td>
                          <td>
                            <span className={`badge ${p.status === 'SUCCESS' ? 'badge-success' : 'badge-danger'}`}>
                              {p.status}
                            </span>
                          </td>
                          <td>
                            {p.transactionId ? (
                              <button
                                type="button"
                                className="btn btn-ghost btn-sm"
                                style={{ padding: '.2rem .5rem', fontSize: '.75rem', display: 'flex', alignItems: 'center', gap: '.3rem' }}
                                onClick={() => openReceiptForPayment(p)}
                                title="View and share receipt"
                              >
                                Receipt
                              </button>
                            ) : (
                              <span style={{ color: 'var(--text-muted)', fontSize: '.75rem' }}>—</span>
                            )}
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Persistent Receipt Modal */}
      <ReceiptModal
        isOpen={receiptModalOpen}
        onClose={() => {
          setReceiptModalOpen(false)
          setSelectedPayment(null)
          setReceiptTxId('')
          setReceiptShortUrl('')
        }}
        transaction={selectedPayment}
        transactionId={receiptTxId}
        initialShortUrl={receiptShortUrl}
      />
    </>
  )
}
