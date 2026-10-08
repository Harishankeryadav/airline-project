#!/usr/bin/env bash
# Stops the services started by run-all.sh (infrastructure containers keep running; 'docker compose down' stops those).
cd "$(dirname "$0")/.."
if [ -f logs/pids ]; then
  while read -r pid; do
    [ -n "$pid" ] && kill "$pid" 2>/dev/null && echo "stopped $pid"
  done < logs/pids
  : > logs/pids
fi
# mvn spring-boot:run starts a child JVM - make sure those are gone too
pkill -f 'com.airline' 2>/dev/null || true
echo "done"
