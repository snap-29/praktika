#!/usr/bin/env bash
set -euo pipefail
base="${BASE_URL:-http://localhost:8080}"
for attempt in $(seq 1 60); do
  if curl -fsS "$base/actuator/health/readiness" >/dev/null; then break; fi
  sleep 2
done
curl -fsS "$base/actuator/health/readiness" >/dev/null
python3 scripts/smoke.py "$base"
