import { useState, useEffect, useCallback } from 'react'
import { useAuth } from '../context/AuthContext'
import { accounts as accsApi, receipts as receiptApi, shortener } from '../services/api'
import ReceiptModal from '../components/ReceiptModal'

function fmtMoney(v) {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(v ?? 0)
}
function fmtDate(iso) {
  return new Date(iso).toLocaleString('en-US', {
    month: 'short', day: 'numeric', year: 'numeric',
    hour: '2-digit', minute: '2-digit'
  })
}

export default function TransactionsPage() {
  const { user } = useAuth()
  const [accs, setAccs] = useState([])
  const [selected, setSelected] = useState('')
  const [txns, setTxns] = useState([])
  const [loadingAccs, setLoadingAccs] = useState(true)
  const [loadingTxns, setLoadingTxns] = useState(false)
  const [error, setError] = useState('')

  // Receipt modal state
  const [activeTxn, setActiveTxn] = useState(null)
  const [shortUrl, setShortUrl] = useState('')
  const [receiptModalOpen, setReceiptModalOpen] = useState(false)
  const [receiptLoading, setReceiptLoading] = useState(false)

  // Load user accounts
  const loadAccounts = useCallback(async () => {
    if (!user?.customerId) return
    setLoadingAccs(true)
    setError('')
    try {
      const data = await accsApi.getForCustomer(user.customerId)
      setAccs(data || [])
      if (data && data.length > 0) {
        setSelected(prev => {
          const stillValid = data.some(a => a.accountNumber === prev)
          return stillValid ? prev : data[0].accountNumber
        })
      }
    } catch (err) {
      setError(err.message || 'Failed to load user accounts.')
    } finally {
      setLoadingAccs(false)
    }
  }, [user?.customerId])

  useEffect(() => {
    loadAccounts()
  }, [loadAccounts])

  // Load transactions when account changes
  const loadTransactions = useCallback(async (accountNum) => {
    if (!accountNum) return
    setLoadingTxns(true)
    setError('')
    try {
      const data = await accsApi.getTransactions(accountNum)
      setTxns(data || [])
    } catch (err) {
      setTxns([]) // Do NOT display transactions when rate-limited or in error state
      const msg = err.message || 'Failed to load transactions.'
      setError(msg)
      if (msg.toLowerCase().includes('too many requests')) {
        setTimeout(() => {
          setError('')
          if (accountNum) loadTransactions(accountNum)
        }, 15000)
      }
    } finally {
      setLoadingTxns(false)
    }
  }, [])

  useEffect(() => {
    if (selected) {
      loadTransactions(selected)
    }
  }, [selected, loadTransactions])

  const handleOpenReceipt = async (tx) => {
    setActiveTxn(tx)
    setShortUrl('')
    setReceiptModalOpen(true)
    setReceiptLoading(true)
    try {
      const receiptData = await receiptApi.get(tx.transactionId).catch(() => tx)
      setActiveTxn(receiptData)
      const shortData = await shortener.shorten({ transactionId: tx.transactionId }).catch(() => null)
      if (shortData && shortData.shortUrl) {
        setShortUrl(shortData.shortUrl)
      }
    } finally {
      setReceiptLoading(false)
    }
  }

  const handleRefresh = () => {
    if (selected) {
      loadTransactions(selected)
    }
  }

  return (
    <>
      <div className="page-header">
        <div>
          <h3 style={{ margin: 0 }}>Transactions</h3>
          <p style={{ margin: 0, fontSize: '.85rem' }}>Full transaction history by account</p>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '.75rem' }}>
          {!loadingAccs && accs.length > 0 && (
            <select
              id="txn-account-select"
              className="input"
              style={{ width: 'auto', minWidth: 220 }}
              value={selected}
              onChange={e => setSelected(e.target.value)}
            >
              {accs.map(a => (
                <option key={a.accountNumber} value={a.accountNumber}>
                  {a.accountNumber} ({fmtMoney(a.balance)})
                </option>
              ))}
            </select>
          )}
          <button
            className="btn btn-secondary btn-sm"
            onClick={handleRefresh}
            disabled={loadingAccs || loadingTxns}
            title="Refresh transactions"
          >
            {loadingAccs || loadingTxns ? <span className="spinner" /> : '↻ Refresh'}
          </button>
        </div>
      </div>

      <div className="page-body">
        {error ? (
          <div
            id="txn-rate-limit-card"
            className="card text-center fade-up"
            style={{
              padding: '2.5rem 1.5rem',
              maxWidth: '520px',
              margin: '2rem auto',
              border: '1px solid rgba(220, 53, 69, 0.25)',
              background: 'var(--bg-card, #ffffff)',
              borderRadius: 'var(--radius-lg, 16px)',
              boxShadow: 'var(--shadow-sm)'
            }}
          >
            <div style={{ fontSize: '2.8rem', marginBottom: '0.75rem' }}>⏳</div>
            <h4 style={{ color: 'var(--danger)', marginBottom: '0.5rem', fontWeight: 700 }}>
              {error.toLowerCase().includes('too many requests') ? 'Rate Limit Active' : 'Unable to Load Transactions'}
            </h4>
            <p style={{ color: 'var(--text-second)', fontSize: '0.9rem', marginBottom: '1.25rem', lineHeight: 1.5 }}>
              {error}
            </p>
            <div style={{ display: 'flex', justifyContent: 'center', gap: '0.75rem' }}>
              <button
                className="btn btn-secondary btn-sm"
                onClick={() => {
                  setError('')
                  if (selected) loadTransactions(selected)
                }}
                style={{ display: 'inline-flex', alignItems: 'center', gap: '.4rem' }}
              >
                ↻ Retry Fetching Transactions
              </button>
            </div>
          </div>
        ) : loadingAccs ? (
          <div className="loading-center"><div className="spinner spinner-lg" /></div>
        ) : accs.length === 0 ? (
          <div className="empty-state">
            <div className="empty-icon" style={{ fontSize: '1.5rem', opacity: 0.3 }}>—</div>
            <h4>No accounts available</h4>
            <p>No active accounts found for this customer profile.</p>
            <button className="btn btn-primary btn-sm" onClick={loadAccounts} style={{ marginTop: '1rem' }}>
              Retry Loading Accounts
            </button>
          </div>
        ) : loadingTxns ? (
          <div className="loading-center"><div className="spinner spinner-lg" /></div>
        ) : txns.length === 0 ? (
          <div className="empty-state">
            <div className="empty-icon" style={{ fontSize: '1.5rem', opacity: 0.3 }}>—</div>
            <h4>No transactions yet</h4>
            <p>Transfers and bill payments for account {selected} will appear here.</p>
          </div>
        ) : (
          <div className="table-wrap fade-up">
            <table>
              <thead>
                <tr>
                  <th>Date & Time</th>
                  <th>Transaction ID</th>
                  <th>Type</th>
                  <th>From</th>
                  <th>To</th>
                  <th>Amount</th>
                  <th>Status</th>
                  <th>Receipt</th>
                </tr>
              </thead>
              <tbody>
                {txns.map(tx => {
                  const isCredit = tx.destinationAccount === selected
                  return (
                    <tr key={tx.id} id={`txn-row-${tx.id}`}>
                      <td><small style={{ color: 'var(--text-muted)' }}>{fmtDate(tx.timestamp)}</small></td>
                      <td>
                        <code style={{ fontSize: '.78rem', color: 'var(--text-second)' }}>
                          {tx.transactionId?.slice(0, 14)}…
                        </code>
                      </td>
                      <td>
                        <span className="badge badge-neutral">{tx.type || 'TRANSFER'}</span>
                      </td>
                      <td style={{ fontFamily: 'monospace', fontSize: '.8rem' }}>{tx.sourceAccount}</td>
                      <td style={{ fontFamily: 'monospace', fontSize: '.8rem' }}>{tx.destinationAccount}</td>
                      <td style={{ fontWeight: 600, color: isCredit ? 'var(--success)' : 'var(--text-primary)' }}>
                        {isCredit ? '+' : '-'}{fmtMoney(tx.amount)}
                      </td>
                      <td>
                        <span className={`badge ${tx.status === 'SUCCESS' ? 'badge-success' : 'badge-danger'}`}>
                          {tx.status}
                        </span>
                      </td>
                      <td>
                        <button
                          type="button"
                          className="btn btn-secondary btn-sm"
                          style={{ padding: '3px 8px', fontSize: '.75rem', display: 'flex', alignItems: 'center', gap: '3px' }}
                          onClick={() => handleOpenReceipt(tx)}
                        >
                          Receipt
                        </button>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Authorized Masked Receipt Modal with Short Link */}
      <ReceiptModal
        isOpen={receiptModalOpen}
        onClose={() => {
          setReceiptModalOpen(false)
          setActiveTxn(null)
          setShortUrl('')
        }}
        transaction={activeTxn}
        shortUrl={shortUrl}
        loading={receiptLoading}
      />
    </>
  )
}
