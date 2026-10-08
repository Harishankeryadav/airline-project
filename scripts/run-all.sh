#!/usr/bin/env bash
# Starts infrastructure (MySQL, Redis, RabbitMQ, Mailpit) and every service in the background. Logs go to ./logs, PIDs to ./logs/pids.
# Needs: docker, JDK 21, Maven 3.9+. Stop everything with scripts/stop-all.sh
set -euo pipefail
cd "$(dirname "$0")/.."

# One shared secret for every service that signs/verifies tokens (override by exporting JWT_SECRET beforehand).
export JWT_SECRET="${JWT_SECRET:-dev-only-secret-change-me-dev-only-secret-change-me}"
export BOOTSTRAP_ADMIN_EMAIL="${BOOTSTRAP_ADMIN_EMAIL:-admin@example.com}"
export BOOTSTRAP_ADMIN_PASSWORD="${BOOTSTRAP_ADMIN_PASSWORD:-ChangeMe123!}"

mkdir -p logs
: > logs/pids

echo "==> Starting MySQL, Redis, RabbitMQ, Mailpit"
docker compose up -d
echo "==> Waiting for MySQL to accept connections"
until docker exec airline-mysql mysqladmin ping -h localhost -uroot -p"${MYSQL_ROOT_PASSWORD:-root}" --silent 2>/dev/null; do sleep 2; done

echo "==> Building (tests skipped for speed; run 'mvn test' separately)"
mvn -q -DskipTests install

start() {  # start <module> <seconds to wait afterwards>
  echo "==> Starting $1"
  nohup mvn -q -pl "$1" spring-boot:run > "logs/$1.log" 2>&1 &
  echo "$!" >> logs/pids
  sleep "$2"
}

start discovery-server 15
start flights-service 5
start auth-service 5
start booking-service 5
start reminder-service 5
start api-gateway 5

echo
echo "Started. Eureka: http://localhost:8761   Logs: ./logs/*.log"
echo "  API GATEWAY (use this one from the frontend): http://localhost:8080"
echo "  flights Swagger : http://localhost:8081/swagger-ui.html"
echo "  auth Swagger    : http://localhost:8082/swagger-ui.html   (admin: $BOOTSTRAP_ADMIN_EMAIL)"
echo "  booking Swagger : http://localhost:8083/swagger-ui.html"
echo "  reminder Swagger: http://localhost:8084/swagger-ui.html   (ADMIN only)"
echo "  Mailpit (all sent emails): http://localhost:8025    RabbitMQ UI: http://localhost:15672 (guest/guest)"
echo "  React app: run  ./scripts/run-frontend.sh  in another terminal -> http://localhost:5173"
echo "Wait ~30-60 s for all five to register, then run scripts/smoke-test.sh"
