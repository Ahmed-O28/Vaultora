import { useState } from 'react'
import { useAuth } from '../context/AuthContext'
import {
  enableDesktopAlerts,
  markAllRead,
  markRead,
  sendTestNotification,
  useNotificationStore,
} from '../services/notificationStore'
import { NOTIFICATION_ICONS } from '../components/NotificationCenter'

function fmtDate(iso) {
  return new Date(iso).toLocaleString('en-US', {
    month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit',
  })
}

const TYPE_LABEL = {
  TRANSFER_DEBIT: 'Money sent',
  TRANSFER_CREDIT: 'Money received',
  BILL_PAYMENT: 'Bill payment',
  SYSTEM: 'System',
}

export default function NotificationsPage() {
  const { user } = useAuth()
  const { items, unread, connected } = useNotificationStore()
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [desktop, setDesktop] = useState(
    typeof Notification === 'undefined' ? 'unsupported' : Notification.permission
  )

  async function run(fn) {
    setError(''); setBusy(true)
    try { await fn() } catch (err) { setError(err.message) } finally { setBusy(false) }
  }

  async function handleDesktop() {
    setDesktop(await enableDesktopAlerts())
  }

  return (
    <>
      <div className="page-header">
        <div>
          <h3 style={{ margin: 0 }}>Notifications</h3>
          <p style={{ margin: 0, fontSize: '.85rem' }}>
            Instant alerts for transfers and bill payments
          </p>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '.75rem', flexWrap: 'wrap' }}>
          <span id="push-status" className={`badge ${connected ? 'badge-success' : 'badge-warn'}`}>
            {connected ? '● Live' : '○ Reconnecting…'}
          </span>
          {desktop === 'default' && (
            <button className="btn btn-outline btn-sm" onClick={handleDesktop}>
              Enable desktop alerts
            </button>
          )}
          <button id="btn-test-push" className="btn btn-outline btn-sm" disabled={busy}
            onClick={() => run(() => sendTestNotification(user.customerId))}>
            Send test
          </button>
          <button id="btn-mark-all" className="btn btn-primary btn-sm" disabled={busy || unread === 0}
            onClick={() => run(() => markAllRead(user.customerId))}>
            Mark all as read{unread > 0 ? ` (${unread})` : ''}
          </button>
        </div>
      </div>

      <div className="page-body">
        {error && <div className="alert alert-danger" style={{ marginBottom: '1rem' }}>{error}</div>}

        {items.length === 0 ? (
          <div className="empty-state">
            <div className="empty-icon" style={{ fontSize: '1.5rem', opacity: 0.3 }}>—</div>
            <h4>No notifications yet</h4>
            <p>Make a transfer or pay a bill and you will be alerted here instantly.</p>
          </div>
        ) : (
          <div className="notif-list fade-up">
            {items.map((n) => (
              <div
                key={n.id}
                id={`notif-${n.id}`}
                className={`notif-item${n.seen ? '' : ' unread'}`}
                onClick={() => !n.seen && run(() => markRead(user.customerId, n.id))}
              >
                <div className={`notif-icon type-${n.type}`}>{NOTIFICATION_ICONS[n.type] || '•'}</div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div className="flex items-center justify-between gap-2">
                    <strong style={{ fontSize: '.95rem' }}>{n.title}</strong>
                    <small style={{ color: 'var(--text-muted)', whiteSpace: 'nowrap' }}>{fmtDate(n.createdAt)}</small>
                  </div>
                  <p style={{ margin: '.2rem 0 .4rem', fontSize: '.875rem' }}>{n.message}</p>
                  <span className="badge badge-neutral">{TYPE_LABEL[n.type] || n.type}</span>
                </div>
                {!n.seen && <span className="notif-dot" title="Unread" />}
              </div>
            ))}
          </div>
        )}
      </div>
    </>
  )
}
