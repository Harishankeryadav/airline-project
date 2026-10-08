# How the services connect

Services are built one at a time, but each follows this contract, so they plug together without rework.
Everything in this document is built.
Infrastructure from `docker-compose.yml`: MySQL, Redis, RabbitMQ and Mailpit (a local SMTP server that catches every email; UI on :8025).

## 1. Services, ports and registry names

| Service | Eureka name (use in Feign / gateway `lb://`) | Port | Database | Status |
|---|---|---|---|---|
| discovery-server | - | 8761 | - | done |
| flights-service | `FLIGHTS-SERVICE` | 8081 | `flights_db` | done |
| auth-service | `AUTH-SERVICE` | 8082 | `auth_db` | done |
| booking-service | `BOOKING-SERVICE` | 8083 | `booking_db` | done |
| reminder-service | `REMINDER-SERVICE` | 8084 | `reminder_db` | done |
| api-gateway | `API-GATEWAY` | 8080 | - | done |
| React app (`frontend/`) | - | 5173 | - | done |

Each service owns its database. **No service reads another service's tables** - they call each other's APIs.
Cross-service references (a booking's `flightId` / `userId`) are plain ids with no cross-database foreign key.

## 2. Request flow

```
React app (5173, in the browser) -> api-gateway (8080) -> routes by name via Eureka -> auth / flights / booking / reminder
booking-service --OpenFeign--> flights-service     (read flight, reserve / release seats)
booking-service --RabbitMQ---> reminder-service    (BOOKING_CONFIRMED -> email)
```

Gateway routes - the same paths the existing React file already uses:

| Public path | Goes to | Prefix stripped |
|---|---|---|
| `/authservice/**` | `lb://AUTH-SERVICE` | `/authservice` |
| `/flightsservice/**` | `lb://FLIGHTS-SERVICE` | `/flightsservice` |
| `/bookingservice/**` | `lb://BOOKING-SERVICE` | `/bookingservice` |
| `/reminderservice/**` | `lb://REMINDER-SERVICE` | `/reminderservice` |

e.g. `GET :8080/flightsservice/api/v1/airports/search?q=del` reaches `GET :8081/api/v1/airports/search?q=del`.

## 3. Who may call what

| Endpoint | Rule | Enforced today by |
|---|---|---|
| `POST /signup`, `/signin` (auth) | public | auth-service |
| `/api/v1/users/**` (auth) | `ADMIN` | auth-service |
| `GET /flightsservice/**` (search, airports, cities) | public | - |
| `POST/PATCH/DELETE /flightsservice/**` | `ADMIN` or `AIRLINE_BUSINESS` | **gateway** |
| `POST /flights/{id}/seats/**` | **internal only** (booking-service calls it directly); answered 404 by the gateway, even for admins | **gateway** |
| `POST /bookings`, `GET /bookings/my`, `GET /bookings/{id}`, `POST /bookings/{id}/cancel` | any signed-in user (owner or ADMIN for `{id}`) | booking-service |
| `GET /bookings` (everyone's) | `ADMIN` | booking-service |
| `/api/v1/tickets/**` (reminder) | `ADMIN` | reminder-service |

**The gateway only protects what goes through it.** flights-service's write endpoints and `seats/*` have no login of their own, so in any
real deployment publish **only port 8080** (docker network / firewall) and keep 8081-8084 private. Locally all ports are open for debugging.

## 4. JWT contract (auth-service issues, every other service verifies)

- Algorithm **HS256**, signed with the shared secret `JWT_SECRET` (>= 32 chars). **Every service that verifies tokens must be given the same value.**
- Claims: `iss` = `airline-auth-service`, `sub` = user id, `email`, `roles` = `["ADMIN","CUSTOMER",...]`, `iat`, `exp` (default 60 min).
- Sent as `Authorization: Bearer <token>`. (auth and booking also accept the legacy `x-access-token` header.)
- Roles are read from the token, so a role change takes effect at the user's next sign-in.
- **Each service verifies the token itself** - nothing relies on trusting headers set by a gateway. The gateway additionally
  checks roles at the edge, blocks internal endpoints, rate-limits and adds CORS, and forwards `Authorization` unchanged.

## 5. Response and error format (identical in every service)

```json
{ "success": true,  "message": "...", "data": { }, "err": {} }
{ "success": false, "message": "Validation failed", "data": null, "err": { "email": "email must be a valid email address" } }
```
Status codes: 400 validation, 401 missing/invalid token or bad credentials, 403 role not allowed, 404 not found
(also "someone else's booking"), 409 conflict (duplicate / not enough seats / wrong booking state), 503 a dependency is down.

## 6. Service-to-service calls (booking-service -> flights-service, OpenFeign)

`@FeignClient(name = "flights-service", url = "${clients.flights.url:}")` - an empty `FLIGHTS_URL` means "find it in Eureka";
set `FLIGHTS_URL=http://localhost:8081` to run booking-service without the registry.

| Purpose | Call | Errors mapped by booking-service |
|---|---|---|
| Read price / departure / seats | `GET /api/v1/flights/{id}` | 404 -> 404 |
| Take seats (atomic) | `POST /api/v1/flights/{id}/seats/reserve` `{"seats": n}` | 409 "Only 2 seat(s) left..." -> 409 |
| Give seats back | `POST /api/v1/flights/{id}/seats/release` `{"seats": n}` | |
| flights-service down / timeout | | 503 |

Feign timeouts: connect 2 s, read 5 s. **No automatic retries** - "reserve" is not idempotent.

### Booking flow and failure handling
1. `GET` the flight (must exist, must not have departed, quick seat check).
2. Save booking `PENDING` (own commit - no DB transaction is held open across remote calls).
3. `reserve` seats. Rejected/failed -> booking `FAILED` with the reason; nothing to undo.
4. Save `CONFIRMED`. If this save fails -> `release` the seats, mark `FAILED`.
5. Publish `BOOKING_CONFIRMED`. If RabbitMQ is down the booking **stays confirmed**; `BookingRecoveryJob` re-publishes it (every minute, for rows older than 30 s).

Cancel: compare-and-set `CONFIRMED -> CANCELLED` (double clicks / concurrent cancels cannot release twice), then `release` seats.
If flights-service is down the cancellation still stands and the release is retried by the recovery job.

## 7. Event contract (booking-service -> reminder-service, RabbitMQ)

One topic exchange `airline.events` (durable). Both services declare the exchange, queues and bindings **identically**
(durable, no extra arguments - RabbitMQ rejects a re-declaration that differs, so never add queue arguments on one side only).

| Event | Routing key | Queue | Published when |
|---|---|---|---|
| `BOOKING_CONFIRMED` | `booking.confirmed` | `reminder.booking-confirmed` | booking becomes CONFIRMED (retried by `BookingRecoveryJob` if RabbitMQ is down) |
| `BOOKING_CANCELLED` | `booking.cancelled` | `reminder.booking-cancelled` | booking is cancelled (best effort) |

Payload - JSON; `eventId` is stable per booking (`booking-confirmed-<id>` / `booking-cancelled-<id>`):
```json
{ "eventId": "booking-confirmed-12", "type": "BOOKING_CONFIRMED", "occurredAt": "2026-10-10T09:00:00Z",
  "data": { "bookingId": 12, "userId": 7, "userEmail": "ana@example.com", "flightId": 3, "flightNumber": "AI-101",
            "origin": "Bengaluru (BLR)", "destination": "Delhi (DEL)", "departureTime": "2026-10-20T09:00:00Z",
            "noOfSeats": 2, "totalCost": 10400.00 } }
```
`BOOKING_CANCELLED` has the same `data` shape. Each service owns its own copy of the event classes - the JSON is the contract;
unknown fields are ignored, so fields can be added safely.

**What reminder-service does with them**
- `BOOKING_CONFIRMED` -> a confirmation email now, plus a departure reminder `REMINDER_LEAD_HOURS` (24) before departure
  (skipped if the booking was made later than that).
- `BOOKING_CANCELLED` -> drops the booking's unsent emails, sends a cancellation notice.
  If the cancel event overtakes the confirm event (different queues), the late confirm event is ignored.
- Emails are rows in `notifications` (`PENDING -> SENDING -> SENT | FAILED | CANCELLED`). A dispatcher sends due ones every 10 s,
  claiming each row first (compare-and-set), so several instances never double-send. A failed send is retried after 5, 10, 15... minutes,
  up to 5 attempts, then `FAILED` (an admin can `POST /api/v1/tickets/{id}/retry`).
- **Idempotent**: unique `(source_event_id, kind)` - a redelivered event creates nothing. Delivery is at-least-once.
- A message that cannot be processed is retried 3 times, then republished to **`reminder.dead-letter`** (exchange `airline.events.dlx`,
  routing key `failed`) for an admin to inspect in the RabbitMQ UI - it is neither lost nor redelivered forever.
- The consumer deserialises into **its own** class (Spring AMQP type precedence `INFERRED`), never by the `__TypeId__` header,
  which names a class that exists only inside booking-service.

## 8. api-gateway behaviour

Spring Cloud Gateway (reactive) on :8080. For every request, in this order:

1. **Path hardening** - `..`, encoded dots/slashes (`%2e`, `%2f`), `;` path parameters, backslashes and `//` -> **400**. (Access rules match the
   path as written, so a trick like `/seats%2Freserve` must not be able to slip past a rule.)
2. **Token** - read from `Authorization: Bearer` (or legacy `x-access-token`) and verified with the shared secret. A bad token counts as
   *no token*: public endpoints still work, protected ones answer 401.
3. **Access rules** (`AccessPolicy`, first match wins, unlisted paths require login): table in section 3 -> **401** no/invalid token, **403** wrong role,
   **404** for internal endpoints, other services' `/actuator/**`, `/swagger-ui/**` and `/v3/api-docs/**`.
4. **Rate limit** - token bucket per client IP **and per route**, stored in Redis: 20 req/s (burst 40) in general, **1 req/s (burst 10) for
   signin/signup** (brute-force protection). Over the limit -> 429. If Redis is down requests are allowed (fail open). Tunable via `RATE_LIMIT_*`.
5. **Route** by prefix to `lb://<EUREKA-NAME>` with the prefix stripped. No instance registered -> **503**, timeout -> **504**
   (same JSON envelope as every service; connect timeout 3 s, response timeout 15 s). Requests are never retried.

**CORS** is handled here only (origins from `CORS_ALLOWED_ORIGINS`, default the Vite and CRA dev servers); services add no CORS headers.
**Identity** is not injected as headers: `Authorization` is forwarded unchanged and each service verifies it.
The rate-limit key uses the TCP peer address; behind a reverse proxy configure Spring's forwarded-header support so it sees the real client
(`X-Forwarded-For` is deliberately not trusted by default - clients can fake it).

## 9. The React app

`frontend/` (Vite + React 18, no router library) is the last piece. It knows **one** address - the gateway (`VITE_API_BASE_URL`, default
`http://localhost:8080`) - and never calls a service port. Port 5173 is fixed so it matches the gateway's default `CORS_ALLOWED_ORIGINS`.

- The token from `signin` is kept in `sessionStorage` and sent as `Authorization: Bearer`. The app only *decodes* it to know the email and roles
  (to choose which tabs to show); the gateway and services verify the signature. A `401` on a request that carried a token signs the person out.
- Tabs follow the access table in section 3: Administration appears for `ADMIN` / `AIRLINE_BUSINESS` (flights and airports), and the bookings,
  emails and users sections only for `ADMIN`. Hiding a tab is a convenience - the gateway refuses the calls anyway.
- Airport search boxes use `GET /airports/search`. Bookings send only `{flightId, noOfSeats}`; the server takes the user from the token.
  The internal `seats/*` endpoints are never called from the browser.

## 10. Containers

`docker compose --profile app up --build -d` runs the whole system. Inside the compose network the services reach each other by container name
(`mysql`, `redis`, `rabbitmq`, `mailpit`, `discovery-server`) and register in Eureka by IP, so `lb://FLIGHTS-SERVICE` works unchanged.
Only the **gateway (8080)** and the **web app (5173)** are published to the host (plus the Eureka, RabbitMQ and Mailpit dashboards and the infrastructure
ports for local convenience) - this enforces the rule from section 3 that the services without their own login are reachable only through the gateway.
The web app's API address is baked in at build time (`PUBLIC_API_URL`) and must be an address the *browser* can reach, which also has to appear in the
gateway's `CORS_ALLOWED_ORIGINS`.

## 11. Running pieces on their own

Every service starts alone: `EUREKA_ENABLED=false` skips the registry; booking-service additionally needs `FLIGHTS_URL`.
Recommended order for everything: `docker compose up -d` -> discovery-server -> flights-service + auth-service -> booking-service
-> reminder-service -> api-gateway. reminder-service does not call any other service, so it only needs RabbitMQ, MySQL and an SMTP server. `scripts/run-all.sh` does this; `scripts/smoke-test.sh` then exercises the whole chain, including the gateway rules.
