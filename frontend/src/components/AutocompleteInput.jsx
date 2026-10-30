import { useState, useEffect, useRef, useCallback } from 'react'

/**
 * Enterprise-grade, accessible Autocomplete Input component.
 * Features:
 * - Debounced asynchronous suggestion fetching
 * - Stale network response discard
 * - Full keyboard navigation (ArrowUp, ArrowDown, Enter, Escape)
 * - Safe event propagation blocking (won't submit parent forms or close parent modals)
 * - WAI-ARIA combobox/listbox accessibility semantics
 * - Clean dropdown positioning with loading, error, and empty states
 */
export default function AutocompleteInput({
  id = 'autocomplete-input',
  value = '',
  onChange,
  onSelect,
  fetchSuggestions,
  placeholder = 'Type to search…',
  disabled = false,
  className = '',
  style = {}
}) {
  const [isOpen, setIsOpen] = useState(false)
  const [suggestions, setSuggestions] = useState([])
  const [loading, setLoading] = useState(false)
  const [selectedIndex, setSelectedIndex] = useState(-1)
  const [error, setError] = useState('')

  const containerRef = useRef(null)
  const inputRef = useRef(null)
  const reqVersionRef = useRef(0)

  // Debounced search logic
  const performSearch = useCallback(async (query) => {
    if (!fetchSuggestions || !query || query.trim().length === 0) {
      setSuggestions([])
      setLoading(false)
      setIsOpen(false)
      return
    }

    const currentVersion = ++reqVersionRef.current
    setLoading(true)
    setError('')

    try {
      const items = await fetchSuggestions(query.trim())
      // Check if this response is the newest requested version
      if (currentVersion === reqVersionRef.current) {
        setSuggestions(items || [])
        setIsOpen(true)
        setSelectedIndex(-1)
      }
    } catch (err) {
      if (currentVersion === reqVersionRef.current) {
        setError('Failed to fetch suggestions')
        setSuggestions([])
      }
    } finally {
      if (currentVersion === reqVersionRef.current) {
        setLoading(false)
      }
    }
  }, [fetchSuggestions])

  // Trigger search on value changes (debounced by 250ms)
  useEffect(() => {
    const timer = setTimeout(() => {
      if (value && value.trim().length > 0) {
        performSearch(value)
      } else {
        setSuggestions([])
        setIsOpen(false)
      }
    }, 250)

    return () => clearTimeout(timer)
  }, [value, performSearch])

  // Close dropdown on click outside
  useEffect(() => {
    function handleClickOutside(e) {
      if (containerRef.current && !containerRef.current.contains(e.target)) {
        setIsOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  // Keyboard navigation
  function handleKeyDown(e) {
    if (!isOpen || suggestions.length === 0) {
      if (e.key === 'ArrowDown' && suggestions.length > 0) {
        setIsOpen(true)
        e.preventDefault()
      }
      return
    }

    if (e.key === 'ArrowDown') {
      e.preventDefault()
      e.stopPropagation()
      setSelectedIndex(prev => (prev < suggestions.length - 1 ? prev + 1 : 0))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      e.stopPropagation()
      setSelectedIndex(prev => (prev > 0 ? prev - 1 : suggestions.length - 1))
    } else if (e.key === 'Enter') {
      if (selectedIndex >= 0 && selectedIndex < suggestions.length) {
        e.preventDefault()
        e.stopPropagation()
        handleSelectSuggestion(suggestions[selectedIndex])
      }
    } else if (e.key === 'Escape') {
      e.preventDefault()
      e.stopPropagation()
      setIsOpen(false)
      setSelectedIndex(-1)
    }
  }

  function handleSelectSuggestion(item) {
    setIsOpen(false)
    setSelectedIndex(-1)
    if (onSelect) {
      onSelect(item)
    } else if (onChange) {
      onChange(item.term || item.displayText || '')
    }
  }

  const activeOptionId = selectedIndex >= 0 ? `${id}-opt-${selectedIndex}` : undefined

  return (
    <div
      ref={containerRef}
      className="autocomplete-container"
      style={{ position: 'relative', width: '100%', ...style }}
    >
      <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
        <input
          ref={inputRef}
          id={id}
          type="text"
          className={`input ${className}`}
          value={value}
          onChange={e => onChange?.(e.target.value)}
          onFocus={() => {
            if (suggestions.length > 0) setIsOpen(true)
          }}
          onKeyDown={handleKeyDown}
          placeholder={placeholder}
          disabled={disabled}
          role="combobox"
          aria-autocomplete="list"
          aria-expanded={isOpen}
          aria-controls={`${id}-popup`}
          aria-activedescendant={activeOptionId}
          autoComplete="off"
          style={{ width: '100%', paddingRight: loading ? '2.5rem' : '1rem' }}
        />

        {loading && (
          <div
            style={{
              position: 'absolute',
              right: '.75rem',
              display: 'flex',
              alignItems: 'center',
              pointerEvents: 'none'
            }}
          >
            <span className="spinner spinner-sm" style={{ width: '1rem', height: '1rem' }} />
          </div>
        )}
      </div>

      {isOpen && (
        <ul
          id={`${id}-popup`}
          role="listbox"
          className="autocomplete-dropdown card shadow-lg"
          style={{
            position: 'absolute',
            top: 'calc(100% + 4px)',
            left: 0,
            right: 0,
            zIndex: 1000,
            maxHeight: '260px',
            overflowY: 'auto',
            background: 'var(--bg-card, #FFFFFF)',
            border: '1px solid var(--border, #E8E5E0)',
            borderRadius: 'var(--radius, 12px)',
            padding: '.35rem 0',
            margin: 0,
            listStyle: 'none',
            boxShadow: 'var(--shadow-lg, 0 12px 35px rgba(0, 0, 0, 0.12))'
          }}
        >
          {suggestions.length === 0 ? (
            <li
              style={{
                padding: '.85rem 1rem',
                fontSize: '.85rem',
                color: 'var(--text-muted, #999999)',
                textAlign: 'center'
              }}
            >
              No matching suggestions found
            </li>
          ) : (
            suggestions.map((item, idx) => {
              const isSelected = idx === selectedIndex
              return (
                <li
                  key={idx}
                  id={`${id}-opt-${idx}`}
                  role="option"
                  aria-selected={isSelected}
                  onClick={() => handleSelectSuggestion(item)}
                  onMouseEnter={() => setSelectedIndex(idx)}
                  style={{
                    padding: '.65rem 1rem',
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    gap: '.75rem',
                    background: isSelected ? 'var(--accent-light, #FDF3EA)' : 'transparent',
                    borderLeft: isSelected ? '3px solid var(--accent, #E99A65)' : '3px solid transparent',
                    transition: 'all 0.15s ease'
                  }}
                >
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '2px', overflow: 'hidden' }}>
                    <span
                      style={{
                        fontSize: '.88rem',
                        fontWeight: 600,
                        color: isSelected ? 'var(--accent-dark, #C97B44)' : 'var(--text-primary, #171717)',
                        whiteSpace: 'nowrap',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis'
                      }}
                    >
                      {item.term || item.displayText}
                    </span>
                    {item.displayText && item.displayText !== item.term && (
                      <span
                        style={{
                          fontSize: '.75rem',
                          color: 'var(--text-muted, #999999)',
                          whiteSpace: 'nowrap',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis'
                        }}
                      >
                        {item.displayText}
                      </span>
                    )}
                  </div>

                  <div style={{ display: 'flex', alignItems: 'center', gap: '.4rem', flexShrink: 0 }}>
                    {item.referenceId && (
                      <span
                        style={{
                          fontSize: '.72rem',
                          fontFamily: 'monospace',
                          background: 'var(--bg-warm, #FAF7F2)',
                          border: '1px solid var(--border)',
                          padding: '2px 6px',
                          borderRadius: '4px',
                          color: 'var(--text-second, #666666)'
                        }}
                      >
                        {item.referenceId}
                      </span>
                    )}
                    <span
                      className={`badge ${item.category === 'BILLER' ? 'badge-neutral' : 'badge-warn'}`}
                      style={{ fontSize: '.7rem', padding: '2px 8px' }}
                    >
                      {item.category === 'BILLER' ? 'Biller' : 'Payee'}
                    </span>
                  </div>
                </li>
              )
            })
          )}
        </ul>
      )}
    </div>
  )
}
