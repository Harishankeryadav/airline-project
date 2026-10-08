# Airline Microservices (Spring Boot)

Migration of the Node.js airline backend (5 services) to Spring Boot microservices.
**How the services connect (names, routes, JWT, events, failure handling): [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).**

| Service | Replaces (Node) | Status | Port |
|---|---|---|---|
| `discovery-server` (Eureka) | - | done | 8761 |
| `flights-service` | `1.FlightsAndSearch` | done | 8081 |
| `auth-service` | `2.Auth` | done | 8082 |
| `booking-service` | `3.BookingService` | done | 8083 |
| `reminder-service` | `4.ReminderService` | done | 8084 |
| `api-gateway` | `5.API_Gateway` | done | 8080 |
| React frontend (`frontend/`) | `airline-frontend.jsx` | done | 5173 |

**Stack:** React 18 + Vite (frontend); Java 21, Spring Boot 3.4, Spring Cloud (Gateway, Eureka, OpenFeign), Maven, MySQL 8 + Flyway, Redis, springdoc Swagger, RabbitMQ, JavaMail, JWT (jjwt). Mailpit catches emails locally.

## How they are linked

```
React --> api-gateway :8080 (JWT + role rules, rate limit, CORS) --lb by Eureka name--> auth / flights / booking / reminder
auth-service ----JWT (shared JWT_SECRET)----> verified by the gateway AND by booking-service, reminder-service
booking-service --OpenFeign (lb by name FLIGHTS-SERVICE)--> flights-service   read flight, reserve / release seats
booking-service --RabbitMQ airline.events (booking.confirmed / booking.cancelled)--> reminder-service --SMTP--> customer
all services register in Eureka (discovery-server)
```

## Run everything in Docker (nothing to install but Docker)

```bash
docker compose --profile app up --build -d      # builds all 6 services and the web app, then starts everything
GATEWAY_ONLY=1 ./scripts/smoke-test.sh          # same end-to-end check, through the gateway only
docker compose --profile app down               # stop (add -v to delete the databases too)
```
Open <http://localhost:5173>. First start takes a few minutes (Maven downloads dependencies once; later builds reuse the cache) and needs about
4 GB of free memory for the six JVMs. The default administrator is `admin@example.com` / `ChangeMe123!` - set `BOOTSTRAP_ADMIN_EMAIL` /
`BOOTSTRAP_ADMIN_PASSWORD` in a `.env` file first if anyone else can reach the machine.

Published ports in this mode: **5173** (web app), **8080** (gateway), 8761 (Eureka dashboard), 8025 (Mailpit), 15672 (RabbitMQ UI), and the
databases/queue for convenience. The four services' own ports (8081-8084) are **not** published - flights-service has no login of its own, so only the
gateway, inside the Docker network, can reach it. That is the shape to keep in a real deployment (also stop publishing 3306, 6379 and 5672 there).
Images: one `Dockerfile.service` (build argument `MODULE`) for every Java service, and `frontend/Dockerfile` (nginx serving the built site). A GitHub Actions
workflow (`.github/workflows/ci.yml`) runs the Java tests, the web app tests and build, and the Docker build on every push.

## Run everything without Docker for the services

Needs Docker (for MySQL, Redis, RabbitMQ and Mailpit), JDK 21 and Maven 3.9+.

```bash
./scripts/run-all.sh        # MySQL + Redis + RabbitMQ + Mailpit, then discovery, flights, auth, booking, reminder, gateway (logs in ./logs)
./scripts/smoke-test.sh     # sign up/in, search, book, seats dropped, confirmation email, cancel, seats back, cancellation email, gateway access rules
./scripts/stop-all.sh
```

Then the web app (needs Node 18+), in another terminal:
```bash
./scripts/run-frontend.sh   # npm install on first run, then http://localhost:5173
```

