#!/usr/bin/env bash
# Starts the React app on http://localhost:5173 (needs Node 18+). The backend should already be running:
# the app talks only to the api-gateway (default http://localhost:8080, see frontend/.env.example).
set -euo pipefail
cd "$(dirname "$0")/../frontend"
[ -f .env ] || cp .env.example .env
[ -d node_modules ] || npm install --no-audit --no-fund
exec npm run dev
