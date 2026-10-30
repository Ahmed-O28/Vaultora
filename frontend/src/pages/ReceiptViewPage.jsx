import { useState, useEffect } from 'react'
import { useParams, Link, useNavigate, useLocation } from 'react-router-dom'
import { receipts, shortener } from '../services/api'
import { useAuth } from '../context/AuthContext'

function fmtMoney(v) {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(v ?? 0)
}

function fmtDate(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}

export default function ReceiptViewPage() {
  const { transactionId } = useParams()
  const { user } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const [receipt, setReceipt] = useState(null)
  const [loading, setLoading] = useState(true)
  const [errorType, setErrorType] = useState(null) // 'UNAUTH' | 'FORBIDDEN' | 'NOT_FOUND' | 'ERROR'
  const [errorMessage, setErrorMessage] = useState('')
  const [shortUrl, setShortUrl] = useState('')
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    if (!transactionId) {
      setErrorType('NOT_FOUND')
      setErrorMessage('No transaction identifier provided.')
      setLoading(false)
      return
    }

    // If user is unauthenticated, prompt for login gracefully
    if (!user) {
      setErrorType('UNAUTH')
      setLoading(false)
      return
    }

    setLoading(true)
    setErrorType(null)
    setErrorMessage('')

    Promise.all([
      receipts.get(transactionId),
      shortener.shorten({ transactionId }).catch(() => null)
    ])
      .then(([rcptData, shortData]) => {
        setReceipt(rcptData)
        if (rcptData?.shortUrl) {
          setShortUrl(rcptData.shortUrl)
        } else if (shortData?.shortUrl) {
          setShortUrl(shortData.shortUrl)
        }
      })
      .catch((err) => {
        const msg = err.message || ''
        const lower = msg.toLowerCase()
        if (lower.includes('access denied') || lower.includes('permission') || lower.includes('forbidden') || lower.includes('403')) {
          setErrorType('FORBIDDEN')
          setErrorMessage('Access denied: You do not have permission to view or share this receipt.')
        } else if (lower.includes('not found') || lower.includes('404')) {
          setErrorType('NOT_FOUND')
          setErrorMessage(`No transaction found matching reference: ${transactionId}`)
        } else if (lower.includes('auth') || lower.includes('token') || lower.includes('401')) {
          setErrorType('UNAUTH')
          setErrorMessage('Your session has expired. Please log in again to view this receipt.')
        } else {
          setErrorType('ERROR')
          setErrorMessage(msg || 'Unable to retrieve verified receipt. Please try again.')
        }
      })
      .finally(() => {
        setLoading(false)
      })
  }, [transactionId, user])

  const handleCopyLink = async () => {
    const linkToCopy = shortUrl || window.location.href
    try {
      await navigator.clipboard.writeText(linkToCopy)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch (_) {
      prompt('Copy receipt URL:', linkToCopy)
    }
  }

  return (
    <div className="receipt-view-container" style={{ maxWidth: '680px', margin: '2rem auto', padding: '0 1rem' }}>
      {/* Navigation / Header Actions (hidden during print) */}
      <div className="no-print" style={{ marginBottom: '1.5rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Link to={user ? "/transactions" : "/login"} className="btn btn-ghost btn-sm" style={{ display: 'flex', alignItems: 'center', gap: '.4rem' }}>
          ← {user ? 'Back to Transactions' : 'Go to Login'}
        </Link>
        {receipt && (
          <button
            onClick={() => window.print()}
            className="btn btn-outline btn-sm"
            style={{ display: 'flex', alignItems: 'center', gap: '.4rem' }}
          >
            Print Receipt
          </button>
        )}
      </div>

      <div
        className="card fade-up receipt-card"
        style={{
          padding: '2.5rem',
          border: '1px solid var(--border)',
          borderRadius: 'var(--radius-lg, 16px)',
          background: 'var(--bg-card)',
          boxShadow: 'var(--shadow-lg, 0 12px 40px rgba(0,0,0,0.08))'
        }}
      >
        {/* Receipt Header */}
        <div style={{ textAlign: 'center', borderBottom: '1px dashed var(--border)', paddingBottom: '1.75rem', marginBottom: '1.75rem' }}>
          <div style={{ fontSize: '1.85rem', fontWeight: 800, letterSpacing: '-0.03em', color: 'var(--text-primary)' }}>
            Vault<span style={{ color: 'var(--accent)' }}>ora</span>
          </div>
          <div style={{ fontSize: '.85rem', color: 'var(--text-second)', marginTop: '.25rem' }}>
            Official Electronic Transaction Receipt
          </div>
          <div style={{ marginTop: '.75rem', display: 'inline-flex', alignItems: 'center', gap: '.35rem', padding: '.3rem .85rem', borderRadius: '999px', background: 'rgba(34, 197, 94, 0.12)', color: 'var(--success)', fontWeight: 600, fontSize: '.78rem' }}>
            Official Banking Record
          </div>
        </div>

        {/* Loading State */}
        {loading && (
          <div className="loading-center" style={{ padding: '3.5rem 0' }}>
            <div className="spinner spinner-lg" />
            <p style={{ marginTop: '1.25rem', color: 'var(--text-second)', fontSize: '.9rem' }}>
              Retrieving cryptographic transaction record…
            </p>
          </div>
        )}

        {/* Unauthenticated State */}
        {!loading && errorType === 'UNAUTH' && (
          <div style={{ textAlign: 'center', padding: '1.5rem 0' }}>
            <div style={{ fontSize: '1.5rem', marginBottom: '1rem', opacity: 0.3 }}>—</div>
            <h3 style={{ marginBottom: '.5rem', color: 'var(--text-primary)' }}>Authentication Required</h3>
            <p style={{ maxWidth: '440px', margin: '0 auto 1.5rem', color: 'var(--text-second)', fontSize: '.9rem' }}>
              This electronic receipt is protected by Vaultora Customer Privacy Isolation.
              Please sign in with your authorized account to verify ownership and view transaction details.
            </p>
            <div style={{ background: 'var(--bg-warm, #faf7f2)', padding: '.75rem 1rem', borderRadius: '8px', display: 'inline-block', marginBottom: '1.5rem', fontFamily: 'monospace', fontSize: '.82rem', color: 'var(--text-muted)' }}>
              Reference ID: {transactionId}
            </div>
            <div>
              <Link
                to="/login"
                state={{ from: `/receipts/${transactionId}` }}
                className="btn btn-primary btn-lg"
                style={{ padding: '.75rem 2rem' }}
              >
                Sign In to View Receipt
              </Link>
            </div>
          </div>
        )}

        {/* Forbidden / Access Denied State */}
        {!loading && errorType === 'FORBIDDEN' && (
          <div style={{ textAlign: 'center', padding: '1.5rem 0' }}>
            <div style={{ fontSize: '1.5rem', marginBottom: '1rem', opacity: 0.3 }}>—</div>
            <h3 style={{ marginBottom: '.5rem', color: 'var(--danger)' }}>Access Restricted</h3>
            <p style={{ maxWidth: '440px', margin: '0 auto 1.5rem', color: 'var(--text-second)', fontSize: '.9rem' }}>
              You do not have authorization to view this receipt. Under digital banking privacy rules,
              receipts may only be viewed by the verified owner of the originating or destination account.
            </p>
            {user && (
              <p style={{ fontSize: '.8rem', color: 'var(--text-muted)', marginBottom: '1.5rem' }}>
                Currently logged in as: <strong>{user.email || user.fullName}</strong>
              </p>
            )}
            <div style={{ display: 'flex', justifyContent: 'center', gap: '1rem' }}>
              <Link to="/dashboard" className="btn btn-outline">
                Return to Dashboard
              </Link>
              <button
                className="btn btn-secondary"
                onClick={() => {
                  localStorage.removeItem('token')
                  localStorage.removeItem('user')
                  navigate('/login', { state: { from: `/receipts/${transactionId}` } })
                }}
              >
                Switch Account
              </button>
            </div>
          </div>
        )}

        {/* Transaction Not Found State */}
        {!loading && errorType === 'NOT_FOUND' && (
          <div style={{ textAlign: 'center', padding: '1.5rem 0' }}>
            <div style={{ fontSize: '1.5rem', marginBottom: '1rem', opacity: 0.3 }}>—</div>
            <h3 style={{ marginBottom: '.5rem', color: 'var(--text-primary)' }}>Receipt Not Found</h3>
            <p style={{ maxWidth: '440px', margin: '0 auto 1.5rem', color: 'var(--text-second)', fontSize: '.9rem' }}>
              {errorMessage || `We could not locate any transaction record matching reference "${transactionId}". Please verify the link or check your transaction history.`}
            </p>
            <Link to="/transactions" className="btn btn-primary">
              View Your Transactions
            </Link>
          </div>
        )}

        {/* Generic Error State */}
        {!loading && errorType === 'ERROR' && (
          <div style={{ textAlign: 'center', padding: '1.5rem 0' }}>
            <div className="alert alert-danger" style={{ marginBottom: '1.5rem', textAlign: 'left' }}>
              {errorMessage}
            </div>
            <button
              onClick={() => window.location.reload()}
              className="btn btn-outline"
            >
              Retry
            </button>
          </div>
        )}

        {/* Valid Verified Receipt Display */}
        {!loading && receipt && !errorType && (
          <div>
            {/* Amount & Status Banner */}
            <div style={{ textAlign: 'center', margin: '1.25rem 0 2rem' }}>
              <div style={{ fontSize: '.8rem', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.06em', fontWeight: 600 }}>
                Transaction Amount
              </div>
              <div style={{ fontSize: '2.5rem', fontWeight: 800, color: 'var(--text-primary)', marginTop: '.25rem', letterSpacing: '-0.02em' }}>
                {fmtMoney(receipt.amount)}
              </div>
              <div style={{ marginTop: '.5rem' }}>
                <span className={`badge ${receipt.status === 'COMPLETED' || receipt.status === 'SUCCESS' ? 'badge-success' : receipt.status === 'PENDING' ? 'badge-warn' : 'badge-danger'}`} style={{ padding: '.3rem .8rem', fontSize: '.8rem' }}>
                  {receipt.status || 'COMPLETED'}
                </span>
              </div>
            </div>

            {/* Transaction Key Details */}
            <div style={{ background: 'var(--bg-warm, #faf7f2)', border: '1px solid var(--border)', borderRadius: '12px', padding: '1.5rem', marginBottom: '1.75rem' }}>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.25rem', fontSize: '.9rem' }}>
                <div>
                  <div style={{ color: 'var(--text-muted)', fontSize: '.72rem', textTransform: 'uppercase', letterSpacing: '.05em', fontWeight: 600 }}>Source Account</div>
                  <div style={{ fontWeight: 600, marginTop: '.25rem', fontFamily: 'monospace', color: 'var(--text-primary)' }}>
                    {receipt.maskedSourceAccount || receipt.sourceAccount || '—'}
                  </div>
                </div>
                <div>
                  <div style={{ color: 'var(--text-muted)', fontSize: '.72rem', textTransform: 'uppercase', letterSpacing: '.05em', fontWeight: 600 }}>Destination Account</div>
                  <div style={{ fontWeight: 600, marginTop: '.25rem', fontFamily: 'monospace', color: 'var(--text-primary)' }}>
                    {receipt.maskedDestinationAccount || receipt.destinationAccount || '—'}
                  </div>
                </div>
                <div>
                  <div style={{ color: 'var(--text-muted)', fontSize: '.72rem', textTransform: 'uppercase', letterSpacing: '.05em', fontWeight: 600 }}>Transaction Type</div>
                  <div style={{ fontWeight: 600, marginTop: '.25rem', color: 'var(--text-primary)' }}>
                    {receipt.transactionType || receipt.type || 'TRANSFER'}
                  </div>
                </div>
                <div>
                  <div style={{ color: 'var(--text-muted)', fontSize: '.72rem', textTransform: 'uppercase', letterSpacing: '.05em', fontWeight: 600 }}>Execution Timestamp</div>
                  <div style={{ fontWeight: 600, marginTop: '.25rem', color: 'var(--text-primary)' }}>
                    {fmtDate(receipt.timestamp)}
                  </div>
                </div>
              </div>

              {receipt.description && (
                <div style={{ marginTop: '1.25rem', paddingTop: '1rem', borderTop: '1px solid var(--border)', fontSize: '.85rem' }}>
                  <span style={{ color: 'var(--text-muted)', fontWeight: 500 }}>Note / Reference: </span>
                  <span style={{ color: 'var(--text-primary)', fontStyle: 'italic' }}>{receipt.description}</span>
                </div>
              )}
            </div>

            {/* Reference Identifier */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '.78rem', color: 'var(--text-muted)', padding: '0 .25rem', marginBottom: '1.75rem' }}>
              <span>Transaction Reference</span>
              <code style={{ fontSize: '.82rem', color: 'var(--text-primary)', fontWeight: 600 }}>{receipt.transactionId}</code>
            </div>

            {/* Persistent Short URL Section (hidden during print) */}
            <div className="no-print" style={{ borderTop: '1px dashed var(--border)', paddingTop: '1.5rem' }}>
              <div style={{ fontSize: '.85rem', fontWeight: 600, marginBottom: '.5rem', display: 'flex', alignItems: 'center', gap: '.4rem', color: 'var(--text-primary)' }}>
                Shareable Short Link
              </div>
              <div style={{ display: 'flex', gap: '.5rem', alignItems: 'center' }}>
                <input
                  type="text"
                  readOnly
                  value={shortUrl || window.location.href}
                  className="input"
                  style={{ fontSize: '.82rem', fontFamily: 'monospace', background: 'var(--bg)', padding: '.5rem .75rem' }}
                />
                <button
                  type="button"
                  onClick={handleCopyLink}
                  className={`btn btn-sm ${copied ? 'btn-success' : 'btn-accent'}`}
                  style={{ whiteSpace: 'nowrap', minWidth: '85px' }}
                >
                  {copied ? 'Copied' : 'Copy Link'}
                </button>
              </div>
              <p style={{ margin: '.5rem 0 0', fontSize: '.72rem', color: 'var(--text-muted)' }}>
                Powered by Base62 URL Shortener in PostgreSQL with 7-day expiration and customer isolation.
              </p>
            </div>
          </div>
        )}
      </div>

      {/* Print Stylesheet */}
      <style>{`
        @media print {
          .no-print { display: none !important; }
          body { background: white !important; color: black !important; }
          .receipt-view-container { margin: 0 !important; max-width: 100% !important; padding: 0 !important; }
          .receipt-card { box-shadow: none !important; border: 1px solid #ddd !important; }
        }
      `}</style>
    </div>
  )
}