Or manually, one terminal each:
```bash
docker compose up -d
mvn -pl discovery-server spring-boot:run
mvn -pl flights-service  spring-boot:run
BOOTSTRAP_ADMIN_EMAIL=admin@example.com BOOTSTRAP_ADMIN_PASSWORD='ChangeMe123!' mvn -pl auth-service spring-boot:run
mvn -pl booking-service  spring-boot:run
mvn -pl reminder-service spring-boot:run
mvn -pl api-gateway      spring-boot:run
mvn test                    # unit tests of all modules
```

| What | URL |
|---|---|
| **React app** (run `./scripts/run-frontend.sh`) | <http://localhost:5173> |
| **API gateway - the only address the app calls** | <http://localhost:8080> |
| Eureka dashboard (all services should appear) | <http://localhost:8761> |
| Flights Swagger | <http://localhost:8081/swagger-ui.html> |
| Auth Swagger | <http://localhost:8082/swagger-ui.html> |
| Booking Swagger | <http://localhost:8083/swagger-ui.html> |
| Reminder Swagger (ADMIN token) | <http://localhost:8084/swagger-ui.html> |
| Mailpit - every email that was "sent" | <http://localhost:8025> |
| RabbitMQ UI (guest / guest) | <http://localhost:15672> |

Any service runs alone with `EUREKA_ENABLED=false` (booking-service then also needs `FLIGHTS_URL=http://localhost:8081`).
To send real email instead of using Mailpit, set `MAIL_HOST/MAIL_PORT/MAIL_USERNAME/MAIL_PASSWORD/MAIL_SMTP_AUTH/MAIL_STARTTLS` (Gmail example in `reminder-service/src/main/resources/application.yml`).
Configuration is via environment variables with local defaults - see `.env.example`.
**`JWT_SECRET` must be identical for auth-service, booking-service, reminder-service and api-gateway** (the dev default already is).
The default `dev` profile of flights-service also loads 4 sample flights.

---

## api-gateway (`:8080`)

Everything the React app calls goes through one address. Paths are `/<service>/<the service's own path>`:

| Prefix | Service | Who may call (details: docs/ARCHITECTURE.md section 3 and 8) |
|---|---|---|
| `/authservice/**` | auth-service | `signup`/`signin` public, `users/**` ADMIN, rest signed-in |
| `/flightsservice/**` | flights-service | GET public; writes need ADMIN or AIRLINE_BUSINESS; `flights/*/seats/**` hidden (404) |
| `/bookingservice/**` | booking-service | signed-in (booking-service enforces owner / ADMIN itself) |
| `/reminderservice/**` | reminder-service | ADMIN |

Also: rate limiting per IP (strict on signin/signup), CORS for `CORS_ALLOWED_ORIGINS`, JSON errors (401/403/404/429/503/504),
rejects tricky paths (`..`, `%2f`, `;`), hides other services' actuator and Swagger.
Swagger UIs are reached on the services' own ports, not through the gateway.

```bash
curl "localhost:8080/flightsservice/api/v1/airports/search?q=del"                       # public
curl -X POST localhost:8080/authservice/api/v1/signin -H 'Content-Type: application/json' -d '{"email":"ana@example.com","password":"password123"}'
curl localhost:8080/bookingservice/api/v1/bookings/my -H "Authorization: Bearer $TOKEN"   # needs a token
curl -X POST localhost:8080/flightsservice/api/v1/city -H "Authorization: Bearer $TOKEN" ... # 403 for a customer
```

---

## Frontend (`frontend/`)

Your `airline-frontend.jsx` rebuilt as a Vite + React project, keeping its departure-board look (ink and amber, Space Grotesk + IBM Plex Mono)
but aligned with the real API. It talks only to the gateway.

