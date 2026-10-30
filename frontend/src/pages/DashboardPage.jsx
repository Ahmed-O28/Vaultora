import { useState, useEffect, useCallback } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { accounts, customers, billerApi } from '../services/api'
import MpinModal from '../components/MpinModal'

function fmtMoney(v) {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(v ?? 0)
}
function fmtDate(iso) {
  return new Date(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}
function fmtTime(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' })
}

function QuickAction({ to, icon, label, id }) {
  return (
    <Link to={to} id={id} className="card flex items-center gap-3"
      style={{ textDecoration: 'none', padding: '1rem 1.25rem', transition: 'all .2s', cursor: 'pointer' }}>
      <div className="stat-icon">{icon}</div>
      <span style={{ fontWeight: 500, fontSize: '.9375rem' }}>{label}</span>
    </Link>
  )
}

export default function DashboardPage() {
  const { user } = useAuth()
  const [accs, setAccs] = useState([])
  const [txns, setTxns] = useState([])
  const [billers, setBillers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  // MPIN & Balance Check Modal state
  const [mpinModalOpen, setMpinModalOpen] = useState(false)
  const [targetAccountForMpin, setTargetAccountForMpin] = useState(null) // null = all / total
  const [revealedAccounts, setRevealedAccounts] = useState(new Set())
  const [isTotalRevealed, setIsTotalRevealed] = useState(false)

  const loadDashboard = useCallback(async () => {
    if (!user?.customerId) return
    try {
      const [customerAccs, billerData] = await Promise.all([
        accounts.getForCustomer(user.customerId),
        billerApi.getBillers().catch(() => null)
      ])
      setAccs(customerAccs || [])
      if (billerData) {
        setBillers(billerData.billers || [])
      }
      // Load recent transactions from the first account
      if (customerAccs?.length > 0) {
        const recentTxns = await accounts.getTransactions(customerAccs[0].accountNumber)
        setTxns((recentTxns || []).slice(0, 5))
      }
    } catch (err) {
      setError(err.message || 'Failed to load dashboard data.')
    } finally {
      setLoading(false)
    }
  }, [user?.customerId])

  useEffect(() => {
    loadDashboard()
  }, [loadDashboard])

  // Open MPIN modal to check balance
  function handlePromptCheckBalance(account = null) {
    setTargetAccountForMpin(account)
    setMpinModalOpen(true)
  }

  // Verify MPIN and reveal balance
  const handleVerifyBalanceMpin = useCallback(async (mpin) => {
    const accNum = targetAccountForMpin ? targetAccountForMpin.accountNumber : null
    const res = await customers.verifyMpin(user.customerId, {
      mpin,
      accountNumber: accNum
    })

    if (res.verified) {
      if (targetAccountForMpin) {
        setRevealedAccounts(prev => new Set(prev).add(targetAccountForMpin.accountNumber))
      } else {
        // Reveal all
        setIsTotalRevealed(true)
        setRevealedAccounts(new Set(accs.map(a => a.accountNumber)))
      }
      setMpinModalOpen(false)
    }
  }, [user?.customerId, targetAccountForMpin, accs])

  function toggleHideAccount(accountNumber) {
    setRevealedAccounts(prev => {
      const next = new Set(prev)
      next.delete(accountNumber)
      return next
    })
    setIsTotalRevealed(false)
  }

  const totalBalance = accs.reduce((sum, a) => sum + Number(a.balance), 0)

  return (
    <>
      {/* Page header */}
      <div className="page-header">
        <div>
          <h3 style={{ margin: 0 }}>Dashboard</h3>
          <p style={{ margin: 0, fontSize: '.85rem' }}>
            {new Date().toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' })}
          </p>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '.75rem' }}>
          <div style={{
            width: 38, height: 38, borderRadius: '50%',
            background: 'var(--accent-light)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: '1rem', fontWeight: 700, color: 'var(--accent-dark)'
          }}>
            {user?.fullName?.[0]?.toUpperCase()}
          </div>
          <div>
            <div style={{ fontSize: '.875rem', fontWeight: 600 }}>{user?.fullName}</div>
            <div style={{ fontSize: '.75rem', color: 'var(--text-muted)' }}>Customer #{user?.customerId}</div>
          </div>
        </div>
      </div>

      {/* Page body */}
      <div className="page-body">
        
        {/* Google Pay-style Hero Balance Card */}
        <div className="hero-card fade-up" style={{ marginBottom: '1.5rem', position: 'relative', overflow: 'hidden' }}>
          <div className="flex justify-between items-center" style={{ marginBottom: '.25rem' }}>
            <p style={{ color: 'rgba(255,255,255,.65)', fontSize: '.8125rem', letterSpacing: '.06em', textTransform: 'uppercase', margin: 0 }}>
              Total Available Balance
            </p>
            <span style={{ fontSize: '.75rem', color: 'rgba(255,255,255,.55)', display: 'flex', alignItems: 'center', gap: '.35rem' }}>
              MPIN Protected
            </span>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', margin: '.5rem 0' }}>
            <div style={{ fontSize: '2.5rem', fontWeight: 800, letterSpacing: '-.04em' }}>
              {loading
                ? '——'
                : isTotalRevealed
                ? fmtMoney(totalBalance)
                : '$ ••••••'}
            </div>

            {!loading && (
              isTotalRevealed ? (
                <button
                  type="button"
                  onClick={() => { setIsTotalRevealed(false); setRevealedAccounts(new Set()) }}
                  className="btn btn-sm"
                  style={{
                    background: 'rgba(255,255,255,0.15)',
                    color: '#ffffff',
                    border: 'none',
                    borderRadius: '20px',
                    padding: '.35rem .75rem',
                    fontSize: '.8rem'
                  }}
                  title="Hide Balance"
                >
                  Hide
                </button>
              ) : (
                <button
                  type="button"
                  id="btn-check-total-balance"
                  onClick={() => handlePromptCheckBalance(null)}
                  className="btn btn-sm"
                  style={{
                    background: 'rgba(255,255,255,0.25)',
                    color: '#ffffff',
                    border: '1px solid rgba(255,255,255,0.4)',
                    borderRadius: '20px',
                    padding: '.35rem .85rem',
                    fontSize: '.85rem',
                    fontWeight: 600,
                    backdropFilter: 'blur(4px)',
                    transition: 'all 0.2s ease'
                  }}
                  onMouseEnter={(e) => e.currentTarget.style.background = 'rgba(255,255,255,0.35)'}
                  onMouseLeave={(e) => e.currentTarget.style.background = 'rgba(255,255,255,0.25)'}
                >
                  Check Balance
                </button>
              )
            )}
          </div>

          <p style={{ color: 'rgba(255,255,255,.55)', fontSize: '.875rem', margin: 0 }}>
            Across {accs.length} account{accs.length !== 1 ? 's' : ''} · Enter MPIN to view balance
          </p>
        </div>

        {error && <div className="alert alert-danger" style={{ marginBottom: '1rem' }}>{error}</div>}

        {/* Quick actions */}
        <h4 className="section-title">Quick actions</h4>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))', gap: '1rem', marginBottom: '2rem' }}>
          <QuickAction to="/transfer"      icon="⇄" label="Transfer Funds" id="qa-transfer" />
          <QuickAction to="/bills"         icon="◎" label="Pay a Bill"     id="qa-bills" />
          <QuickAction to="/transactions"  icon="≡" label="Transactions"   id="qa-transactions" />
          <QuickAction to="/beneficiaries" icon="⊕" label="Beneficiaries"  id="qa-beneficiaries" />
        </div>

        {/* Accounts with Google Pay-Style Check Balance */}
        <div className="flex justify-between items-center" style={{ marginBottom: '.75rem' }}>
          <h4 className="section-title" style={{ margin: 0 }}>My accounts</h4>
          <span style={{ fontSize: '.8rem', color: 'var(--text-muted)' }}>
            Google Pay style MPIN security active
          </span>
        </div>

        {loading ? (
          <div className="loading-center"><div className="spinner" /></div>
        ) : accs.length === 0 ? (
          <div className="empty-state">
            <div className="empty-icon" style={{ fontSize: '1.5rem', opacity: 0.3 }}>—</div>
            <p>No accounts found for your profile.</p>
          </div>
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: '1rem', marginBottom: '2rem' }}>
            {accs.map(acc => {
              const isRevealed = revealedAccounts.has(acc.accountNumber)
              return (
                <div key={acc.id} className="stat-card fade-up" id={`account-card-${acc.accountNumber}`}>
                  <div className="flex justify-between items-center">
                    <span className="stat-label" style={{ fontWeight: 600 }}>{acc.accountNumber}</span>
                    <span className="badge badge-success" style={{ fontSize: '.7rem' }}>Active</span>
                  </div>

                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', margin: '.5rem 0' }}>
                    <div className="stat-value" style={{ margin: 0 }}>
                      {isRevealed ? fmtMoney(acc.balance) : '$ ••••••'}
                    </div>

                    {isRevealed ? (
                      <button
                        type="button"
                        onClick={() => toggleHideAccount(acc.accountNumber)}
                        className="btn btn-ghost btn-sm"
                        style={{ fontSize: '.75rem', color: 'var(--text-muted)' }}
                        title="Hide balance"
                      >
                        Hide
                      </button>
                    ) : (
                      <button
                        type="button"
                        id={`btn-check-balance-${acc.accountNumber}`}
                        onClick={() => handlePromptCheckBalance(acc)}
                        className="btn btn-secondary btn-sm"
                        style={{
                          fontSize: '.75rem',
                          padding: '.25rem .65rem',
                          borderRadius: '16px',
                          border: '1.5px solid var(--accent)',
                          color: 'var(--accent-dark)',
                          background: 'var(--accent-light)',
                          fontWeight: 600
                        }}
                      >
                        Check Balance
                      </button>
                    )}
                  </div>

                  <div style={{ fontSize: '.8rem', color: 'var(--text-muted)' }}>
                    Shard {acc.shardId ?? 1} · {acc.customerName}
                  </div>
                </div>
              )
            })}
          </div>
        )}

        {/* Partner Billers & Live Service Status */}
        <div style={{ marginBottom: '2rem' }}>
          <div className="flex justify-between items-center" style={{ marginBottom: '.75rem' }}>
            <div>
              <h4 className="section-title" style={{ margin: 0 }}>Partner Billers & Service Status</h4>
              <small style={{ color: 'var(--text-muted)' }}>
                Partner biller directory managed by system administrators
              </small>
            </div>
            <Link to="/bills" style={{ fontSize: '.875rem', color: 'var(--accent)', fontWeight: 500 }}>
              Pay bill →
            </Link>
          </div>

          {billers.length === 0 ? (
            <div className="card" style={{ padding: '1.25rem', color: 'var(--text-muted)', fontSize: '.875rem' }}>
              Biller status directory loading...
            </div>
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: '0.875rem' }}>
              {billers.map(b => {
                const isActive = b.status === 'ACTIVE'
                return (
                  <div
                    key={b.billerId}
                    id={`biller-status-card-${b.billerId}`}
                    className="card fade-up"
                    style={{
                      padding: '1rem',
                      display: 'flex',
                      flexDirection: 'column',
                      justifyContent: 'space-between',
                      opacity: isActive ? 1 : 0.65,
                      border: isActive ? '1px solid var(--border)' : '1px dashed var(--danger)',
                      background: isActive ? 'var(--bg-card)' : 'rgba(211, 47, 47, 0.04)'
                    }}
                  >
                    <div>
                      <div className="flex justify-between items-center" style={{ marginBottom: '.35rem' }}>
                        <span style={{ fontSize: '.75rem', color: 'var(--text-muted)' }}>{b.category}</span>
                        <span className={`badge ${isActive ? 'badge-success' : 'badge-danger'}`} style={{ fontSize: '.7rem' }}>
                          {isActive ? 'Active' : 'Inactive'}
                        </span>
                      </div>
                      <div style={{ fontWeight: 600, fontSize: '.95rem', color: isActive ? 'var(--text-main)' : 'var(--text-second)' }}>
                        {b.name}
                      </div>
                      <div style={{ fontSize: '.75rem', fontFamily: 'monospace', color: 'var(--text-muted)', marginTop: '.2rem' }}>
                        {b.billerId}
                      </div>
                    </div>

                    <div style={{ marginTop: '.75rem', paddingTop: '.5rem', borderTop: '1px solid rgba(0,0,0,0.06)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <span style={{ fontSize: '.75rem', color: isActive ? 'var(--success)' : 'var(--danger)', fontWeight: 500 }}>
                        {isActive ? 'Ready for Payments' : 'Temporarily Unavailable'}
                      </span>
                      {isActive ? (
                        <Link to="/bills" className="btn btn-ghost btn-sm" style={{ fontSize: '.75rem', padding: '0.2rem 0.5rem' }}>
                          Pay →
                        </Link>
                      ) : (
                        <span style={{ fontSize: '.7rem', color: 'var(--text-muted)' }}>Payments Paused</span>
                      )}
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </div>

        {/* Recent transactions */}
        {txns.length > 0 && (
          <>
            <div className="flex justify-between items-center" style={{ marginBottom: '1rem' }}>
              <h4 className="section-title" style={{ margin: 0 }}>Recent transactions</h4>
              <Link to="/transactions" style={{ fontSize: '.875rem', color: 'var(--accent)', fontWeight: 500 }}>
                View all →
              </Link>
            </div>
            <div className="table-wrap fade-up" style={{ marginBottom: '2rem' }}>
              <table>
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>From</th>
                    <th>To</th>
                    <th>Amount</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {txns.map(tx => (
                    <tr key={tx.id}>
                      <td><small>{fmtDate(tx.timestamp)}</small></td>
                      <td style={{ fontFamily: 'monospace', fontSize: '.8rem' }}>{tx.sourceAccount}</td>
                      <td style={{ fontFamily: 'monospace', fontSize: '.8rem' }}>{tx.destinationAccount}</td>
                      <td style={{ fontWeight: 600 }}>{fmtMoney(tx.amount)}</td>
                      <td>
                        <span className={`badge ${tx.status === 'SUCCESS' ? 'badge-success' : 'badge-danger'}`}>
                          {tx.status}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
      </div>

      {/* Google Pay-style MPIN Modal */}
      <MpinModal
        isOpen={mpinModalOpen}
        onClose={() => setMpinModalOpen(false)}
        onSubmit={handleVerifyBalanceMpin}
        title="Check Account Balance"
        subtitle={targetAccountForMpin ? `Account: ${targetAccountForMpin.accountNumber}` : 'All accounts balance check'}
        accountNumber={targetAccountForMpin?.accountNumber}
      />
    </>
  )
}
