import { useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { receipts, shortener } from '../services/api'

function fmtMoney(v) {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(v ?? 0)
}

function fmtDate(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('en-US', {
    month: 'short', day: 'numeric', year: 'numeric',
    hour: '2-digit', minute: '2-digit'
  })
}

export default function ReceiptModal({
  isOpen,
  onClose,
  transaction: propTransaction,
  transactionId,
  shortUrl: propShortUrl,
  initialShortUrl,
  loading: propLoading = false
}) {
  const [copied, setCopied] = useState(false)
  const [internalTxn, setInternalTxn] = useState(null)
  const [internalShortUrl, setInternalShortUrl] = useState('')
  const [fetchLoading, setFetchLoading] = useState(false)
  const [fetchError, setFetchError] = useState('')

  const transaction = propTransaction || internalTxn
  const shortUrl = propShortUrl || initialShortUrl || internalShortUrl
  const loading = propLoading || fetchLoading

  // Always reset internal state when modal opens or closes or transaction changes
  useEffect(() => {
    if (!isOpen) {
      setInternalTxn(null)
      setInternalShortUrl('')
      setFetchError('')
      setFetchLoading(false)
      return
    }

    setInternalTxn(null)
    setInternalShortUrl('')
    setFetchError('')

    const targetTxId = transactionId || propTransaction?.transactionId
    if (!targetTxId) return

    setFetchLoading(true)

    const fetchTasks = [
      shortener.shorten({ transactionId: targetTxId }).catch(() => null)
    ]
    if (!propTransaction) {
      fetchTasks.push(receipts.get(targetTxId))
    }

    Promise.all(fetchTasks)
      .then(([shortResp, rcptResp]) => {
        if (shortResp?.shortUrl) setInternalShortUrl(shortResp.shortUrl)
        if (rcptResp) setInternalTxn(rcptResp)
      })
      .catch((err) => {
        setFetchError(err.message || 'Failed to retrieve receipt details')
      })
      .finally(() => setFetchLoading(false))
  }, [isOpen, transactionId, propTransaction?.transactionId])

  // Keyboard navigation for Escape key
  useEffect(() => {
    if (!isOpen) return

    function handleKeyDown(e) {
      if (e.key === 'Escape') {
        e.preventDefault()
        e.stopPropagation()
        onClose()
      }
    }

    window.addEventListener('keydown', handleKeyDown, true)
    return () => window.removeEventListener('keydown', handleKeyDown, true)
  }, [isOpen, onClose])

  if (!isOpen) return null

  function handleCopyLink() {
    if (!shortUrl) return
    navigator.clipboard.writeText(shortUrl)
      .then(() => {
        setCopied(true)
        setTimeout(() => setCopied(false), 2500)
      })
      .catch(() => {
        prompt('Copy receipt URL:', shortUrl)
      })
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="receipt-modal-title"
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(15, 15, 15, 0.72)',
        backdropFilter: 'blur(8px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 9999,
        padding: '1rem',
        animation: 'fadeIn 0.2s ease-out'
      }}
      onClick={(e) => {
        e.stopPropagation()
        if (e.target === e.currentTarget) onClose()
      }}
      onKeyDown={(e) => e.stopPropagation()}
    >
      <div
        className="card shadow-lg fade-up"
        style={{
          width: '100%',
          maxWidth: '500px',
          background: 'var(--bg-card, #FFFFFF)',
          borderRadius: 'var(--radius-lg, 20px)',
          border: '1px solid var(--border)',
          padding: '2.25rem',
          position: 'relative',
          boxShadow: 'var(--shadow-lg)'
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.5rem', borderBottom: '1px solid var(--border)', paddingBottom: '1rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '.75rem' }}>
            <div style={{
              width: '42px', height: '42px', borderRadius: '12px',
              background: 'var(--accent-light, #FDF3EA)', color: 'var(--accent-dark, #C97B44)',
              display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '1.25rem', fontWeight: 700
            }}>
              ≡
            </div>
            <div>
              <h3 id="receipt-modal-title" style={{ margin: 0, fontSize: '1.2rem', fontWeight: 700, color: 'var(--text-primary)' }}>
                Vault<span style={{ color: 'var(--accent)' }}>ora</span> Receipt
              </h3>
              <p style={{ margin: 0, fontSize: '.78rem', color: 'var(--text-muted)' }}>
                Official verified banking ledger record
              </p>
            </div>
          </div>
          <button
            className="btn btn-ghost btn-sm"
            onClick={onClose}
            aria-label="Close receipt dialog"
            style={{ fontSize: '1.25rem', width: '32px', height: '32px', padding: 0, borderRadius: '8px', display: 'flex', alignItems: 'center', justifyContent: 'center' }}
          >
            ×
          </button>
        </div>

        {loading ? (
          <div className="loading-center" style={{ padding: '3rem 0' }}>
            <div className="spinner spinner-lg" />
            <p style={{ marginTop: '1rem', fontSize: '.85rem', color: 'var(--text-second)' }}>Retrieving receipt…</p>
          </div>
        ) : transaction ? (
          <div>
            {/* Amount Banner */}
            <div
              style={{
                textAlign: 'center',
                padding: '1.35rem',
                background: 'var(--accent-light, #FDF3EA)',
                border: '1px solid rgba(233, 154, 101, 0.35)',
                borderRadius: 'var(--radius, 12px)',
                marginBottom: '1.5rem'
              }}
            >
              <div style={{ fontSize: '.78rem', color: 'var(--text-second)', textTransform: 'uppercase', letterSpacing: '.06em', fontWeight: 600, marginBottom: '.25rem' }}>
                Payment Amount
              </div>
              <div style={{ fontSize: '2.1rem', fontWeight: 800, color: 'var(--accent-dark, #C97B44)', letterSpacing: '-0.02em' }}>
                {fmtMoney(transaction.amount)}
              </div>
              <div style={{ display: 'inline-block', marginTop: '.4rem' }}>
                <span className="badge badge-success" style={{ fontSize: '.75rem', padding: '3px 10px' }}>
                  {transaction.status || 'SUCCESS'}
                </span>
              </div>
            </div>

            {/* Receipt Details Table */}
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                gap: '.7rem',
                fontSize: '.88rem',
                background: 'var(--bg-warm, #FAF7F2)',
                border: '1px solid var(--border)',
                borderRadius: 'var(--radius, 12px)',
                padding: '1.25rem',
                marginBottom: '1.5rem'
              }}
            >
              <div className="flex justify-between" style={{ alignItems: 'center' }}>
                <span style={{ color: 'var(--text-muted)', fontSize: '.82rem' }}>Transaction Ref</span>
                <code style={{ fontSize: '.82rem', fontWeight: 600, color: 'var(--text-primary)' }}>
                  {transaction.transactionId}
                </code>
              </div>

              <div className="flex justify-between" style={{ alignItems: 'center' }}>
                <span style={{ color: 'var(--text-muted)', fontSize: '.82rem' }}>Date & Time</span>
                <span style={{ color: 'var(--text-primary)', fontSize: '.85rem', fontWeight: 500 }}>
                  {fmtDate(transaction.timestamp || new Date())}
                </span>
              </div>

              <div className="flex justify-between" style={{ alignItems: 'center' }}>
                <span style={{ color: 'var(--text-muted)', fontSize: '.82rem' }}>Source Account</span>
                <span style={{ fontFamily: 'monospace', color: 'var(--text-primary)', fontWeight: 600 }}>
                  {transaction.maskedSourceAccount || transaction.sourceAccount}
                </span>
              </div>

              <div className="flex justify-between" style={{ alignItems: 'center' }}>
                <span style={{ color: 'var(--text-muted)', fontSize: '.82rem' }}>Destination</span>
                <span style={{ fontFamily: 'monospace', color: 'var(--text-primary)', fontWeight: 600 }}>
                  {transaction.maskedDestinationAccount || transaction.destinationAccount}
                </span>
              </div>

              {transaction.description && (
                <div className="flex justify-between" style={{ borderTop: '1px solid var(--border)', paddingTop: '.6rem', marginTop: '.2rem' }}>
                  <span style={{ color: 'var(--text-muted)', fontSize: '.82rem' }}>Note</span>
                  <span style={{ color: 'var(--text-second)', fontStyle: 'italic', fontSize: '.85rem' }}>
                    {transaction.description}
                  </span>
                </div>
              )}
            </div>

            {/* Persistent Short URL Section */}
            {shortUrl && (
              <div
                style={{
                  background: 'var(--bg-warm, #FAF7F2)',
                  border: '1px dashed var(--accent)',
                  borderRadius: '10px',
                  padding: '.85rem 1rem',
                  marginBottom: '1.5rem'
                }}
              >
                <div style={{ fontSize: '.78rem', color: 'var(--text-second)', fontWeight: 600, marginBottom: '.4rem', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <span>Shareable Short URL</span>
                  <a
                    href={shortUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    style={{ color: 'var(--accent)', textDecoration: 'underline', fontSize: '.78rem', fontWeight: 600 }}
                  >
                    Open Link ↗
                  </a>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '.5rem' }}>
                  <input
                    type="text"
                    readOnly
                    value={shortUrl}
                    className="input"
                    style={{ fontSize: '.82rem', fontFamily: 'monospace', padding: '.45rem .65rem', background: '#FFFFFF' }}
                  />
                  <a
                    href={shortUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="btn btn-sm btn-secondary"
                    style={{ whiteSpace: 'nowrap', textDecoration: 'none' }}
                  >
                    Open ↗
                  </a>
                  <button
                    className={`btn btn-sm ${copied ? 'btn-success' : 'btn-accent'}`}
                    onClick={handleCopyLink}
                    style={{ whiteSpace: 'nowrap', minWidth: '92px' }}
                  >
                    {copied ? 'Copied' : 'Copy Link'}
                  </button>
                </div>
              </div>
            )}

            {/* Action Buttons */}
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '.75rem', marginTop: '1.25rem' }}>
              <button className="btn btn-secondary btn-sm" onClick={onClose}>
                Close
              </button>
              {transaction?.transactionId && (
                <Link
                  to={`/receipts/${transaction.transactionId}`}
                  className="btn btn-primary btn-sm"
                  onClick={onClose}
                  style={{ display: 'flex', alignItems: 'center', gap: '.4rem' }}
                >
                  View Full Receipt →
                </Link>
              )}
            </div>
          </div>
        ) : (
          <div className="empty-state">
            <p>No receipt details available.</p>
          </div>
        )}
      </div>
    </div>
  )
}
