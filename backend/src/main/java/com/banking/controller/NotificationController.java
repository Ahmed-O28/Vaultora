package com.banking.controller;

import com.banking.model.Notification;
import com.banking.service.NotificationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Push notification API. Lives under /api/customers/{customerId}/..., so the existing JWT interceptor
 * authenticates the caller and guarantees a customer can only access their own notifications.
 */
@RestController
@RequestMapping("/api/customers/{customerId}/notifications")
@CrossOrigin(origins = "*")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<List<Notification>> list(@PathVariable Long customerId) {
        return ResponseEntity.ok(notificationService.list(customerId));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Object>> unreadCount(@PathVariable Long customerId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("count", notificationService.unreadCount(customerId));
        return ResponseEntity.ok(body);
    }

    /** Real-time push channel (Server-Sent Events). */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long customerId) {
        return notificationService.subscribe(customerId);
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<Notification> markRead(@PathVariable Long customerId,
                                                 @PathVariable Long notificationId) {
        return notificationService.markRead(customerId, notificationId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Object>> markAllRead(@PathVariable Long customerId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("updated", notificationService.markAllRead(customerId));
        return ResponseEntity.ok(body);
    }

    /** Sends a test push to the caller so the channel can be verified end-to-end. */
    @PostMapping("/test")
    public ResponseEntity<Notification> sendTest(@PathVariable Long customerId) {
        Notification n = notificationService.notifyCustomer(customerId, "SYSTEM", "Test notification",
                "Push notifications are working. You will be alerted instantly about transfers and bill payments.",
                null);
        return ResponseEntity.ok(n);
    }
}
