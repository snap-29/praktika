#!/usr/bin/env bash
set -euo pipefail
mkdir -p backups
file="backups/support-$(date -u +%Y%m%dT%H%M%SZ).dump"
# Binary redirect must run in bash (not Windows PowerShell 5).
docker compose exec -T db pg_dump -U support -d support -Fc > "$file"
test -s "$file"
printf '%s\n' "$file"
