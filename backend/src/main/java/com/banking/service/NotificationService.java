package com.banking.service;

import com.banking.model.Notification;
import com.banking.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Push notification service.
 *
 * Every notification is (1) persisted, so it can be listed / marked as read later, and (2) pushed in real time
 * to every open browser session of that customer through Server-Sent Events (SSE).
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    // ── creating + pushing ──────────────────────────────────────────────────

    /** Persists a notification and pushes it to the customer's live sessions. Deduplicates by referenceId per customer and debounces rapid duplicates. */
    public Notification notifyCustomer(Long customerId, String type, String title, String message, String referenceId) {
        if (referenceId != null && !referenceId.isBlank()) {
            Optional<Notification> existing = notificationRepository
                    .findTopByCustomerIdAndReferenceIdOrderByCreatedAtDesc(customerId, referenceId);
            if (existing.isPresent()) {
                log.info("Duplicate notification prevented for customer {} with transaction ref {}", customerId, referenceId);
                return existing.get();
            }
        }

        // Debounce identical notifications arriving within 5 seconds (e.g. from twin rapid submissions)
        List<Notification> recent = notificationRepository.findTop50ByCustomerIdOrderByCreatedAtDescIdDesc(customerId);
        if (!recent.isEmpty()) {
            Notification latest = recent.get(0);
            if (type != null && type.equals(latest.getType())
                    && title != null && title.equals(latest.getTitle())
                    && stripRef(message).equals(stripRef(latest.getMessage()))
                    && latest.getCreatedAt() != null
                    && Math.abs(ChronoUnit.SECONDS.between(latest.getCreatedAt(), Instant.now())) < 5) {
                log.info("Duplicate notification debounced for customer {} within 5s window", customerId);
                return latest;
            }
        }

        Notification saved = notificationRepository.save(new Notification(customerId, type, title, message, referenceId));
        push(saved);
        return saved;
    }

    private String stripRef(String msg) {
        if (msg == null) return "";
        int idx = msg.indexOf(". Ref:");
        return idx >= 0 ? msg.substring(0, idx).trim() : msg.trim();
    }

    private void push(Notification notification) {
        List<SseEmitter> list = emitters.get(notification.getCustomerId());
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .id(String.valueOf(notification.getId()))
                        .data(notification, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                removeEmitter(notification.getCustomerId(), emitter);
            }
        }
    }

    // ── live connections ────────────────────────────────────────────────────

    /** Opens a long-lived SSE stream for the customer. */
    public SseEmitter subscribe(Long customerId) {
        SseEmitter emitter = new SseEmitter(0L); // 0 = no timeout
        emitters.computeIfAbsent(customerId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(customerId, emitter));
        emitter.onTimeout(() -> removeEmitter(customerId, emitter));
        emitter.onError(e -> removeEmitter(customerId, emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException | IllegalStateException e) {
            removeEmitter(customerId, emitter);
        }
        return emitter;
    }

    private void removeEmitter(Long customerId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(customerId);
        if (list != null) {
            list.remove(emitter);
        }
    }

    public int activeConnections(Long customerId) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(customerId);
        return list == null ? 0 : list.size();
    }

    /** Keeps idle SSE connections alive through proxies and detects dead ones. */
    @Scheduled(fixedRate = 25000L)
    public void heartbeat() {
        for (Map.Entry<Long, CopyOnWriteArrayList<SseEmitter>> entry : emitters.entrySet()) {
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException | IllegalStateException e) {
                    removeEmitter(entry.getKey(), emitter);
                }
            }
        }
    }

    // ── queries / read state ────────────────────────────────────────────────

    public List<Notification> list(Long customerId) {
        return notificationRepository.findTop50ByCustomerIdOrderByCreatedAtDescIdDesc(customerId);
    }

    public long unreadCount(Long customerId) {
        return notificationRepository.countByCustomerIdAndSeenFalse(customerId);
    }

    @Transactional
    public Optional<Notification> markRead(Long customerId, Long notificationId) {
        Optional<Notification> found = notificationRepository.findByIdAndCustomerId(notificationId, customerId);
        found.ifPresent(n -> {
            n.setSeen(true);
            notificationRepository.save(n);
        });
        return found;
    }

    @Transactional
    public int markAllRead(Long customerId) {
        List<Notification> unread = notificationRepository.findByCustomerIdAndSeenFalse(customerId);
        for (Notification n : unread) {
            n.setSeen(true);
        }
        notificationRepository.saveAll(unread);
        log.debug("Marked {} notifications as read for customer {}", unread.size(), customerId);
        return unread.size();
    }
}
