import { useState, useEffect, useCallback, useRef } from 'react'
import { useAuth } from '../context/AuthContext'
import { accounts as accsApi, transfers, beneficiaries as beneApi, autocomplete as autoApi, shortener } from '../services/api'
import MpinModal from '../components/MpinModal'
import AutocompleteInput from '../components/AutocompleteInput'
import ReceiptModal from '../components/ReceiptModal'

function fmtMoney(v) {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(v ?? 0)
}

export default function TransferPage() {
  const { user } = useAuth()
  const [myAccs, setMyAccs]               = useState([])
  const [allAccs, setAllAccs]             = useState([])
  const [beneficiaries, setBeneficiaries] = useState([])
  const [destinationMode, setDestinationMode] = useState('list') // 'list' | 'custom'
  const [customSearchQuery, setCustomSearchQuery] = useState('')

  const [form, setForm] = useState({
    sourceAccount: '',
    destinationAccount: '',
    amount: '',
    description: ''
  })

  const [result, setResult]     = useState(null)
  const [receiptShortUrl, setReceiptShortUrl] = useState('')
  const [receiptModalOpen, setReceiptModalOpen] = useState(false)
  const [error, setError]       = useState('')
  const [loading, setLoading]   = useState(false)
  const [accsLoading, setAccsLoading] = useState(true)

  // MPIN modal state
  const [mpinModalOpen, setMpinModalOpen] = useState(false)
  const isTransferringRef = useRef(false)

  // Fetch all accounts and beneficiaries
  const loadData = useCallback(async () => {
    if (!user?.customerId) return
    setAccsLoading(true)
    setError('')
    try {
      const [userAccounts, systemAccounts, userBenes] = await Promise.all([
        accsApi.getForCustomer(user.customerId).catch(() => []),
        accsApi.getAll().catch(() => []),
        beneApi.getAll(user.customerId).catch(() => []),
      ])

      setMyAccs(userAccounts || [])
      setAllAccs(systemAccounts || [])
      setBeneficiaries(userBenes || [])

      // Auto-select first source account if not selected
      if (userAccounts && userAccounts.length > 0) {
        setForm(f => {
          const currentSourceValid = userAccounts.some(a => a.accountNumber === f.sourceAccount)
          const newSource = currentSourceValid ? f.sourceAccount : userAccounts[0].accountNumber
          return { ...f, sourceAccount: newSource }
        })
      }
    } catch (err) {
      setError(err.message || 'Failed to load accounts.')
    } finally {
      setAccsLoading(false)
    }
  }, [user?.customerId])

  useEffect(() => {
    loadData()
  }, [loadData])

  // Destination candidates: system accounts + beneficiaries excluding currently selected source account
  const destinationOptions = allAccs.filter(a => a.accountNumber !== form.sourceAccount)

  // Selected source account object for quick balance lookup
  const activeSource = myAccs.find(a => a.accountNumber === form.sourceAccount)
  const sourceBalance = activeSource ? Number(activeSource.balance) : 0

  // Lookup recipient details
  const matchingDestAcc = allAccs.find(a => a.accountNumber === form.destinationAccount)
  const matchingBene = beneficiaries.find(b => b.beneficiaryAccountNumber === form.destinationAccount)
  const recipientName = matchingBene?.nickname || matchingDestAcc?.customerName || form.destinationAccount

  // Quick recipient selection helper
  function handleSelectDestination(accNum) {
    setForm(f => ({ ...f, destinationAccount: accNum }))
    if (destinationMode === 'custom') {
      setDestinationMode('list')
    }
  }

  // Quick amount percentage setter
  function handleQuickAmount(pct) {
    if (sourceBalance <= 0) return
    const calculated = (sourceBalance * pct).toFixed(2)
    setForm(f => ({ ...f, amount: calculated }))
  }

  // Initial form submission: validates inputs and prompts Google Pay MPIN modal
  function handleInitiateTransfer(e) {
    e.preventDefault()
    setError('')
    setResult(null)

    if (!form.sourceAccount) {
      setError('Please select a source account.')
      return
    }
    if (!form.destinationAccount) {
      setError('Please select or specify a destination account.')
      return
    }
    if (form.sourceAccount === form.destinationAccount) {
      setError('Source and destination accounts must be different.')
      return
    }

    const amount = parseFloat(form.amount)
    if (!amount || amount <= 0) {
      setError('Enter a valid transfer amount greater than $0.00.')
      return
    }
    if (amount > sourceBalance) {
      setError(`Insufficient funds: Available balance is ${fmtMoney(sourceBalance)}, requested ${fmtMoney(amount)}.`)
      return
    }

    // Open Google Pay MPIN modal
    setMpinModalOpen(true)
  }

  // Executed when MPIN is entered in the modal
  const handleExecuteTransferWithMpin = useCallback(async (mpin) => {
    if (isTransferringRef.current) {
      console.warn('Transfer request is already in-flight, preventing duplicate submission.')
      return
    }
    isTransferringRef.current = true
    const amount = parseFloat(form.amount)
    setLoading(true)
    try {
      const tx = await transfers.send({
        sourceAccount: form.sourceAccount,
        destinationAccount: form.destinationAccount,
        amount,
        description: form.description,
        mpin
      })
      setResult(tx)
      setForm(f => ({ ...f, destinationAccount: '', amount: '', description: '' }))
      setCustomSearchQuery('')
      setMpinModalOpen(false)

      // Automatically generate persistent short link for this receipt
      try {
        const shortResp = await shortener.shorten({ transactionId: tx.transactionId })
        if (shortResp && shortResp.shortUrl) {
          setReceiptShortUrl(shortResp.shortUrl)
        }
      } catch (shortErr) {
        console.warn('Could not auto-generate short link for receipt:', shortErr)
      }

      // Re-fetch latest account balances
      await loadData()
    } finally {
      setLoading(false)
      isTransferringRef.current = false
    }
  }, [form, loadData])

  return (
    <>
      <div className="page-header">
        <div>
          <h3 style={{ margin: 0 }}>Transfer Funds</h3>
          <p style={{ margin: 0, fontSize: '.85rem' }}>Send money between available accounts with Google Pay MPIN security</p>
        </div>
        <button
          className="btn btn-secondary btn-sm"
          onClick={loadData}
          disabled={accsLoading}
          title="Refresh available accounts"
        >
          {accsLoading ? <span className="spinner" /> : '↻ Refresh Accounts'}
        </button>
      </div>

      <div className="page-body">
        <div style={{ display: 'grid', gridTemplateColumns: 'minmax(320px, 1.2fr) minmax(280px, 0.8fr)', gap: '1.5rem', alignItems: 'start' }}>
          
          {/* Main Transfer Form Card */}
          <div className="card fade-up">
            <div className="flex justify-between items-center" style={{ marginBottom: '1.25rem' }}>
              <h4 style={{ margin: 0 }}>New transfer</h4>
              <span style={{ fontSize: '.75rem', color: 'var(--text-muted)', display: 'flex', alignItems: 'center', gap: '.3rem' }}>
                MPIN Required
              </span>
            </div>

            {error && (
              <div className="alert alert-danger" style={{ marginBottom: '1.25rem' }}>
                {error}
              </div>
            )}

            {result && (
              <div className="alert alert-success" style={{ marginBottom: '1.25rem' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '.5rem' }}>
                  <div>
                    <div style={{ fontWeight: 600, marginBottom: '.25rem' }}>Transfer completed successfully</div>
                    <div style={{ fontSize: '.85rem', color: 'inherit', opacity: 0.9 }}>
                      Transaction ID: <code>{result.transactionId}</code> · Amount: <strong>{fmtMoney(result.amount)}</strong>
                    </div>
                  </div>
                  <button
                    type="button"
                    className="btn btn-secondary btn-sm"
                    onClick={() => setReceiptModalOpen(true)}
                    style={{ fontSize: '.8rem', padding: '6px 12px', display: 'flex', alignItems: 'center', gap: '.35rem' }}
                  >
                    View Receipt & Link
                  </button>
                </div>
              </div>
            )}

            {accsLoading ? (
              <div className="loading-center" style={{ padding: '3rem 0' }}>
                <div className="spinner spinner-lg" />
              </div>
            ) : myAccs.length === 0 ? (
              <div className="empty-state">
                <div className="empty-icon" style={{ fontSize: '1.5rem', opacity: 0.3 }}>—</div>
                <h4>No active source accounts</h4>
                <p>We could not find any accounts linked to your profile. Please refresh or contact support.</p>
                <button className="btn btn-primary btn-sm" onClick={loadData} style={{ marginTop: '1rem' }}>
                  Try Again
                </button>
              </div>
            ) : (
              <form onSubmit={handleInitiateTransfer} className="flex-col gap-4">
                
                {/* 1. Source Account */}
                <div className="form-group">
                  <div className="flex justify-between items-center" style={{ marginBottom: '.35rem' }}>
                    <label htmlFor="tf-source" style={{ margin: 0, fontWeight: 500 }}>From account (Source)</label>
                    {activeSource && (
                      <span style={{ fontSize: '.8rem', color: 'var(--text-second)' }}>
                        Available: <strong style={{ color: 'var(--text-primary)' }}>{fmtMoney(sourceBalance)}</strong>
                      </span>
                    )}
                  </div>
                  <select
                    id="tf-source"
                    className="input"
                    value={form.sourceAccount}
                    onChange={e => setForm(f => ({ ...f, sourceAccount: e.target.value }))}
                    required
                  >
                    <option value="">Select source account…</option>
                    {myAccs.map(a => (
                      <option key={a.accountNumber} value={a.accountNumber}>
                        {a.accountNumber} — {fmtMoney(a.balance)} ({a.customerName || 'My Account'})
                      </option>
                    ))}
                  </select>
                </div>

                {/* 2. Destination Account */}
                <div className="form-group">
                  <div className="flex justify-between items-center" style={{ marginBottom: '.35rem' }}>
                    <label htmlFor="tf-dest" style={{ margin: 0, fontWeight: 500 }}>To account (Destination)</label>
                    <button
                      type="button"
                      className="btn btn-ghost btn-sm"
                      style={{ padding: '0 .25rem', fontSize: '.8rem', color: 'var(--accent-dark)' }}
                      onClick={() => {
                        setDestinationMode(m => m === 'list' ? 'custom' : 'list')
                        setForm(f => ({ ...f, destinationAccount: '' }))
                      }}
                    >
                      {destinationMode === 'list' ? 'Enter manual account' : 'Pick from available accounts'}
                    </button>
                  </div>

                  {destinationMode === 'list' ? (
                    <select
                      id="tf-dest-select"
                      className="input"
                      value={form.destinationAccount}
                      onChange={e => setForm(f => ({ ...f, destinationAccount: e.target.value }))}
                      required
                    >
                      <option value="">Select destination account…</option>

                      {/* Saved Beneficiaries Group */}
                      {beneficiaries.length > 0 && (
                        <optgroup label="Saved Beneficiaries">
                          {beneficiaries
                            .filter(b => b.beneficiaryAccountNumber !== form.sourceAccount)
                            .map(b => (
                              <option key={`bene-${b.beneficiaryId}`} value={b.beneficiaryAccountNumber}>
                                {b.nickname ? `${b.nickname} (${b.beneficiaryAccountNumber})` : b.beneficiaryAccountNumber}
                              </option>
                            ))}
                        </optgroup>
                      )}

                      {/* Other Bank Accounts Group */}
                      <optgroup label="Available Bank Accounts">
                        {destinationOptions.map(a => (
                          <option key={`acc-${a.accountNumber}`} value={a.accountNumber}>
                            {a.accountNumber} — {a.customerName} {a.shardId ? `(Shard ${a.shardId})` : ''}
                          </option>
                        ))}
                      </optgroup>
                    </select>
                  ) : (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '.4rem' }}>
                      <AutocompleteInput
                        id="tf-dest-auto"
                        value={customSearchQuery || form.destinationAccount}
                        onChange={(text) => {
                          setCustomSearchQuery(text)
                          setForm(f => ({ ...f, destinationAccount: text }))
                        }}
                        onSelect={(item) => {
                          const matchedBene = beneficiaries.find(b => b.nickname === item.term)
                          const targetAcc = matchedBene ? matchedBene.beneficiaryAccountNumber : (item.referenceId || item.term)
                          setForm(f => ({ ...f, destinationAccount: targetAcc }))
                          setCustomSearchQuery(item.displayText || item.term)
                        }}
                        fetchSuggestions={async (query) => {
                          if (!user?.customerId) return []
                          return autoApi.getBeneficiaries(user.customerId, query)
                        }}
                        placeholder="Search payee or type account (e.g. Bob or ACC-1002)..."
                      />
                      {form.destinationAccount && (
                        <div style={{ fontSize: '.78rem', color: 'var(--text-second)', display: 'flex', alignItems: 'center', gap: '.4rem' }}>
                          Target Account: <code style={{ color: 'var(--primary)' }}>{form.destinationAccount}</code>
                        </div>
                      )}
                    </div>
                  )}
                </div>

                {/* 3. Transfer Amount */}
                <div className="form-group">
                  <div className="flex justify-between items-center" style={{ marginBottom: '.35rem' }}>
                    <label htmlFor="tf-amount" style={{ margin: 0, fontWeight: 500 }}>Amount (USD)</label>
                    <div style={{ display: 'flex', gap: '.35rem' }}>
                      {[0.25, 0.50, 1.0].map(pct => (
                        <button
                          key={pct}
                          type="button"
                          className="btn btn-secondary btn-sm"
                          style={{ padding: '.15rem .45rem', fontSize: '.75rem', height: 'auto' }}
                          onClick={() => handleQuickAmount(pct)}
                          disabled={sourceBalance <= 0}
                        >
                          {pct === 1.0 ? 'Max' : `${pct * 100}%`}
                        </button>
                      ))}
                    </div>
                  </div>
                  <input
                    id="tf-amount"
                    type="number"
                    className="input"
                    placeholder="0.00"
                    min="0.01"
                    max={sourceBalance > 0 ? sourceBalance : undefined}
                    step="0.01"
                    value={form.amount}
                    onChange={e => setForm(f => ({ ...f, amount: e.target.value }))}
                    required
                  />
                </div>

                {/* 4. Description */}
                <div className="form-group">
                  <label htmlFor="tf-desc" style={{ marginBottom: '.35rem', fontWeight: 500 }}>
                    Description (optional)
                  </label>
                  <input
                    id="tf-desc"
                    type="text"
                    className="input"
                    placeholder="e.g. Rent, dinner, invoice #102"
                    value={form.description}
                    onChange={e => setForm(f => ({ ...f, description: e.target.value }))}
                  />
                </div>

                {/* Submit Button */}
                <button
                  id="btn-transfer-submit"
                  type="submit"
                  className="btn btn-primary btn-full btn-lg"
                  disabled={loading || myAccs.length === 0}
                  style={{ marginTop: '.5rem', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '.5rem' }}
                >
                  <span>Proceed with MPIN</span>
                </button>
              </form>
            )}
          </div>

          {/* Side Panel: Quick Select Payees & Available Accounts */}
          <div className="card fade-up">
            <h4 style={{ marginBottom: '.75rem' }}>Available Payees & Accounts</h4>
            <p style={{ fontSize: '.825rem', color: 'var(--text-second)', marginBottom: '1.25rem' }}>
              Click any account below to quickly set as transfer destination:
            </p>

            {destinationOptions.length === 0 ? (
              <div style={{ color: 'var(--text-muted)', fontSize: '.875rem' }}>
                No other accounts found.
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '.65rem' }}>
                {destinationOptions.map(acc => {
                  const isSelected = form.destinationAccount === acc.accountNumber
                  const matchingB = beneficiaries.find(b => b.beneficiaryAccountNumber === acc.accountNumber)
                  return (
                    <div
                      key={acc.accountNumber}
                      onClick={() => handleSelectDestination(acc.accountNumber)}
                      className="card"
                      style={{
                        padding: '.75rem 1rem',
                        cursor: 'pointer',
                        borderColor: isSelected ? 'var(--accent)' : 'var(--border)',
                        background: isSelected ? 'var(--accent-light)' : 'var(--bg-card)',
                        boxShadow: isSelected ? '0 0 0 1px var(--accent)' : 'var(--shadow-sm)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        gap: '.75rem',
                        transition: 'all 0.15s ease'
                      }}
                    >
                      <div>
                        <div style={{ fontWeight: 600, fontSize: '.9rem', display: 'flex', alignItems: 'center', gap: '.35rem' }}>
                          {acc.customerName}
                          {matchingB && (
                            <span className="badge badge-success" style={{ fontSize: '.65rem', padding: '1px 6px' }}>
                              {matchingB.nickname || 'Beneficiary'}
                            </span>
                          )}
                        </div>
                        <div style={{ fontFamily: 'monospace', fontSize: '.8rem', color: 'var(--text-second)' }}>
                          {acc.accountNumber}
                        </div>
                      </div>
                      <span className="btn btn-secondary btn-sm" style={{ padding: '.25rem .6rem', fontSize: '.75rem' }}>
                        {isSelected ? 'Selected' : 'Choose'}
                      </span>
                    </div>
                  )
                })}
              </div>
            )}
          </div>

        </div>
      </div>

      {/* Google Pay-style MPIN Authorization Modal */}
      <MpinModal
        isOpen={mpinModalOpen}
        onClose={() => setMpinModalOpen(false)}
        onSubmit={handleExecuteTransferWithMpin}
        title="Authorize Transfer"
        subtitle={`Authorizing payment from ${form.sourceAccount}`}
        amount={form.amount}
        recipient={recipientName}
        accountNumber={form.destinationAccount}
      />

      {/* Verified Transaction Receipt Modal with Persistent Base62 Short Link */}
      <ReceiptModal
        isOpen={receiptModalOpen}
        onClose={() => setReceiptModalOpen(false)}
        transaction={result}
        shortUrl={receiptShortUrl}
      />
    </>
  )
}
