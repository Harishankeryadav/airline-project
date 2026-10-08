package com.airline.booking.client;

import com.airline.booking.dto.ApiResult;
import com.airline.booking.exception.ServiceUnavailableException;
import feign.FeignException;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Thin wrapper over {@link FlightsClient}: unwraps the {success,data} envelope and turns connection problems
 * (timeout, refused, no instance registered) into a 503.
 * HTTP error statuses are already mapped to proper exceptions by {@link FlightsErrorDecoder}.
 */
@Component
public class FlightsGateway {

    private final FlightsClient client;

    public FlightsGateway(FlightsClient client) {
        this.client = client;
    }

    public FlightDto getFlight(Long id) {
        return unwrap(() -> client.getFlight(id));
    }

    public FlightDto reserveSeats(Long id, int seats) {
        return unwrap(() -> client.reserveSeats(id, new SeatRequest(seats)));
    }

    public FlightDto releaseSeats(Long id, int seats) {
        return unwrap(() -> client.releaseSeats(id, new SeatRequest(seats)));
    }

    private FlightDto unwrap(Supplier<ApiResult<FlightDto>> call) {
        try {
            ApiResult<FlightDto> result = call.get();
            if (result == null || result.data() == null) {
                throw new ServiceUnavailableException("The flights service returned an empty response");
            }
            return result.data();
        } catch (FeignException e) {
            throw new ServiceUnavailableException("The flights service could not be reached, please try again");
        }
    }
}
