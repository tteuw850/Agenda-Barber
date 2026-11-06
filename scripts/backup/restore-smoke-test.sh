#!/usr/bin/env bash
# Restores ONLY to a disposable, network-isolated PostgreSQL 17 container.
set -Eeuo pipefail
umask 077
[[ "$EUID" -eq 0 ]] || { echo 'ERROR: root required' >&2; exit 1; }
[[ "$#" -eq 1 && -f "$1" ]] || { echo 'Usage: restore-smoke-test.sh /path/to/backup.dump' >&2; exit 1; }
archive="$(realpath "$1")"
name="agenda-barber-restore-qa-$$"
cleanup() { docker rm -f "$name" >/dev/null 2>&1 || true; }
trap cleanup EXIT
docker run -d --rm --network none --name "$name" \
  -e POSTGRES_PASSWORD=disposable-container-only \
  -e POSTGRES_DB=agenda_barber postgres:17 >/dev/null
ready=0
for i in $(seq 1 30); do
  if docker exec -u postgres "$name" pg_isready -U postgres -d agenda_barber >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 1
done
[[ "$ready" == 1 ]] || { echo 'ERROR: test DB failed to start' >&2; exit 1; }
docker exec -i -u postgres "$name" pg_restore \
  --no-owner --no-acl --exit-on-error -U postgres -d agenda_barber < "$archive"
version="$(docker exec -u postgres "$name" psql -X -A -t -U postgres -d agenda_barber \
  -c "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank DESC LIMIT 1")"
[[ "$version" =~ ^[0-9]+$ ]] || { echo 'ERROR: Flyway version missing' >&2; exit 1; }
echo "RESTORE_TEST_PASS migration_version=$version isolated_container=true"
