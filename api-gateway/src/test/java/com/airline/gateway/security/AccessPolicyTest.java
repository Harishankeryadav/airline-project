package com.airline.gateway.security;

import com.airline.gateway.security.AccessPolicy.Decision;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;

import static com.airline.gateway.security.AccessPolicy.Decision.ALLOW;
import static com.airline.gateway.security.AccessPolicy.Decision.FORBIDDEN;
import static com.airline.gateway.security.AccessPolicy.Decision.NOT_FOUND;
import static com.airline.gateway.security.AccessPolicy.Decision.UNAUTHORIZED;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The access table from docs/ARCHITECTURE.md section 3, one assertion per row. */
class AccessPolicyTest {

    private final AccessPolicy policy = new AccessPolicy();

    private final AuthenticatedUser customer = new AuthenticatedUser(5L, "ana@example.com", List.of("CUSTOMER"));
    private final AuthenticatedUser business = new AuthenticatedUser(6L, "biz@example.com", List.of("CUSTOMER", "AIRLINE_BUSINESS"));
    private final AuthenticatedUser admin = new AuthenticatedUser(1L, "admin@example.com", List.of("ADMIN", "CUSTOMER"));

    private Decision decide(HttpMethod method, String path, AuthenticatedUser user) {
        return policy.decide(method, path, user);
    }

    // ------------------------------------------------------------ auth
    @Test
    void signupAndSigninArePublic() {
        assertEquals(ALLOW, decide(HttpMethod.POST, "/authservice/api/v1/signup", null));
        assertEquals(ALLOW, decide(HttpMethod.POST, "/authservice/api/v1/signin", null));
    }

    @Test
    void otherAuthEndpointsNeedASignedInUser() {
        assertEquals(UNAUTHORIZED, decide(HttpMethod.GET, "/authservice/api/v1/me", null));
        assertEquals(ALLOW, decide(HttpMethod.GET, "/authservice/api/v1/me", customer));
        assertEquals(UNAUTHORIZED, decide(HttpMethod.GET, "/authservice/api/v1/isAuthenticated", null));
        assertEquals(UNAUTHORIZED, decide(HttpMethod.GET, "/authservice/api/v1/signup", null)); // only POST is public
    }

    @Test
    void userAdministrationIsAdminOnly() {
        assertEquals(UNAUTHORIZED, decide(HttpMethod.GET, "/authservice/api/v1/users", null));
        assertEquals(FORBIDDEN, decide(HttpMethod.GET, "/authservice/api/v1/users", customer));
        assertEquals(FORBIDDEN, decide(HttpMethod.POST, "/authservice/api/v1/users/5/roles", business));
        assertEquals(ALLOW, decide(HttpMethod.POST, "/authservice/api/v1/users/5/roles", admin));
    }

    // ------------------------------------------------------------ flights
    @Test
    void flightSearchAndLookupsArePublic() {
        assertEquals(ALLOW, decide(HttpMethod.GET, "/flightsservice/api/v1/flights", null));
        assertEquals(ALLOW, decide(HttpMethod.GET, "/flightsservice/api/v1/flights/3", null));
        assertEquals(ALLOW, decide(HttpMethod.GET, "/flightsservice/api/v1/airports/search", null));
        assertEquals(ALLOW, decide(HttpMethod.GET, "/flightsservice/api/v1/city", null));
        assertEquals(ALLOW, decide(HttpMethod.OPTIONS, "/flightsservice/api/v1/flights", null));
    }

    @Test
    void flightWritesNeedAdminOrAirlineBusiness() {
        assertEquals(UNAUTHORIZED, decide(HttpMethod.POST, "/flightsservice/api/v1/flights", null));
        assertEquals(FORBIDDEN, decide(HttpMethod.POST, "/flightsservice/api/v1/flights", customer));
        assertEquals(ALLOW, decide(HttpMethod.POST, "/flightsservice/api/v1/flights", business));
        assertEquals(ALLOW, decide(HttpMethod.PATCH, "/flightsservice/api/v1/flights/3", admin));
        assertEquals(FORBIDDEN, decide(HttpMethod.DELETE, "/flightsservice/api/v1/city/2", customer));
        assertEquals(ALLOW, decide(HttpMethod.POST, "/flightsservice/api/v1/airports", business));
    }

