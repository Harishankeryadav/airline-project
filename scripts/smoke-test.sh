#!/usr/bin/env bash
# End-to-end check that the services are linked: auth -> flights -> booking (-> flights seats) -> cancel,
# plus the api-gateway access rules, plus the RabbitMQ -> reminder-service -> email leg (checked in Mailpit; reported as WARN, not FAIL, if it is slow).
# Run after scripts/run-all.sh (or after starting the services yourself).
# In containers (docker compose --profile app up --build -d) run:  GATEWAY_ONLY=1 ./scripts/smoke-test.sh
set -uo pipefail

AUTH="${AUTH_URL:-http://localhost:8082}"
FLIGHTS="${FLIGHTS_URL:-http://localhost:8081}"
BOOKING="${BOOKING_URL:-http://localhost:8083}"
MAILPIT="${MAILPIT_URL:-http://localhost:8025}"
GATEWAY="${GATEWAY_URL:-http://localhost:8080}"
# GATEWAY_ONLY=1: reach every service through the gateway instead of its own port. Use this when the system runs in
# containers (docker compose --profile app up), where only the gateway's port is published on purpose.
if [ "${GATEWAY_ONLY:-0}" == 1 ]; then
  AUTH="$GATEWAY/authservice"; FLIGHTS="$GATEWAY/flightsservice"; BOOKING="$GATEWAY/bookingservice"
fi
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@example.com}"        # created by run-all.sh (BOOTSTRAP_ADMIN_*)
ADMIN_PASSWORD="${ADMIN_PASSWORD:-ChangeMe123!}"
EMAIL="smoke-$(date +%s)@example.com"
PASSWORD="password123"
FAILED=0

jget() {  # jget <dot.path>   e.g. data.token or data.0.id  (reads JSON from stdin)
  python3 -c '
import sys, json
d = json.load(sys.stdin)
for k in sys.argv[1].split("."):
    d = d[int(k)] if k.isdigit() else d[k]
print(d)' "$1"
}
check() {  # check <description> <expected> <actual>
  if [ "$2" == "$3" ]; then echo "  PASS  $1"; else echo "  FAIL  $1 (expected '$2', got '$3')"; FAILED=1; fi
}
soft() {   # soft <description> <expected> <actual>   - reports but does not fail the run
  if [ "$2" == "$3" ]; then echo "  PASS  $1"; else echo "  WARN  $1 (expected '$2', got '$3')"; fi
}
mail_count() {   # number of emails Mailpit holds for our test address ("-1" if Mailpit is not reachable)
  curl -s -G "$MAILPIT/api/v1/search" --data-urlencode "query=to:$EMAIL" 2>/dev/null | python3 -c '
import sys, json
try:
    d = json.load(sys.stdin); print(d.get("messages_count", d.get("total", 0)))
except Exception:
    print(-1)'
}
wait_for_mails() {   # wait_for_mails <how many> - polls up to 45 s; prints the final count
  local n=0 c=0
  while [ $n -lt 15 ]; do c=$(mail_count); [ "$c" -ge "$1" ] && break; [ "$c" == "-1" ] && break; sleep 3; n=$((n+1)); done
  echo "$c"
}
post() { curl -s -X POST -H 'Content-Type: application/json' "${@:2}" "$1"; }