| Screen | What it does |
|---|---|
| **Flights** | The board shows the next departures on arrival. **From / To** are autocomplete boxes backed by `/airports/search` (type "del" or "international"). Filters: date, passengers, highest fare, sort. **Book** per flight (1-9 seats); the seat count refreshes afterwards. A visitor sees "Sign in to book". |
| **My bookings** | Your bookings with status; **Cancel** asks for a second confirmation, then the seats go back on sale. Failed bookings show why. |
| **Account** | Sign in / create account (signs you in straight away). Shows your roles and when the session ends; signs out by itself when the token expires. |
| **Administration** | *Admin or airline business:* add cities, airports (with IATA code), airplanes and flights. *Admin only:* all bookings, scheduled/sent emails (cancel / retry / schedule one), users and their roles. |

```bash
cd frontend
cp .env.example .env     # VITE_API_BASE_URL=http://localhost:8080, VITE_CURRENCY=INR
npm install
npm run dev              # http://localhost:5173
npm test                 # 49 tests: API client, airport picker, booking flow, cancel, role-based screens
npm run build            # production build in dist/ - serve it from any static host and add its URL to CORS_ALLOWED_ORIGINS
```
The gateway address can also be changed from the gear icon in the app's header.

What changed from the old file, because the backend changed: signin returns `data.token` and the app decodes the roles from it; passwords need 8+
characters; flights show `availableSeats` (the old UI showed `totalSeats` as "seats left"); bookings are a list (`/bookings/my`) with cancel, not a
lookup by id; the booking never sends a `userId`; every error shows the backend's own sentence (for example "Only 1 seat(s) left on flight AI-101,
requested 2"), and 429 / 503 get a plain explanation.

---

## flights-service (`/api/v1`)

| Method | Path | Notes |
|---|---|---|
| POST / GET / GET`/{id}` / PATCH`/{id}` / DELETE`/{id}` | `/city` | `GET /city?name=ben` filters by prefix. DELETE -> 409 while the city has airports |
| POST / GET / GET`/{id}` / PATCH`/{id}` / DELETE`/{id}` | `/airports` | `GET /airports?cityId=1` |
| **GET** | **`/airports/search?q=&limit=`** | **new** - airport name OR city name, partial, case-insensitive, prefix matches first, Redis-cached |
| POST / GET / GET`/{id}` | `/airplanes` | new (the Node version had no endpoint) |
| POST | `/flights` | seats come from the airplane's capacity |
| GET | `/flights` | filters: `departureAirportId, arrivalAirportId, minPrice, maxPrice, departureDate (UTC), seats, sortBy, order, page, size` |
| GET / PATCH`/{id}` | `/flights/{id}` | PATCH edits times, price, gate only |
| POST | `/flights/{id}/seats/reserve`, `/seats/release` | `{"seats": 2}` - atomic; used by booking-service |

Redis caches airport lists, airport search, flight search and flight-by-id (writes evict; flight caches also expire after 2 min).
If Redis is down the API still works, just uncached.

## auth-service (`/api/v1`)

| Method | Path | Access | Notes |
|---|---|---|---|
| POST | `/signup` | public | `{email, password}` (8-72 chars). New users are `CUSTOMER`. 409 if the email exists |
| POST | `/signin` | public | returns `{token, tokenType, expiresInSeconds, user}`. Wrong email *or* password -> the same 401 |
| GET | `/isAuthenticated`, `/me`, `/isAdmin` | token | |
| GET | `/users`, `/users/{id}` | ADMIN | |
| POST / DELETE | `/users/{id}/roles`, `/users/{id}/roles/{role}` | ADMIN | the last ADMIN cannot be removed |

The first ADMIN is created at startup from `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD`.

## booking-service (`/api/v1/bookings`)

| Method | Path | Access | Notes |
|---|---|---|---|
| POST | `/bookings` | signed-in | `{flightId, noOfSeats (1-9)}`. The booking belongs to the **token's** user. 409 not enough seats, 400 flight departed, 503 flights-service down |
| GET | `/bookings/my` | signed-in | newest first |
| GET | `/bookings/{id}` | owner / ADMIN | anyone else gets 404 |
| POST | `/bookings/{id}/cancel` | owner / ADMIN | only `CONFIRMED` bookings before departure; seats are given back |
| GET | `/bookings?status=` | ADMIN | |

