package com.airline.booking.client;

import com.airline.booking.exception.BadRequestException;
import com.airline.booking.exception.ConflictException;
import com.airline.booking.exception.ResourceNotFoundException;
import com.airline.booking.exception.ServiceUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.RequiredArgsConstructor;

import java.io.InputStream;

/**
 * Translates flights-service HTTP errors into booking-service exceptions, so the user sees e.g. 409
 * "Only 2 seat(s) left on flight AI-101, requested 5" instead of a generic 500.
 */
@RequiredArgsConstructor
public class FlightsErrorDecoder implements ErrorDecoder {

    private final ObjectMapper objectMapper;

    @Override
    public Exception decode(String methodKey, Response response) {
        String detail = readDetail(response);
        return switch (response.status()) {
            case 400 -> new BadRequestException(detail != null ? detail : "The flights service rejected the request");
            case 404 -> new ResourceNotFoundException(detail != null ? detail : "Flight not found");
            case 409 -> new ConflictException(detail != null ? detail : "Not enough seats available on this flight");
            default -> new ServiceUnavailableException("The flights service is currently unavailable, please try again");
        };
    }

    /** flights-service errors look like {success:false, message, err:"<detail>"} - return the detail text if present. */
    private String readDetail(Response response) {
        if (response.body() == null) {
            return null;
        }
        try (InputStream in = response.body().asInputStream()) {
            JsonNode err = objectMapper.readTree(in).path("err");
            return err.isTextual() && !err.asText().isBlank() ? err.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
