import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { adminApi } from '../services/api'

export default function AdminDashboardPage() {
  const navigate = useNavigate()
  const [adminUser, setAdminUser] = useState(null)
  const [stats, setStats] = useState(null)
  const [customers, setCustomers] = useState([])
  const [transactions, setTransactions] = useState([])
  const [billers, setBillers] = useState([])
  const [billerLoadingId, setBillerLoadingId] = useState(null)
  const [billerSuccessMsg, setBillerSuccessMsg] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  // Tab: 'customers' | 'transactions' | 'billers'
  const [activeTab, setActiveTab] = useState('customers')

  // Customer filters
  const [customerSearch, setCustomerSearch] = useState('')
  const [customerShard, setCustomerShard] = useState('')

  // Transaction filters
  const [txnSearch, setTxnSearch] = useState('')
  const [txnType, setTxnType] = useState('')
  const [txnShard, setTxnShard] = useState('')

  useEffect(() => {
    const stored = localStorage.getItem('adminUser')
    if (stored) {
      try {
        setAdminUser(JSON.parse(stored))
      } catch (_) {}
    }
  }, [])

  const loadData = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const [statsRes, custRes, txnRes, billersRes] = await Promise.all([
        adminApi.getStats(),
        adminApi.getCustomers({ search: customerSearch, shardId: customerShard || undefined }),
        adminApi.getTransactions({ search: txnSearch, type: txnType || undefined, shardId: txnShard || undefined }),
        adminApi.getBillers().catch(() => [])
      ])
      setStats(statsRes)
      setCustomers(custRes || [])
      setTransactions(txnRes || [])
      if (billersRes) setBillers(billersRes)
    } catch (err) {
      setError(err.message || 'Failed to load multi-shard admin data.')
    } finally {
      setLoading(false)
    }
  }, [customerSearch, customerShard, txnSearch, txnType, txnShard])

  useEffect(() => {
    loadData()
  }, [loadData])

  const handleToggleBillerStatus = async (biller) => {
    setBillerLoadingId(biller.billerId)
    setError('')
    setBillerSuccessMsg('')
    try {
      const targetStatus = biller.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
      const updated = await adminApi.updateBillerStatus(biller.billerId, targetStatus)
      setBillers(prev => prev.map(b => b.billerId === biller.billerId ? updated : b))
      setBillerSuccessMsg(`Successfully updated ${biller.name} to ${updated.status}. Saved to PostgreSQL.`)
      setTimeout(() => setBillerSuccessMsg(''), 4500)
    } catch (err) {
      setError(err.message || 'Failed to update biller status.')
    } finally {
      setBillerLoadingId(null)
    }
  }

  const handleLogout = () => {
    localStorage.removeItem('adminToken')
    localStorage.removeItem('adminUser')
    navigate('/admin/login')
  }

  const formatCurrency = (val) => {
    if (val == null) return '$0.00'
    return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(val)
  }

  const formatDateTime = (iso) => {
    if (!iso) return '—'
    return new Date(iso).toLocaleString('en-US', {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    })
  }

  const activeBillersCount = billers.filter(b => b.status === 'ACTIVE').length
  const inactiveBillersCount = billers.filter(b => b.status === 'INACTIVE').length

  return (
    <div className="app-layout">
      {/* ── Top Bar ── */}
      <header className="topbar">
        <div className="flex items-center gap-3">
          <div className="brand" style={{ display: 'flex', alignItems: 'center', gap: '.65rem' }}>
            <span style={{ fontSize: '1.25rem', fontWeight: 800, letterSpacing: '-0.02em', color: '#fff' }}>
              Vault<span style={{ color: 'var(--accent)' }}>ora</span>
            </span>
            <span style={{
              fontSize: '0.7rem',
              fontWeight: 700,
              textTransform: 'uppercase',
              letterSpacing: '0.06em',
              padding: '0.2rem 0.5rem',
              borderRadius: '4px',
              background: 'rgba(233, 154, 101, 0.15)',
              color: 'var(--accent)',
              border: '1px solid rgba(233, 154, 101, 0.3)'
            }}>
              Admin Console
            </span>
          </div>
          <span style={{ fontSize: '0.8rem', color: 'rgba(255, 255, 255, 0.55)', paddingLeft: '.5rem', borderLeft: '1px solid rgba(255,255,255,.15)' }}>
            Multi-Shard Distributed Monitor
          </span>
        </div>

        <div className="flex items-center gap-4">
          <div style={{ textAlign: 'right', fontSize: '0.85rem' }}>
            <div style={{ fontWeight: 600, color: '#fff' }}>{adminUser?.username || 'Administrator'}</div>
            <div style={{ color: 'rgba(255,255,255,0.6)', fontSize: '0.75rem' }}>{adminUser?.role || 'SYSTEM_ADMIN'}</div>
          </div>
          <button
            id="btn-admin-logout"
            type="button"
            className="btn btn-outline btn-sm"
            onClick={handleLogout}
            style={{ fontSize: '0.8rem', borderColor: 'rgba(255,255,255,.25)', color: '#fff' }}
          >
            Sign out
          </button>
        </div>
      </header>

      {/* ── Main Content Container ── */}
      <main className="content" style={{ padding: '2rem', maxWidth: '1400px', margin: '0 auto', width: '100%' }}>
        {/* Header Title & Subtitle */}
        <div style={{ marginBottom: '2rem' }}>
          <h2 style={{ margin: 0, fontSize: '1.75rem', fontWeight: 700 }}>Distributed System Dashboard</h2>
          <p style={{ margin: '0.35rem 0 0', color: 'var(--text-second)', fontSize: '0.95rem' }}>
            Central monitoring across 4 PostgreSQL Database Shards (Ports 5432-5435) with Consistent Hashing & Admin Biller Controls
          </p>
        </div>

        {error && (
          <div className="alert alert-danger" style={{ marginBottom: '1.5rem' }}>
            {error}
          </div>
        )}

        {/* ── System Statistics Cards ── */}
        <div className="stat-grid" style={{ marginBottom: '2rem' }}>
          {/* Card 1: Total Customers */}
          <div className="stat-card">
            <span className="stat-label">Total Customers</span>
            <span className="stat-value">{stats?.totalCustomers ?? '—'}</span>
            <div style={{ display: 'flex', gap: '.4rem', flexWrap: 'wrap', marginTop: '.25rem', fontSize: '.75rem', color: 'var(--text-muted)' }}>
              <span>S1: <strong>{stats?.shard1CustomerCount ?? 0}</strong></span> ·
              <span>S2: <strong>{stats?.shard2CustomerCount ?? 0}</strong></span> ·
              <span>S3: <strong>{stats?.shard3CustomerCount ?? 0}</strong></span> ·
              <span>S4: <strong>{stats?.shard4CustomerCount ?? 0}</strong></span>
            </div>
          </div>

          {/* Card 2: Total Transactions */}
          <div className="stat-card">
            <span className="stat-label">Total Transactions</span>
            <span className="stat-value">{stats?.totalTransactions ?? '—'}</span>
            <div style={{ display: 'flex', gap: '.4rem', flexWrap: 'wrap', marginTop: '.25rem', fontSize: '.75rem', color: 'var(--text-muted)' }}>
              <span>S1: <strong>{stats?.shard1TransactionCount ?? 0}</strong></span> ·
              <span>S2: <strong>{stats?.shard2TransactionCount ?? 0}</strong></span> ·
              <span>S3: <strong>{stats?.shard3TransactionCount ?? 0}</strong></span> ·
              <span>S4: <strong>{stats?.shard4TransactionCount ?? 0}</strong></span>
            </div>
          </div>

          {/* Card 3: Total Deposit Volume */}
          <div className="stat-card">
            <span className="stat-label">Total Deposit Volume</span>
            <span className="stat-value" style={{ color: 'var(--accent-dark)' }}>
              {stats ? formatCurrency(stats.totalDepositVolume) : '—'}
            </span>
            <small style={{ color: 'var(--text-muted)' }}>Aggregate balance under custody</small>
          </div>

          {/* Card 4: Partner Billers */}
          <div className="stat-card">
            <div className="flex justify-between items-center">
              <span className="stat-label">Partner Billers</span>
              <span className="badge badge-success" style={{ fontSize: '0.75rem' }}>
                ADMIN CONTROLLED
              </span>
            </div>
            <div className="stat-value" style={{ fontSize: '1.4rem', margin: '.25rem 0' }}>
              {activeBillersCount} <span style={{ fontSize: '.9rem', fontWeight: 400, color: 'var(--text-second)' }}>/ {billers.length} Active</span>
            </div>
            <small style={{ color: 'var(--text-muted)' }}>
              Stored in database · Real-time enforcement
            </small>
          </div>
        </div>

        {/* ── Main Tab Panel ── */}
        <div className="card" style={{ padding: '1.5rem' }}>
          <div style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            borderBottom: '1px solid var(--border)',
            paddingBottom: '1rem',
            marginBottom: '1.25rem',
            flexWrap: 'wrap',
            gap: '1rem'
          }}>
            {/* Tabs */}
            <div className="flex gap-2">
              <button
                id="tab-customers"
                type="button"
                className={`btn ${activeTab === 'customers' ? 'btn-primary' : 'btn-secondary'}`}
                onClick={() => setActiveTab('customers')}
                style={{ fontSize: '0.875rem' }}
              >
                Customers ({customers.length})
              </button>
              <button
                id="tab-transactions"
                type="button"
                className={`btn ${activeTab === 'transactions' ? 'btn-primary' : 'btn-secondary'}`}
                onClick={() => setActiveTab('transactions')}
                style={{ fontSize: '0.875rem' }}
              >
                Transactions ({transactions.length})
              </button>
              <button
                id="tab-billers"
                type="button"
                className={`btn ${activeTab === 'billers' ? 'btn-primary' : 'btn-secondary'}`}
                onClick={() => setActiveTab('billers')}
                style={{ fontSize: '0.875rem' }}
              >
                Partner Billers ({billers.length})
                {inactiveBillersCount > 0 && (
                  <span className="badge badge-warn" style={{ marginLeft: '.5rem', padding: '0.15rem 0.45rem', fontSize: '0.75rem' }}>
                    {inactiveBillersCount} inactive
                  </span>
                )}
              </button>
            </div>

            {/* Privacy indicator */}
            <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', display: 'flex', alignItems: 'center', gap: '.4rem' }}>
              <span>Security: Passwords & MPIN hashes protected by zero-exposure architecture</span>
            </div>
          </div>

          {/* ── CUSTOMERS TAB ── */}
          {activeTab === 'customers' && (
            <div>
              {/* Customer Filters */}
              <div style={{ display: 'flex', gap: '1rem', marginBottom: '1.25rem', flexWrap: 'wrap' }}>
                <input
                  id="filter-customer-search"
                  type="text"
                  className="input"
                  placeholder="Search customer by name, email, account..."
                  value={customerSearch}
                  onChange={(e) => setCustomerSearch(e.target.value)}
                  style={{ flex: 1, minWidth: '240px' }}
                />

                <select
                  id="filter-customer-shard"
                  className="input"
                  value={customerShard}
                  onChange={(e) => setCustomerShard(e.target.value)}
                  style={{ width: '220px' }}
                >
                  <option value="">All Database Shards</option>
                  <option value="1">Shard 1 (Port 5432)</option>
                  <option value="2">Shard 2 (Port 5433)</option>
                  <option value="3">Shard 3 (Port 5434)</option>
                  <option value="4">Shard 4 (Port 5435)</option>
                </select>

                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={loadData}
                  disabled={loading}
                >
                  {loading ? <span className="spinner" /> : 'Refresh'}
                </button>
              </div>

              {/* Customers Table */}
              <div className="table-wrap">
                <table id="table-admin-customers">
                  <thead>
                    <tr>
                      <th>Customer ID</th>
                      <th>Full Name</th>
                      <th>Email</th>
                      <th>Account Number</th>
                      <th style={{ textAlign: 'right' }}>Balance</th>
                      <th>Assigned Shard</th>
                      <th>Status</th>
                      <th>Created At</th>
                    </tr>
                  </thead>
                  <tbody>
                    {customers.length === 0 ? (
                      <tr>
                        <td colSpan="8" style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>
                          No customers found matching the search criteria.
                        </td>
                      </tr>
                    ) : (
                      customers.map((c) => (
                        <tr key={c.customerId}>
                          <td><code>#{c.customerId}</code></td>
                          <td><strong>{c.fullName}</strong></td>
                          <td>{c.email}</td>
                          <td><code>{c.accountNumber}</code></td>
                          <td style={{ textAlign: 'right', fontWeight: 600 }}>
                            {formatCurrency(c.balance)}
                          </td>
                          <td>
                            <span className={`badge ${
                              c.shardId === 1 ? 'badge-primary' :
                              c.shardId === 2 ? 'badge-info' :
                              c.shardId === 3 ? 'badge-success' : 'badge-accent'
                            }`}>
                              Shard {c.shardId}
                            </span>
                          </td>
                          <td>
                            <span className="badge badge-success">{c.status || 'ACTIVE'}</span>
                          </td>
                          <td style={{ fontSize: '0.8rem', color: 'var(--text-second)' }}>
                            {formatDateTime(c.createdAt)}
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* ── TRANSACTIONS TAB ── */}
          {activeTab === 'transactions' && (
            <div>
              {/* Transaction Filters */}
              <div style={{ display: 'flex', gap: '1rem', marginBottom: '1.25rem', flexWrap: 'wrap' }}>
                <input
                  id="filter-txn-search"
                  type="text"
                  className="input"
                  placeholder="Search by Txn ID, source, destination, description..."
                  value={txnSearch}
                  onChange={(e) => setTxnSearch(e.target.value)}
                  style={{ flex: 1, minWidth: '240px' }}
                />

                <select
                  id="filter-txn-type"
                  className="input"
                  value={txnType}
                  onChange={(e) => setTxnType(e.target.value)}
                  style={{ width: '180px' }}
                >
                  <option value="">All Types</option>
                  <option value="TRANSFER">TRANSFER</option>
                  <option value="BILL_PAYMENT">BILL PAYMENT</option>
                </select>

                <select
                  id="filter-txn-shard"
                  className="input"
                  value={txnShard}
                  onChange={(e) => setTxnShard(e.target.value)}
                  style={{ width: '220px' }}
                >
                  <option value="">All Shards</option>
                  <option value="1">Shard 1 (Port 5432)</option>
                  <option value="2">Shard 2 (Port 5433)</option>
                  <option value="3">Shard 3 (Port 5434)</option>
                  <option value="4">Shard 4 (Port 5435)</option>
                </select>

                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={loadData}
                  disabled={loading}
                >
                  {loading ? <span className="spinner" /> : 'Refresh'}
                </button>
              </div>

              {/* Transactions Table */}
              <div className="table-wrap">
                <table id="table-admin-transactions">
                  <thead>
                    <tr>
                      <th>Transaction ID</th>
                      <th>Source Account</th>
                      <th>Destination Account</th>
                      <th style={{ textAlign: 'right' }}>Amount</th>
                      <th>Type</th>
                      <th>Status</th>
                      <th>Shard</th>
                      <th>Timestamp</th>
                      <th>Description</th>
                    </tr>
                  </thead>
                  <tbody>
                    {transactions.length === 0 ? (
                      <tr>
                        <td colSpan="9" style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>
                          No transactions found matching the filter criteria.
                        </td>
                      </tr>
                    ) : (
                      transactions.map((t) => (
                        <tr key={t.id || t.transactionId}>
                          <td><code>{t.transactionId}</code></td>
                          <td><code>{t.sourceAccount}</code></td>
                          <td><code>{t.destinationAccount}</code></td>
                          <td style={{ textAlign: 'right', fontWeight: 600, color: 'var(--text-main)' }}>
                            {formatCurrency(t.amount)}
                          </td>
                          <td>
                            <span className={`badge ${t.type === 'TRANSFER' ? 'badge-info' : 'badge-neutral'}`}>
                              {t.type}
                            </span>
                          </td>
                          <td>
                            <span className={`badge ${t.status === 'SUCCESS' ? 'badge-success' : 'badge-danger'}`}>
                              {t.status}
                            </span>
                          </td>
                          <td>
                            <span className={`badge ${
                              t.shardId === 1 ? 'badge-primary' :
                              t.shardId === 2 ? 'badge-info' :
                              t.shardId === 3 ? 'badge-success' : 'badge-accent'
                            }`}>
                              Shard {t.shardId}
                            </span>
                          </td>
                          <td style={{ fontSize: '0.8rem', color: 'var(--text-second)' }}>
                            {formatDateTime(t.timestamp)}
                          </td>
                          <td style={{ fontSize: '0.85rem', color: 'var(--text-second)', maxWidth: '240px' }}>
                            {t.description || '—'}
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* ── BILLER MANAGEMENT TAB ── */}
          {activeTab === 'billers' && (
            <div>
              <div style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                background: 'var(--bg-warm)',
                padding: '1rem 1.25rem',
                borderRadius: '8px',
                border: '1px solid var(--border)',
                marginBottom: '1.5rem',
                flexWrap: 'wrap',
                gap: '1rem'
              }}>
                <div>
                  <h4 style={{ margin: '0 0 .25rem 0' }}>Admin-Controlled Biller Management</h4>
                  <p style={{ margin: 0, fontSize: '0.85rem', color: 'var(--text-second)' }}>
                    Activate or deactivate partner billers. Changes are stored in PostgreSQL and immediately prevent inactive billers from receiving customer payments.
                  </p>
                </div>

                <div style={{ display: 'flex', gap: '.5rem', alignItems: 'center' }}>
                  <span className="badge badge-success" style={{ fontSize: '.8rem' }}>
                    {activeBillersCount} Active
                  </span>
                  <span className="badge badge-neutral" style={{ fontSize: '.8rem' }}>
                    {inactiveBillersCount} Inactive
                  </span>
                </div>
              </div>

              {billerSuccessMsg && (
                <div className="alert alert-success" style={{ marginBottom: '1.25rem' }}>
                  {billerSuccessMsg}
                </div>
              )}

              <div className="table-wrap">
                <table id="table-admin-billers">
                  <thead>
                    <tr>
                      <th>Biller ID</th>
                      <th>Biller Name</th>
                      <th>Category</th>
                      <th>Customer Payment Status</th>
                      <th>Last Updated</th>
                      <th style={{ textAlign: 'right' }}>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {billers.length === 0 ? (
                      <tr>
                        <td colSpan={6} style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-muted)' }}>
                          No billers found in database.
                        </td>
                      </tr>
                    ) : (
                      billers.map(b => {
                        const isActive = b.status === 'ACTIVE'
                        const isUpdating = billerLoadingId === b.billerId
                        return (
                          <tr key={b.billerId} id={`biller-row-${b.billerId}`}>
                            <td>
                              <code style={{ fontSize: '.85rem', fontWeight: 600 }}>{b.billerId}</code>
                            </td>
                            <td>
                              <strong>{b.name}</strong>
                            </td>
                            <td>
                              <span className="badge badge-neutral">{b.category}</span>
                            </td>
                            <td>
                              <span
                                id={`badge-status-${b.billerId}`}
                                className={`badge ${isActive ? 'badge-success' : 'badge-danger'}`}
                                style={{ fontWeight: 600 }}
                              >
                                {isActive ? 'ACTIVE' : 'INACTIVE'}
                              </span>
                            </td>
                            <td style={{ fontSize: '.8rem', color: 'var(--text-muted)' }}>
                              {formatDateTime(b.lastUpdated)}
                            </td>
                            <td style={{ textAlign: 'right' }}>
                              <button
                                id={`btn-toggle-biller-${b.billerId}`}
                                type="button"
                                className={`btn btn-sm ${isActive ? 'btn-danger' : 'btn-success'}`}
                                onClick={() => handleToggleBillerStatus(b)}
                                disabled={isUpdating}
                                style={{ minWidth: '110px', fontSize: '.8rem' }}
                              >
                                {isUpdating ? (
                                  <span className="spinner" />
                                ) : isActive ? (
                                  'Deactivate'
                                ) : (
                                  'Activate'
                                )}
                              </button>
                            </td>
                          </tr>
                        )
                      })
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      </main>
    </div>
  )
}