Statuses: `PENDING -> CONFIRMED -> CANCELLED`, or `FAILED` (nothing was reserved).
On confirmation a `BOOKING_CONFIRMED` event is published to RabbitMQ, and on cancellation a `BOOKING_CANCELLED` event, for the reminder-service.

```bash
TOKEN=$(curl -s -X POST localhost:8082/api/v1/signin -H 'Content-Type: application/json' \
        -d '{"email":"ana@example.com","password":"password123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')
curl -X POST localhost:8083/api/v1/bookings -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"flightId":1,"noOfSeats":2}'
```

## reminder-service (`/api/v1/tickets`, ADMIN only)

Customers never call this service - they receive emails. It listens to booking events (see docs/ARCHITECTURE.md section 7):

| Event | Emails created |
|---|---|
| `BOOKING_CONFIRMED` | confirmation (sent right away) + departure reminder (24 h before departure, `REMINDER_LEAD_HOURS`) |
| `BOOKING_CANCELLED` | the booking's unsent emails are dropped; a cancellation notice is sent |

| Method | Path | Notes |
|---|---|---|
| POST | `/tickets` | custom email `{subject, content, recipientEmail, notificationTime?}` (kept from the Node API) |
| GET | `/tickets?status=` | newest 200: `PENDING / SENDING / SENT / FAILED / CANCELLED` |
| GET | `/tickets/{id}` | |
| POST | `/tickets/{id}/cancel`, `/tickets/{id}/retry` | cancel a PENDING one / re-queue a FAILED one |

Retries: a failed email is retried after 5, 10, 15... minutes up to 5 attempts, then `FAILED`. Unprocessable messages go to the
`reminder.dead-letter` queue (RabbitMQ UI) after 3 tries.

---

## Bugs from the Node version fixed

**flights** - `POST /airports` always crashed (wrong base class) -> fixed. Every error was a 500 -> proper 400/404/409. Overbooking race (read-then-PATCH) -> one atomic `UPDATE ... WHERE seats >= n` plus a DB `CHECK`. `totalSeats` meant "seats left" -> now `totalSeats` (capacity) and `availableSeats`. Model/migration column mismatch, no foreign keys, hard-coded seed ids, a duplicate/typo airport -> single Flyway schema, real FKs, clean seed data with IATA codes. City delete cascaded and wiped airports -> `RESTRICT`. `price: 0` rejected, no date/sort/paging in search -> fixed/added.

**auth** - duplicate email crashed (`res.status(undefined)`) -> 409. Unknown email / wrong password -> 500 -> identical 401 (no account probing). Missing/invalid token -> 500 -> 401. One salt for all passwords -> BCrypt per-password salt. 3-character passwords and the hash returned on signup -> min 8, never returned. `User_Roles` had no migration and no default role -> Flyway + `CUSTOMER`. `isAdmin` was public and read a GET body -> token-based. No way to become admin -> bootstrap admin + role endpoints.

**booking** - every error path crashed (three broken error classes: undefined variables, wrong export names) -> working exception handling. `userId` taken from the request body ("book as anyone") -> always the token's user. Seats updated by read-then-patch with no rollback; a failure left bookings stuck `InProcess` -> atomic reserve, `FAILED` / compensation, recovery job. The confirmation event was never published (only a test endpoint) -> published on confirmation, retried if RabbitMQ is down. A new RabbitMQ connection per message -> one managed `RabbitTemplate`. No cancel / get-booking (the frontend called `GET /bookings/{id}`) -> added.