echo "1. auth-service: signup + signin"
check "signup succeeds" True "$(post "$AUTH/api/v1/signup" -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" | jget success)"
TOKEN=$(post "$AUTH/api/v1/signin" -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" | jget data.token)
check "signin returns a token" "True" "$([ -n "$TOKEN" ] && echo True || echo False)"
check "wrong password is 401" 401 "$(curl -s -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' "$AUTH/api/v1/signin" -d "{\"email\":\"$EMAIL\",\"password\":\"nope-nope-nope\"}")"

echo "2. flights-service: search"
check "airport search finds Delhi" "True" "$(curl -s "$FLIGHTS/api/v1/airports/search?q=del" | python3 -c 'import sys,json; d=json.load(sys.stdin)["data"]; print(any(a["cityName"]=="Delhi" for a in d))')"
FLIGHT_ID=$(curl -s "$FLIGHTS/api/v1/flights?sortBy=price" | jget data.0.id)
SEATS_BEFORE=$(curl -s "$FLIGHTS/api/v1/flights/$FLIGHT_ID" | jget data.availableSeats)
echo "     using flight $FLIGHT_ID with $SEATS_BEFORE seats"

echo "3. booking-service -> flights-service (Feign): book 2 seats"
check "booking without a token is 401" 401 "$(curl -s -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' "$BOOKING/api/v1/bookings" -d "{\"flightId\":$FLIGHT_ID,\"noOfSeats\":2}")"
RESP=$(post "$BOOKING/api/v1/bookings" -H "Authorization: Bearer $TOKEN" -d "{\"flightId\":$FLIGHT_ID,\"noOfSeats\":2}")
BOOKING_ID=$(echo "$RESP" | jget data.id)
check "booking is CONFIRMED" CONFIRMED "$(echo "$RESP" | jget data.status)"
check "flights-service seats dropped by 2" $((SEATS_BEFORE - 2)) "$(curl -s "$FLIGHTS/api/v1/flights/$FLIGHT_ID" | jget data.availableSeats)"
check "booking 10 seats is rejected by validation (400)" 400 "$(curl -s -o /dev/null -w '%{http_code}' -X POST -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' "$BOOKING/api/v1/bookings" -d "{\"flightId\":$FLIGHT_ID,\"noOfSeats\":10}")"
check "my bookings lists it" "True" "$(curl -s -H "Authorization: Bearer $TOKEN" "$BOOKING/api/v1/bookings/my" | python3 -c "import sys,json; print(any(b['id']==$BOOKING_ID for b in json.load(sys.stdin)['data']))")"

echo "3b. reminder-service: confirmation email (RabbitMQ -> reminder-service -> Mailpit, may take ~15 s)"
MAILS=$(wait_for_mails 1)
if [ "$MAILS" == "-1" ]; then echo "  SKIP  Mailpit not reachable at $MAILPIT"; else soft "confirmation email delivered" True "$([ "$MAILS" -ge 1 ] && echo True || echo False)"; fi

echo "4. cancel gives the seats back"
check "cancel succeeds" CANCELLED "$(post "$BOOKING/api/v1/bookings/$BOOKING_ID/cancel" -H "Authorization: Bearer $TOKEN" | jget data.status)"
check "seats are restored" "$SEATS_BEFORE" "$(curl -s "$FLIGHTS/api/v1/flights/$FLIGHT_ID" | jget data.availableSeats)"
check "second cancel is 409" 409 "$(curl -s -o /dev/null -w '%{http_code}' -X POST -H "Authorization: Bearer $TOKEN" "$BOOKING/api/v1/bookings/$BOOKING_ID/cancel")"

echo "4b. reminder-service: cancellation email"
if [ "$MAILS" != "-1" ]; then
  MAILS=$(wait_for_mails 2)
  soft "cancellation email delivered" True "$([ "$MAILS" -ge 2 ] && echo True || echo False)"
fi

echo "5. api-gateway: the single entry point (:8080) applies the access rules"
code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
JSON='Content-Type: application/json'
check "airport search is public through the gateway" True "$(curl -s "$GATEWAY/flightsservice/api/v1/airports/search?q=del" | jget success)"
check "creating a city without a token is 401" 401 "$(code -X POST -H "$JSON" "$GATEWAY/flightsservice/api/v1/city" -d '{"name":"Nowhere"}')"
check "creating a city as a customer is 403" 403 "$(code -X POST -H "$JSON" -H "Authorization: Bearer $TOKEN" "$GATEWAY/flightsservice/api/v1/city" -d '{"name":"Nowhere"}')"
check "internal seat endpoint is hidden (404)" 404 "$(code -X POST -H "$JSON" -H "Authorization: Bearer $TOKEN" "$GATEWAY/flightsservice/api/v1/flights/$FLIGHT_ID/seats/reserve" -d '{"seats":1}')"
check "a service's actuator is hidden (404)" 404 "$(code "$GATEWAY/flightsservice/actuator/env")"
check "encoded path trick is rejected (400)" 400 "$(code -X POST -H "Authorization: Bearer $TOKEN" "$GATEWAY/flightsservice/api/v1/flights/$FLIGHT_ID/seats%2Freserve")"
check "my bookings without a token is 401" 401 "$(code "$GATEWAY/bookingservice/api/v1/bookings/my")"
check "my bookings with a token is 200" 200 "$(code -H "Authorization: Bearer $TOKEN" "$GATEWAY/bookingservice/api/v1/bookings/my")"
check "/me through the gateway is 200" 200 "$(code -H "Authorization: Bearer $TOKEN" "$GATEWAY/authservice/api/v1/me")"
check "reminder API as a customer is 403" 403 "$(code -H "Authorization: Bearer $TOKEN" "$GATEWAY/reminderservice/api/v1/tickets")"
check "CORS preflight from the React origin is answered" True "$(curl -s -o /dev/null -D - -X OPTIONS -H 'Origin: http://localhost:5173' -H 'Access-Control-Request-Method: POST' "$GATEWAY/flightsservice/api/v1/flights" | tr -d '\r' | grep -qi '^access-control-allow-origin: http://localhost:5173' && echo True || echo False)"
ADMIN_TOKEN=$(post "$GATEWAY/authservice/api/v1/signin" -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" | jget data.token 2>/dev/null || true)
if [ -n "$ADMIN_TOKEN" ]; then
  check "reminder API as ADMIN is 200" 200 "$(code -H "Authorization: Bearer $ADMIN_TOKEN" "$GATEWAY/reminderservice/api/v1/tickets")"
  CITY=$(post "$GATEWAY/flightsservice/api/v1/city" -H "Authorization: Bearer $ADMIN_TOKEN" -d "{\"name\":\"Smoke-$(date +%s)\"}")
  check "ADMIN can create a city through the gateway" True "$(echo "$CITY" | jget success)"
  CITY_ID=$(echo "$CITY" | jget data.id 2>/dev/null || echo 0)
  check "ADMIN can delete it again" 200 "$(code -X DELETE -H "Authorization: Bearer $ADMIN_TOKEN" "$GATEWAY/flightsservice/api/v1/city/$CITY_ID")"
else
  echo "  SKIP  admin checks (could not sign in as $ADMIN_EMAIL - set ADMIN_EMAIL / ADMIN_PASSWORD)"
fi

echo
if [ "$FAILED" == 0 ]; then echo "ALL CHECKS PASSED"; else echo "SOME CHECKS FAILED"; exit 1; fi
