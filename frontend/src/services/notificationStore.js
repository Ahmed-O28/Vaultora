import { useSyncExternalStore } from 'react'
import { notificationsApi } from './api'

/**
 * Client side of the push-notification feature.
 *
 * Keeps one authenticated Server-Sent-Events connection open to
 *   GET /api/customers/{id}/notifications/stream
 * and mirrors every pushed notification into a small external store that the
 * sidebar badge, the toast stack and the Notifications page all read from.
 * (EventSource cannot send an Authorization header, so the stream is read with fetch.)
 */

let state = { items: [], unread: 0, connected: false, toasts: [] }
const listeners = new Set()

function setState(patch) {
  state = { ...state, ...patch }
  listeners.forEach((l) => l())
}
function subscribe(l) {
  listeners.add(l)
  return () => listeners.delete(l)
}
function getSnapshot() {
  return state
}
export function useNotificationStore() {
  return useSyncExternalStore(subscribe, getSnapshot)
}

let controller = null
let activeCustomerId = null

export function startNotifications(customerId) {
  if (!customerId) return
  if (controller && activeCustomerId === customerId) return
  stopNotifications()
  activeCustomerId = customerId
  controller = new AbortController()
  loadInitial(customerId)
  streamLoop(customerId, controller.signal)
}

export function stopNotifications() {
  if (controller) controller.abort()
  controller = null
  activeCustomerId = null
  state = { items: [], unread: 0, connected: false, toasts: [] }
  listeners.forEach((l) => l())
}

async function loadInitial(customerId) {
  try {
    const [items, count] = await Promise.all([
      notificationsApi.list(customerId),
      notificationsApi.unreadCount(customerId),
    ])
    if (activeCustomerId !== customerId) return
    setState({ items: items || [], unread: count?.count ?? 0 })
  } catch (_) {
    /* the badge simply stays empty until the next push */
  }
}

function sleep(ms, signal) {
  return new Promise((resolve) => {
    const t = setTimeout(resolve, ms)
    signal.addEventListener('abort', () => { clearTimeout(t); resolve() }, { once: true })
  })
}

async function streamLoop(customerId, signal) {
  let delay = 1000
  while (!signal.aborted) {
    try {
      const token = localStorage.getItem('token')
      const res = await fetch(`/api/customers/${customerId}/notifications/stream`, {
        headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
        signal,
      })
      if (res.status === 401 || res.status === 403) return // session expired: stop quietly
      if (!res.ok || !res.body) throw new Error(`stream HTTP ${res.status}`)

      setState({ connected: true })
      delay = 1000
      const reader = res.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      while (true) {
        const { value, done } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
        let idx
        while ((idx = buffer.indexOf('\n\n')) >= 0) {
          handleEvent(buffer.slice(0, idx), customerId)
          buffer = buffer.slice(idx + 2)
        }
      }
    } catch (_) {
      if (signal.aborted) return
    }
    if (signal.aborted) return
    setState({ connected: false })
    await sleep(delay, signal)
    delay = Math.min(delay * 2, 15000)
  }
}

let reconnects = 0
function handleEvent(raw, customerId) {
  let name = 'message'
  const data = []
  for (const line of raw.split('\n')) {
    if (line.startsWith(':')) continue // heartbeat comment
    if (line.startsWith('event:')) name = line.slice(6).trim()
    else if (line.startsWith('data:')) data.push(line.slice(5).trim())
  }
  if (name === 'connected') {
    // after a reconnect, re-sync in case something was pushed while we were offline
    if (reconnects++ > 0) loadInitial(customerId)
    return
  }
  if (name !== 'notification' || data.length === 0) return
  try {
    addNotification(JSON.parse(data.join('\n')))
  } catch (_) { /* ignore malformed event */ }
}

function addNotification(n) {
  if (state.items.some((i) => i.id === n.id)) return

  // Debounce duplicate notifications arriving within 5 seconds
  const isDuplicate = state.items.some((i) => {
    if (i.type === n.type && i.title === n.title) {
      const msg1 = (i.message || '').split('. Ref:')[0].trim()
      const msg2 = (n.message || '').split('. Ref:')[0].trim()
      if (msg1 === msg2) {
        const timeDiff = Math.abs(new Date(n.createdAt || Date.now()) - new Date(i.createdAt || Date.now()))
        if (isNaN(timeDiff) || timeDiff < 5000) return true
      }
    }
    return false
  })
  if (isDuplicate) return

  const toast = { ...n, toastId: `${n.id}-${Date.now()}` }
  setState({
    items: [n, ...state.items].slice(0, 50),
    unread: state.unread + (n.seen ? 0 : 1),
    toasts: [...state.toasts, toast].slice(-4),
  })
  setTimeout(() => dismissToast(toast.toastId), 6500)
  showDesktopNotification(n)
}

export function dismissToast(toastId) {
  setState({ toasts: state.toasts.filter((t) => t.toastId !== toastId) })
}

function showDesktopNotification(n) {
  try {
    if (typeof Notification !== 'undefined' && Notification.permission === 'granted' && document.hidden) {
      new Notification(n.title, { body: n.message, tag: `vaultora-${n.id}` })
    }
  } catch (_) { /* desktop alerts are optional */ }
}

export async function enableDesktopAlerts() {
  if (typeof Notification === 'undefined') return 'unsupported'
  return Notification.requestPermission()
}

export async function markRead(customerId, id) {
  await notificationsApi.markRead(customerId, id)
  const target = state.items.find((i) => i.id === id)
  if (target && !target.seen) {
    setState({
      items: state.items.map((i) => (i.id === id ? { ...i, seen: true } : i)),
      unread: Math.max(0, state.unread - 1),
    })
  }
}

export async function markAllRead(customerId) {
  await notificationsApi.markAllRead(customerId)
  setState({ items: state.items.map((i) => ({ ...i, seen: true })), unread: 0 })
}

export async function sendTestNotification(customerId) {
  // The server pushes it back through the stream; if the stream is down, fall back to adding it locally.
  const n = await notificationsApi.sendTest(customerId)
  if (!state.connected && n) addNotification(n)
}
