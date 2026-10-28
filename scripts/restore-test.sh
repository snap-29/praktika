#!/usr/bin/env bash
set -euo pipefail
: "${1:?Usage: bash scripts/restore-test.sh backups/file.dump}"
docker compose exec -T db dropdb -U support --if-exists support_restore
docker compose exec -T db createdb -U support support_restore
docker compose exec -T db pg_restore -U support -d support_restore --exit-on-error < "$1"
docker compose exec -T db psql -U support -d support_restore -c 'SELECT count(*) AS restored_tickets FROM tickets'
