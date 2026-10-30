import { useState, useEffect, useRef, useCallback } from 'react'

export default function MpinModal({
  isOpen,
  onClose,
  onSubmit,
  title = 'Verify MPIN',
  subtitle = '',
  amount = null,
  recipient = null,
  accountNumber = null,
}) {
  const [pin, setPin] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [shake, setShake] = useState(false)
  const hiddenInputRef = useRef(null)

  // Guard against duplicate execution and auto-submit loops
  const isSubmittingRef = useRef(false)
  const hasAutoSubmittedRef = useRef(false)
  const onSubmitRef = useRef(onSubmit)
  onSubmitRef.current = onSubmit

  // Reset state whenever modal opens or closes
  useEffect(() => {
    if (isOpen) {
      setPin('')
      setError('')
      setLoading(false)
      setShake(false)
      isSubmittingRef.current = false
      hasAutoSubmittedRef.current = false
      // Focus hidden input for physical keyboard entry
      setTimeout(() => hiddenInputRef.current?.focus(), 80)
    } else {
      isSubmittingRef.current = false
      hasAutoSubmittedRef.current = false
    }
  }, [isOpen])

  // Handle number click on keypad
  const handleDigit = useCallback((digit) => {
    if (isSubmittingRef.current || loading) return
    setError('')
    setPin(prev => (prev.length < 4 ? prev + digit : prev))
  }, [loading])

  const handleBackspace = useCallback(() => {
    if (isSubmittingRef.current || loading) return
    setError('')
    setPin(prev => prev.slice(0, -1))
  }, [loading])

  const handleClear = useCallback(() => {
    if (isSubmittingRef.current || loading) return
    setError('')
    setPin('')
  }, [loading])

  // Handle Enter/Submit
  const handleVerify = useCallback(async (currentPin) => {
    const pinToSubmit = currentPin || pin
    if (pinToSubmit.length !== 4) {
      setError('Please enter a 4-digit MPIN.')
      setShake(true)
      setTimeout(() => setShake(false), 500)
      return
    }

    // Atomic guard: if already submitting, ignore duplicate call
    if (isSubmittingRef.current) {
      return
    }
    isSubmittingRef.current = true
    setLoading(true)
    setError('')

    try {
      if (onSubmitRef.current) {
        await onSubmitRef.current(pinToSubmit)
      }
      // Successfully submitted
    } catch (err) {
      setError(err.message || 'Incorrect MPIN. Please try again.')
      setShake(true)
      setPin('')
      isSubmittingRef.current = false
      hasAutoSubmittedRef.current = false
      setTimeout(() => {
        setShake(false)
        hiddenInputRef.current?.focus()
      }, 500)
    } finally {
      setLoading(false)
    }
  }, [pin])

  // Auto-submit exactly once when 4th digit is entered
  useEffect(() => {
    if (pin.length === 4 && !hasAutoSubmittedRef.current && !isSubmittingRef.current) {
      hasAutoSubmittedRef.current = true
      handleVerify(pin)
    }
  }, [pin, handleVerify])

  // Keyboard navigation — stop propagation to prevent parent handlers from firing
  useEffect(() => {
    if (!isOpen) return

    function handleKeyDown(e) {
      if (e.key === 'Escape') {
        e.preventDefault()
        e.stopPropagation()
        if (!isSubmittingRef.current && !loading) onClose()
      } else if (e.key >= '0' && e.key <= '9') {
        e.preventDefault()
        e.stopPropagation()
        if (!isSubmittingRef.current && !loading) {
          handleDigit(e.key)
        }
      } else if (e.key === 'Backspace') {
        e.preventDefault()
        e.stopPropagation()
        if (!isSubmittingRef.current && !loading) {
          handleBackspace()
        }
      } else if (e.key === 'Enter') {
        e.preventDefault()
        e.stopPropagation()
        if (pin.length === 4 && !isSubmittingRef.current && !loading) {
          handleVerify(pin)
        }
      } else if (e.key === 'Delete' || e.key === 'ArrowLeft' || e.key === 'ArrowRight') {
        e.stopPropagation()
      } else if (e.key !== 'Tab') {
        e.stopPropagation()
      }
    }

    window.addEventListener('keydown', handleKeyDown, true)
    return () => window.removeEventListener('keydown', handleKeyDown, true)
  }, [isOpen, pin, handleDigit, handleBackspace, handleVerify, onClose, loading])

  if (!isOpen) return null

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(15, 15, 15, 0.72)',
        backdropFilter: 'blur(5px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 9999,
        padding: '1rem',
        animation: 'fadeIn 0.2s ease-out'
      }}
      onClick={(e) => {
        e.stopPropagation()
        if (e.target === e.currentTarget && !loading) onClose()
      }}
      onKeyDown={(e) => e.stopPropagation()}
    >
      {/* Hidden real input for mobile keyboard / accessibility */}
      <input
        ref={hiddenInputRef}
        type="password"
        inputMode="numeric"
        pattern="[0-9]*"
        maxLength={4}
        value={pin}
        disabled={loading}
        onChange={(e) => {
          if (isSubmittingRef.current || loading) return
          const val = e.target.value.replace(/\D/g, '').slice(0, 4)
          setPin(val)
        }}
        style={{
          position: 'absolute',
          opacity: 0,
          pointerEvents: 'none',
          top: -9999,
          left: -9999
        }}
        autoFocus
      />

      <div
        className={`card ${shake ? 'shake-anim' : ''}`}
        style={{
          width: '100%',
          maxWidth: '380px',
          background: '#ffffff',
          borderRadius: '24px',
          padding: '2rem 1.75rem',
          boxShadow: '0 20px 60px rgba(0, 0, 0, 0.3)',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          position: 'relative',
          border: '1px solid var(--border)'
        }}
        onClick={() => hiddenInputRef.current?.focus()}
      >
        {/* Close button */}
        {!loading && (
          <button
            type="button"
            onClick={onClose}
            style={{
              position: 'absolute',
              top: '1rem',
              right: '1rem',
              background: 'transparent',
              border: 'none',
              fontSize: '1.25rem',
              color: 'var(--text-muted)',
              cursor: 'pointer',
              lineHeight: 1,
              padding: '.25rem',
              borderRadius: '50%'
            }}
          >
            ✕
          </button>
        )}

        {/* Brand / Security Header */}
        <div
          style={{
            width: '52px',
            height: '52px',
            borderRadius: '50%',
            background: 'var(--accent-light)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: '1.25rem',
            color: 'var(--accent-dark)',
            marginBottom: '1rem',
            boxShadow: '0 4px 12px rgba(233, 154, 101, 0.25)',
            fontWeight: 700
          }}
        >
          ●
        </div>

        <h3 style={{ margin: 0, fontSize: '1.2rem', fontWeight: 700, textAlign: 'center' }}>
          {title}
        </h3>

        {/* Payment details summary if provided */}
        {(amount || recipient || accountNumber) && (
          <div
            style={{
              background: 'var(--bg-warm)',
              border: '1px solid var(--border)',
              borderRadius: '12px',
              padding: '.75rem 1rem',
              width: '100%',
              margin: '1rem 0 .5rem',
              textAlign: 'center'
            }}
          >
            {amount && (
              <div style={{ fontSize: '1.4rem', fontWeight: 800, color: 'var(--text-primary)', marginBottom: '.2rem' }}>
                ${Number(amount).toFixed(2)}
              </div>
            )}
            {recipient && (
              <div style={{ fontSize: '.85rem', color: 'var(--text-second)' }}>
                Transfer to: <strong style={{ color: 'var(--text-primary)' }}>{recipient}</strong>
              </div>
            )}
            {accountNumber && (
              <div style={{ fontSize: '.8rem', fontFamily: 'monospace', color: 'var(--text-muted)' }}>
                Account: {accountNumber}
              </div>
            )}
          </div>
        )}

        <p style={{ fontSize: '.825rem', color: 'var(--text-second)', marginTop: '.5rem', marginBottom: '1.25rem', textAlign: 'center' }}>
          {subtitle || 'Enter your 4-digit Vaultora MPIN'}
        </p>

        {/* 4-Digit Indicator Circles */}
        <div
          style={{
            display: 'flex',
            gap: '1.25rem',
            marginBottom: '1.25rem',
            alignItems: 'center',
            justifyContent: 'center'
          }}
        >
          {[0, 1, 2, 3].map((idx) => {
            const isFilled = pin.length > idx
            return (
              <div
                key={idx}
                style={{
                  width: '18px',
                  height: '18px',
                  borderRadius: '50%',
                  border: isFilled ? '2px solid var(--accent)' : '2px solid #D1D5DB',
                  background: isFilled ? 'var(--accent)' : '#F9FAFB',
                  transform: isFilled ? 'scale(1.15)' : 'scale(1)',
                  transition: 'all 0.15s cubic-bezier(0.4, 0, 0.2, 1)',
                  boxShadow: isFilled ? '0 0 10px rgba(233, 154, 101, 0.5)' : 'none'
                }}
              />
            )
          })}
        </div>

        {error && (
          <div
            style={{
              color: 'var(--danger)',
              fontSize: '.825rem',
              fontWeight: 500,
              marginBottom: '.75rem',
              textAlign: 'center',
              animation: 'fadeIn 0.2s'
            }}
          >
            {error}
          </div>
        )}

        {/* Hint banner */}
        <div
          style={{
            fontSize: '.75rem',
            color: 'var(--text-muted)',
            background: '#F3F4F6',
            borderRadius: '8px',
            padding: '.35rem .75rem',
            marginBottom: '1rem'
          }}
        >
          Default demo MPIN is <strong>1234</strong>
        </div>

        {/* Google Pay-style Numeric Keypad */}
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(3, 1fr)',
            gap: '.65rem',
            width: '100%',
            maxWidth: '280px'
          }}
        >
          {['1', '2', '3', '4', '5', '6', '7', '8', '9', 'C', '0', '⌫'].map((k) => {
            const isSpecial = k === 'C' || k === '⌫'
            return (
              <button
                key={k}
                type="button"
                disabled={loading}
                onClick={(e) => {
                  e.stopPropagation()
                  if (k === 'C') handleClear()
                  else if (k === '⌫') handleBackspace()
                  else handleDigit(k)
                }}
                style={{
                  height: '52px',
                  borderRadius: '16px',
                  border: '1px solid var(--border)',
                  background: isSpecial ? 'var(--bg-warm)' : '#ffffff',
                  fontSize: isSpecial ? '1rem' : '1.25rem',
                  fontWeight: isSpecial ? 600 : 700,
                  color: isSpecial ? 'var(--text-second)' : 'var(--text-primary)',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  transition: 'all 0.1s ease',
                  boxShadow: 'var(--shadow-sm)',
                  userSelect: 'none'
                }}
                onMouseDown={(e) => {
                  e.currentTarget.style.transform = 'scale(0.92)'
                  e.currentTarget.style.background = 'var(--accent-light)'
                }}
                onMouseUp={(e) => {
                  e.currentTarget.style.transform = 'scale(1)'
                  e.currentTarget.style.background = isSpecial ? 'var(--bg-warm)' : '#ffffff'
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.transform = 'scale(1)'
                  e.currentTarget.style.background = isSpecial ? 'var(--bg-warm)' : '#ffffff'
                }}
              >
                {k}
              </button>
            )
          })}
        </div>

        {/* Loading overlay if verifying */}
        {loading && (
          <div
            style={{
              position: 'absolute',
              inset: 0,
              background: 'rgba(255, 255, 255, 0.85)',
              borderRadius: '24px',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '.75rem',
              zIndex: 10
            }}
          >
            <div className="spinner spinner-lg" />
            <span style={{ fontSize: '.875rem', fontWeight: 600, color: 'var(--text-second)' }}>
              Verifying MPIN with Vaultora…
            </span>
          </div>
        )}
      </div>

      <style>{`
        @keyframes fadeIn {
          from { opacity: 0; }
          to { opacity: 1; }
        }
        @keyframes shake {
          0%, 100% { transform: translateX(0); }
          20%, 60% { transform: translateX(-8px); }
          40%, 80% { transform: translateX(8px); }
        }
        .shake-anim {
          animation: shake 0.4s ease-in-out;
        }
      `}</style>
    </div>
  )
}
