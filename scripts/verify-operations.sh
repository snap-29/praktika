#!/usr/bin/env bash
set -euo pipefail
# Run only against a disposable verification environment, after migrations.
id="$(docker compose exec -T db psql -U support -d support -Atc "SELECT id FROM tickets ORDER BY created_at LIMIT 1" | tr -d '\r')"
test -n "$id"
count_before="$(docker compose exec -T db psql -U support -d support -Atc 'SELECT count(*) FROM tickets' | tr -d '\r')"
docker compose down
docker compose up -d --no-build --wait --wait-timeout 180
curl -fsS "http://localhost:8080/api/tickets/$id" >/dev/null
count_after="$(docker compose exec -T db psql -U support -d support -Atc 'SELECT count(*) FROM tickets' | tr -d '\r')"
test "$count_before" = "$count_after"
backup="$(bash scripts/backup.sh)"
bash scripts/restore-test.sh "$backup"
restored="$(docker compose exec -T db psql -U support -d support_restore -Atc 'SELECT count(*) FROM tickets' | tr -d '\r')"
test "$restored" = "$count_before"
for table in tickets status_history categories; do
  original="$(docker compose exec -T db psql -U support -d support -Atc "SELECT md5(coalesce(string_agg(row_to_json(t)::text, ',' ORDER BY id),'')) FROM $table t" | tr -d '\r')"
  copy="$(docker compose exec -T db psql -U support -d support_restore -Atc "SELECT md5(coalesce(string_agg(row_to_json(t)::text, ',' ORDER BY id),'')) FROM $table t" | tr -d '\r')"
  test "$original" = "$copy"
done
printf 'PASS restart persistence and backup restore: %s tickets\n' "$restored"
