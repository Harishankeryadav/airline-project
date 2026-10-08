package com.airline.gateway.security;

import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;
import java.util.Set;

/**
 * Who may call what at the edge - the table in docs/ARCHITECTURE.md section 3.
 * Rules are checked top to bottom, FIRST MATCH WINS. Anything not listed needs a signed-in user (deny by default).
 * Pure logic with no web dependencies, so it is unit-tested directly.
 */
@Component
public class AccessPolicy {

    public enum Decision { ALLOW, UNAUTHORIZED, FORBIDDEN, NOT_FOUND }

    private enum Requirement { PUBLIC, AUTHENTICATED, ROLES, DENY }

    private record Rule(Set<HttpMethod> methods, PathPattern pattern, Requirement requirement, Set<String> roles) {
        boolean matches(HttpMethod method, PathContainer path) {
            return (methods.isEmpty() || methods.contains(method)) && pattern.matches(path);
        }
    }

    private static final Set<HttpMethod> ANY = Set.of();
    private static final Set<HttpMethod> READ = Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS);
    private static final Set<HttpMethod> POST = Set.of(HttpMethod.POST);

    private final PathPatternParser parser = new PathPatternParser();
    private final List<Rule> rules = List.of(
            // --- never exposed through the gateway: other services' actuator and API docs, the gateway's own actuator
            rule(ANY, "/actuator/health", Requirement.PUBLIC),
            rule(ANY, "/actuator/info", Requirement.PUBLIC),
            rule(ANY, "/actuator/**", Requirement.DENY),
            rule(ANY, "/*/actuator/**", Requirement.DENY),
            rule(ANY, "/*/swagger-ui/**", Requirement.DENY),
            rule(ANY, "/*/swagger-ui.html", Requirement.DENY),
            rule(ANY, "/*/v3/api-docs/**", Requirement.DENY),

            // --- auth-service
            rule(POST, "/authservice/api/v1/signup", Requirement.PUBLIC),
            rule(POST, "/authservice/api/v1/signin", Requirement.PUBLIC),
            rule(ANY, "/authservice/api/v1/users/**", Requirement.ROLES, "ADMIN"),
            rule(ANY, "/authservice/**", Requirement.AUTHENTICATED),

            // --- flights-service: seat endpoints are internal (booking-service calls them directly, not via the gateway)
            rule(ANY, "/flightsservice/api/v1/flights/*/seats/**", Requirement.DENY),
            rule(READ, "/flightsservice/**", Requirement.PUBLIC),                       // search, airports, cities
            rule(ANY, "/flightsservice/**", Requirement.ROLES, "ADMIN", "AIRLINE_BUSINESS"), // any write

            // --- booking-service (it also enforces ownership / ADMIN-only listing itself)
            rule(ANY, "/bookingservice/**", Requirement.AUTHENTICATED),

            // --- reminder-service
            rule(ANY, "/reminderservice/**", Requirement.ROLES, "ADMIN"),

            // --- everything else
            rule(ANY, "/**", Requirement.AUTHENTICATED));

    /** @param user the verified caller, or null if there is no valid token */
    public Decision decide(HttpMethod method, String path, AuthenticatedUser user) {
        PathContainer container = PathContainer.parsePath(path);
        for (Rule rule : rules) {
            if (rule.matches(method, container)) {
                return switch (rule.requirement()) {
                    case PUBLIC -> Decision.ALLOW;
                    case DENY -> Decision.NOT_FOUND;
                    case AUTHENTICATED -> user == null ? Decision.UNAUTHORIZED : Decision.ALLOW;
                    case ROLES -> user == null ? Decision.UNAUTHORIZED
                            : (user.hasAnyRole(rule.roles()) ? Decision.ALLOW : Decision.FORBIDDEN);
                };
            }
        }
        return Decision.UNAUTHORIZED; // unreachable: the last rule matches everything
    }

    private Rule rule(Set<HttpMethod> methods, String pattern, Requirement requirement, String... roles) {
        return new Rule(methods, parser.parse(pattern), requirement, Set.of(roles));
    }
}
