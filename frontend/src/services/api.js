// API base URL — proxied through Vite dev server to http://localhost:8081
const BASE = '/api'

function getToken(path) {
  if (path && path.startsWith('/admin')) {
    return localStorage.getItem('adminToken')
  }
  return localStorage.getItem('token') || localStorage.getItem('adminToken')
}

async function request(path, options = {}) {
  const token = getToken(path)
  const headers = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...options.headers,
  }
  let res
  try {
    res = await fetch(`${BASE}${path}`, { ...options, headers })
  } catch (err) {
    throw new Error('Unable to connect to the banking server. Please ensure the backend is running on port 8081.')
  }

  if (!res.ok) {
    let msg = `HTTP ${res.status}`
    let errorBody = null
    try {
      errorBody = await res.json()
      msg = errorBody.message || errorBody.error || msg
    } catch (_) {}

    // Only clear auth and redirect for genuine token/session expiry on protected endpoints.
    // Do NOT log out for: login/register attempts, MPIN failures, rate limiting, or business errors.
    if (res.status === 401 && !path.includes('/login') && !path.includes('/register')) {
      const code = errorBody?.errorCode
      let isTokenIssue = false

      if (code) {
        // Structured error codes take precedence
        isTokenIssue = (code === 'AUTH_REQUIRED' || code === 'TOKEN_EXPIRED' || code === 'INVALID_TOKEN')
      } else {
        // Fallback to phrase matching only if structured code is absent
        isTokenIssue = msg.toLowerCase().includes('token') ||
                       msg.toLowerCase().includes('authentication required') ||
                       msg.toLowerCase().includes('authorization header')
      }

      // Explicitly protect against logout for MPIN or credential verification failures
      if (code === 'INVALID_MPIN' || code === 'INVALID_CREDENTIALS') {
        isTokenIssue = false
      }

      if (isTokenIssue) {
        if (path.startsWith('/admin')) {
          localStorage.removeItem('adminToken')
          localStorage.removeItem('adminUser')
          if (typeof window !== 'undefined' && !window.location.pathname.includes('/admin/login')) {
            window.location.href = '/admin/login'
          }
        } else {
          localStorage.removeItem('token')
          localStorage.removeItem('user')
          if (typeof window !== 'undefined' && !window.location.pathname.includes('/login')) {
            window.location.href = '/login'
          }
        }
      }
    }

    // For 429 Too Many Requests, provide a user-friendly message
    if (res.status === 429) {
      const retryAfter = res.headers.get('Retry-After') || '15'
      msg = `Too many requests. Please try again in ${retryAfter} seconds.`
    }

    throw new Error(msg)
  }
  // 204 No Content
  if (res.status === 204) return null
  return res.json()
}

// ── Auth ──────────────────────────────────────────────────────────────────────
export const auth = {
  register: (data) => request('/customers/register', { method: 'POST', body: JSON.stringify(data) }),
  login: (data) => request('/customers/login', { method: 'POST', body: JSON.stringify(data) }),
}

// ── Accounts ──────────────────────────────────────────────────────────────────
export const accounts = {
  getAll: () => request('/accounts'),
  getByNumber: (num) => request(`/accounts/${num}`),
  getTransactions: (num) => request(`/accounts/${num}/transactions`),
  getForCustomer: (customerId) => request(`/customers/${customerId}/accounts`),
}

// ── Transfers ─────────────────────────────────────────────────────────────────
export const transfers = {
  send: (data) => request('/transfers', { method: 'POST', body: JSON.stringify(data) }),
}

// ── Bill Payments ─────────────────────────────────────────────────────────────
export const bills = {
  pay: (data) => request('/bills/pay', { method: 'POST', body: JSON.stringify(data) }),
  getForAccount: (num) => request(`/bills/account/${num}`),
}

// ── Beneficiaries ─────────────────────────────────────────────────────────────
export const beneficiaries = {
  getAll: (customerId) => request(`/customers/${customerId}/beneficiaries`),
  add: (customerId, data) => request(`/customers/${customerId}/beneficiaries`, { method: 'POST', body: JSON.stringify(data) }),
}

// ── Customers & MPIN ──────────────────────────────────────────────────────────
export const customers = {
  verifyMpin: (customerId, data) => request(`/customers/${customerId}/verify-mpin`, { method: 'POST', body: JSON.stringify(data) }),
  setMpin: (customerId, data) => request(`/customers/${customerId}/set-mpin`, { method: 'POST', body: JSON.stringify(data) }),
}