    @Test
    void seatEndpointsAreInternalAndHiddenFromEveryoneIncludingAdmins() {
        for (AuthenticatedUser user : new AuthenticatedUser[]{null, customer, business, admin}) {
            assertEquals(NOT_FOUND, decide(HttpMethod.POST, "/flightsservice/api/v1/flights/3/seats/reserve", user));
            assertEquals(NOT_FOUND, decide(HttpMethod.POST, "/flightsservice/api/v1/flights/3/seats/release", user));
            assertEquals(NOT_FOUND, decide(HttpMethod.GET, "/flightsservice/api/v1/flights/3/seats/anything", user));
        }
    }

    // ------------------------------------------------------------ booking / reminder
    @Test
    void bookingEndpointsNeedASignedInUser() {
        assertEquals(UNAUTHORIZED, decide(HttpMethod.POST, "/bookingservice/api/v1/bookings", null));
        assertEquals(ALLOW, decide(HttpMethod.POST, "/bookingservice/api/v1/bookings", customer));
        assertEquals(UNAUTHORIZED, decide(HttpMethod.GET, "/bookingservice/api/v1/bookings/my", null));
        assertEquals(ALLOW, decide(HttpMethod.GET, "/bookingservice/api/v1/bookings/my", customer));
        assertEquals(ALLOW, decide(HttpMethod.POST, "/bookingservice/api/v1/bookings/7/cancel", customer));
    }

    @Test
    void reminderApiIsAdminOnly() {
        assertEquals(UNAUTHORIZED, decide(HttpMethod.GET, "/reminderservice/api/v1/tickets", null));
        assertEquals(FORBIDDEN, decide(HttpMethod.GET, "/reminderservice/api/v1/tickets", customer));
        assertEquals(FORBIDDEN, decide(HttpMethod.POST, "/reminderservice/api/v1/tickets", business));
        assertEquals(ALLOW, decide(HttpMethod.GET, "/reminderservice/api/v1/tickets", admin));
    }

    // ------------------------------------------------------------ infrastructure paths
    @Test
    void gatewayHealthIsPublicButOtherActuatorEndpointsAreHidden() {
        assertEquals(ALLOW, decide(HttpMethod.GET, "/actuator/health", null));
        assertEquals(ALLOW, decide(HttpMethod.GET, "/actuator/info", null));
        assertEquals(NOT_FOUND, decide(HttpMethod.GET, "/actuator/env", admin));
        assertEquals(NOT_FOUND, decide(HttpMethod.GET, "/flightsservice/actuator/env", admin));
        assertEquals(NOT_FOUND, decide(HttpMethod.GET, "/authservice/actuator/health", null));
    }

    @Test
    void swaggerAndApiDocsOfTheServicesAreNotExposedThroughTheGateway() {
        assertEquals(NOT_FOUND, decide(HttpMethod.GET, "/authservice/swagger-ui.html", admin));
        assertEquals(NOT_FOUND, decide(HttpMethod.GET, "/bookingservice/swagger-ui/index.html", admin));
        assertEquals(NOT_FOUND, decide(HttpMethod.GET, "/flightsservice/v3/api-docs", null));
    }

    @Test
    void unknownPathsRequireLoginByDefault() {
        assertEquals(UNAUTHORIZED, decide(HttpMethod.GET, "/something/else", null));
        assertEquals(ALLOW, decide(HttpMethod.GET, "/something/else", customer)); // then routing answers 404
    }

    @Test
    void methodMattersForTheSamePath() {
        assertEquals(ALLOW, decide(HttpMethod.GET, "/flightsservice/api/v1/flights", null));
        assertEquals(UNAUTHORIZED, decide(HttpMethod.POST, "/flightsservice/api/v1/flights", null));
        assertEquals(UNAUTHORIZED, decide(HttpMethod.PUT, "/flightsservice/api/v1/flights/1", null));
    }
}
