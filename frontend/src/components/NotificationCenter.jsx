import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import {
  startNotifications,
  stopNotifications,
  dismissToast,
  useNotificationStore,
} from '../services/notificationStore'

export const NOTIFICATION_ICONS = {
  TRANSFER_DEBIT: '↗',
  TRANSFER_CREDIT: '↙',
  BILL_PAYMENT: '◎',
  SYSTEM: '✦',
}

/** Starts the push connection for the signed-in customer and renders live toast pop-ups. */
export default function NotificationCenter() {
  const { user } = useAuth()
  const { toasts } = useNotificationStore()
  const navigate = useNavigate()
  const customerId = user?.customerId

  useEffect(() => {
    startNotifications(customerId)
    return () => stopNotifications()
  }, [customerId])

  if (toasts.length === 0) return null
  return (
    <div className="toast-stack" aria-live="polite">
      {toasts.map((t) => (
        <div
          key={t.toastId}
          className="toast-card"
          role="status"
          onClick={() => { dismissToast(t.toastId); navigate('/notifications') }}
        >
          <div className="toast-icon">{NOTIFICATION_ICONS[t.type] || '•'}</div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div className="toast-title">{t.title}</div>
            <div className="toast-msg">{t.message}</div>
          </div>
          <button
            className="toast-close"
            aria-label="Dismiss"
            onClick={(e) => { e.stopPropagation(); dismissToast(t.toastId) }}
          >×</button>
        </div>
      ))}
    </div>
  )
}

/** Unread counter shown next to the Notifications link in the sidebar. */
export function NavBadge() {
  const { unread } = useNotificationStore()
  if (!unread) return null
  return <span className="nav-badge" id="nav-notifications-badge">{unread > 99 ? '99+' : unread}</span>
}