// ── Health ────────────────────────────────────────────────────────────────────
export const health = {
  check: () => request('/health'),
}

// ── Admin ─────────────────────────────────────────────────────────────────────
export const adminApi = {
  login: (data) => request('/admin/login', { method: 'POST', body: JSON.stringify(data) }),
  getStats: () => request('/admin/stats'),
  getCustomers: (params = {}) => {
    const q = new URLSearchParams()
    if (params.search) q.append('search', params.search)
    if (params.shardId) q.append('shardId', params.shardId)
    const queryStr = q.toString() ? `?${q.toString()}` : ''
    return request(`/admin/customers${queryStr}`)
  },
  getTransactions: (params = {}) => {
    const q = new URLSearchParams()
    if (params.search) q.append('search', params.search)
    if (params.type) q.append('type', params.type)
    if (params.status) q.append('status', params.status)
    if (params.shardId) q.append('shardId', params.shardId)
    const queryStr = q.toString() ? `?${q.toString()}` : ''
    return request(`/admin/transactions${queryStr}`)
  },
  getBillers: () => request('/admin/billers'),
  updateBillerStatus: (billerId, status) => request(`/admin/billers/${billerId}/status`, {
    method: 'POST',
    body: JSON.stringify({ status })
  }),
  toggleBillerStatus: (billerId) => request(`/admin/billers/${billerId}/toggle`, {
    method: 'POST'
  }),
  getCrawlerStatus: () => Promise.resolve({ status: 'IDLE' }),
  getCrawlerChanges: () => Promise.resolve([]),
  triggerCrawl: () => Promise.resolve({ status: 'SUCCESS' }),
  clearCrawlerChanges: () => Promise.resolve([]),
}

// ── Push notifications ────────────────────────────────────────────────────────
export const notificationsApi = {
  list: (customerId) => request(`/customers/${customerId}/notifications`),
  unreadCount: (customerId) => request(`/customers/${customerId}/notifications/unread-count`),
  markRead: (customerId, id) => request(`/customers/${customerId}/notifications/${id}/read`, { method: 'POST' }),
  markAllRead: (customerId) => request(`/customers/${customerId}/notifications/read-all`, { method: 'POST' }),
  sendTest: (customerId) => request(`/customers/${customerId}/notifications/test`, { method: 'POST' }),
}

// ── Admin-Controlled Biller Directory & Management ───────────────────────────
export const billerApi = {
  getBillers: () => request('/billers'),
  getActiveBillers: () => request('/billers/active'),
  getBiller: (id) => request(`/billers/${id}`),
  adminListBillers: () => request('/admin/billers'),
  updateStatus: (billerId, status) => request(`/admin/billers/${billerId}/status`, {
    method: 'POST',
    body: JSON.stringify({ status })
  }),
  toggleStatus: (billerId) => request(`/admin/billers/${billerId}/toggle`, {
    method: 'POST'
  }),
}

// Safe compatibility shim for any residual calls
export const crawlerApi = {
  getBillers: () => request('/billers'),
  getRates: () => Promise.resolve({ rates: [] }),
  refresh: () => Promise.resolve({ status: 'SUCCESS' }),
}

// ── Unique ID generator ───────────────────────────────────────────────────────
export const idsApi = {
  generate: (prefix = 'TXN', count = 1) => request(`/system/ids/generate?prefix=${encodeURIComponent(prefix)}&count=${count}`),
  parse: (id) => request(`/system/ids/parse?id=${encodeURIComponent(id)}`),
}

// ── URL Shortener & Receipts ──────────────────────────────────────────────────
export const shortener = {
  shorten: (data) => request('/shorten', { method: 'POST', body: JSON.stringify(data) }),
  getMetadata: (shortCode) => request(`/shorten/${shortCode}`),
}

export const receipts = {
  get: (transactionId) => request(`/receipts/${transactionId}`),
}

// ── Trie Autocomplete ─────────────────────────────────────────────────────────
export const autocomplete = {
  getBeneficiaries: (customerId, query, limit = 5) => {
    const q = encodeURIComponent(query || '')
    return request(`/customers/${customerId}/autocomplete/beneficiaries?query=${q}&limit=${limit}`)
  },
  getBillers: (query, limit = 5) => {
    const q = encodeURIComponent(query || '')
    return request(`/autocomplete/billers?query=${q}&limit=${limit}`)
  },
  getCombined: (customerId, query, limit = 6) => {
    const q = encodeURIComponent(query || '')
    return request(`/customers/${customerId}/autocomplete?query=${q}&limit=${limit}`)
  },
}