**reminder** - the cron job was written but **never started**, so no reminder was ever sent -> scheduled dispatcher. Column typo `staus` vs `status` and `recepientEmail` vs `recipientEmail` broke the pending-email query and the recipient -> one consistent schema. `notificationTime` was a STRING in the model but a DATE in the migration -> real timestamp. Send errors were swallowed (the API always answered "success") -> status, attempts, retry with backoff, `FAILED` + admin retry. `SEND_BASIC_MAIL` called `sendBasicEmail` with the wrong arguments; the consumer acknowledged messages even when sending failed; queues were not durable -> durable queues, ack only after the work is saved, dead-letter queue. No protection against duplicates -> idempotent on event id. No way to react to a cancelled booking -> `BOOKING_CANCELLED` drops unsent emails.

**gateway** - one global limit of 5 requests per 2 minutes for everything (unusable) -> 20 req/s per IP and route, 1 req/s on signin/signup, in Redis. Only `/bookingservice` was proxied -> all four services, by Eureka name instead of hard-coded `localhost` URLs. Every request made an HTTP call to auth-service to check the token -> verified locally with the shared secret (no extra hop, no single point of failure). A failed auth check answered 500 -> 401/403 with the JSON envelope. Role rules did not exist -> `AccessPolicy` (and the internal seat endpoints are hidden). No CORS (the browser app could not call it) -> configured. No path hardening -> `..`, `%2f`, `;` rejected.

**everywhere** - DB credentials committed in `config.json`, no `.gitignore` -> env-based config, `.gitignore`, `.env.example`.

## Known limitations (deliberate, documented)

- **Docker:** the Dockerfiles and the `app` profile were validated for structure (YAML, service names, ports, health checks, build arguments) but not built, because there is no Docker in my build environment. A first `docker compose --profile app up --build` is the real test.
- **Frontend:** the 49 tests run against a mocked gateway, and the frontend/backend contract was checked field by field against the Java code, but
  the app has not been opened in a browser against the live backend (no browser in my build environment). Expect small visual polish items.
- The token is kept in `sessionStorage` (survives refresh, gone when the tab closes) - the usual trade-off for a pure single-page app: a cross-site-scripting
  bug could read it. React escapes output by default and the app renders no raw HTML. Signing out only forgets the token; it stays valid on the server until it expires.
- Fares are formatted with `VITE_CURRENCY` (default INR); the backend stores plain numbers with no currency.

- **Not compiled or run end-to-end.** Written in a sandbox without Maven Central. Syntax-checked with `javac` and the Lombok accessors cross-checked, but expect to fix a small compile/startup issue on the first `mvn test` / run. Paste the error and it gets fixed. Dependency versions (Spring Boot 3.4.5, Spring Cloud 2024.0.1, springdoc 2.8.6, jjwt 0.12.6) are from memory.
- The gateway protects only what goes **through** it. Locally every service port is open; in a real deployment publish only port 8080 (flights-service writes and `seats/*` have no login of their own).
- If the reserve call **times out** the booking is marked `FAILED`; in the rare case flights-service did take the seats they stay unavailable (the safe side). Reservation ids would remove this ambiguity.
- A booking left `PENDING` by a crash between steps 2 and 3 is not auto-reconciled yet.
- `BOOKING_CANCELLED` is published best-effort: if RabbitMQ is down at that moment the cancellation stands but the customer gets no cancellation email (and a pending departure reminder could still go out). `BOOKING_CONFIRMED` is retried; making the cancel event equally durable is a small follow-up.
- Emails are plain text, in English, with times in UTC.
- The dead-letter queue is inspected and re-driven by hand (RabbitMQ UI).
- Role changes reach a user's token at their next sign-in.
- Times are UTC; `departureDate` filters a UTC calendar day.
- Gateway rate limiting keys on the TCP peer address; behind a reverse proxy configure forwarded headers (see docs/ARCHITECTURE.md section 8). Rate-limit state needs Redis (fails open without it).
- A token stays valid until it expires (60 min) - there is no logout/revocation list yet. A Redis-backed deny-list in the gateway would add it.
