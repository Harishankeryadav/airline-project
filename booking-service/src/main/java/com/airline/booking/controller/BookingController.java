package com.airline.booking.controller;

import com.airline.booking.dto.ApiResult;
import com.airline.booking.dto.BookingResponse;
import com.airline.booking.dto.CreateBookingRequest;
import com.airline.booking.entity.BookingStatus;
import com.airline.booking.security.AuthenticatedUser;
import com.airline.booking.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings")
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @Operation(summary = "Book seats on a flight",
            description = "The booking belongs to the signed-in user (from the token). Reserves the seats atomically in "
                    + "flights-service: 409 if not enough seats, 503 if flights-service is unreachable.")
    public ResponseEntity<ApiResult<BookingResponse>> create(@AuthenticationPrincipal AuthenticatedUser user,
                                                             @Valid @RequestBody CreateBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.ok("Successfully completed the booking", bookingService.create(user, request)));
    }

    @GetMapping("/my")
    @Operation(summary = "My bookings (newest first)")
    public ResponseEntity<ApiResult<List<BookingResponse>>> mine(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(ApiResult.ok("Successfully fetched your bookings", bookingService.listMine(user)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a booking", description = "Owner or ADMIN only; anyone else gets 404.")
    public ResponseEntity<ApiResult<BookingResponse>> get(@AuthenticationPrincipal AuthenticatedUser user,
                                                          @PathVariable Long id) {
        return ResponseEntity.ok(ApiResult.ok("Successfully fetched the booking", bookingService.get(user, id)));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a booking and give the seats back",
            description = "Owner or ADMIN. Only CONFIRMED bookings of flights that have not departed.")
    public ResponseEntity<ApiResult<BookingResponse>> cancel(@AuthenticationPrincipal AuthenticatedUser user,
                                                             @PathVariable Long id) {
        return ResponseEntity.ok(ApiResult.ok("Successfully cancelled the booking", bookingService.cancel(user, id)));
    }

    @GetMapping
    @Operation(summary = "All bookings (ADMIN)", description = "Optional ?status=CONFIRMED|CANCELLED|FAILED|PENDING")
    public ResponseEntity<ApiResult<List<BookingResponse>>> all(@RequestParam(required = false) BookingStatus status) {
        return ResponseEntity.ok(ApiResult.ok("Successfully fetched the bookings", bookingService.listAll(status)));
    }
}
