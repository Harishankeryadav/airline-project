package com.airline.reminder.controller;

import com.airline.reminder.dto.ApiResult;
import com.airline.reminder.dto.CreateNotificationRequest;
import com.airline.reminder.dto.NotificationResponse;
import com.airline.reminder.entity.NotificationStatus;
import com.airline.reminder.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** ADMIN only (SecurityConfig). Booking emails are created automatically from RabbitMQ events. */
@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
@Tag(name = "Notifications (tickets)")
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping
    @Operation(summary = "Schedule a custom email",
            description = "Same as the Node API's POST /tickets. notificationTime is optional (default: send now).")
    public ResponseEntity<ApiResult<NotificationResponse>> create(@Valid @RequestBody CreateNotificationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.ok("Successfully registered the notification", notificationService.createCustom(request)));
    }

    @GetMapping
    @Operation(summary = "List notifications (newest 200)", description = "Optional ?status=PENDING|SENDING|SENT|FAILED|CANCELLED")
    public ResponseEntity<ApiResult<List<NotificationResponse>>> list(@RequestParam(required = false) NotificationStatus status) {
        return ResponseEntity.ok(ApiResult.ok("Successfully fetched the notifications", notificationService.list(status)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a notification")
    public ResponseEntity<ApiResult<NotificationResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResult.ok("Successfully fetched the notification", notificationService.get(id)));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a PENDING notification")
    public ResponseEntity<ApiResult<NotificationResponse>> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResult.ok("Successfully cancelled the notification", notificationService.cancel(id)));
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "Retry a FAILED notification")
    public ResponseEntity<ApiResult<NotificationResponse>> retry(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResult.ok("Notification queued for sending", notificationService.retry(id)));
    }
}
