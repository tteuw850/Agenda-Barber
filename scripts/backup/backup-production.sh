#!/usr/bin/env bash
# Read-only production dump. No Neon branch or data is modified.
set -Eeuo pipefail
umask 077
[[ "$EUID" -eq 0 ]] || { echo 'ERROR: root required' >&2; exit 1; }
APP_ENV=/opt/agenda-barber-prod/deploy/.env
BACKUP_CONFIG=/etc/agenda-barber-backup.conf
BACKUP_DIR=/var/backups/agenda-barber-prod
[[ -r "$APP_ENV" ]] || { echo 'ERROR: prod env missing' >&2; exit 1; }
for tool in docker flock mktemp sha256sum stat; do
  command -v "$tool" >/dev/null || { echo "ERROR: $tool missing" >&2; exit 1; }
done
exec 9>/run/lock/agenda-barber-backup.lock
flock -n 9 || { echo 'ERROR: backup already running' >&2; exit 1; }

set +x
set -a
# shellcheck source=/dev/null
source "$APP_ENV"
set +a
[[ "${NEON_DB_USERNAME:-}" == agenda_app_prod ]] || { echo 'ERROR: role mismatch' >&2; exit 1; }
[[ -n "${NEON_DB_PASSWORD:-}" && "${NEON_JDBC_URL:-}" == jdbc:postgresql://* ]] || { echo 'ERROR: DB config missing' >&2; exit 1; }
url="${NEON_JDBC_URL#jdbc:postgresql://}"
pg_host="${url%%/*}"
pg_host="${pg_host%%:*}"
pg_host="${pg_host/-pooler./.}"
pg_database="${url#*/}"
pg_database="${pg_database%%\?*}"
[[ "$pg_host" == *.neon.tech && "$pg_database" == agenda_barber ]] || { echo 'ERROR: DB target mismatch' >&2; exit 1; }

install -d -m 0700 "$BACKUP_DIR"
name="agenda-barber-prod-$(date -u +%Y%m%dT%H%M%SZ).dump"
partial="$(mktemp "$BACKUP_DIR/.${name}.XXXXXX.part")"
trap 'rm -f -- "${partial:-}"' EXIT

# Pass variable NAMES into Docker, never password values in arguments.
export PGHOST="$pg_host" PGUSER="$NEON_DB_USERNAME" \
  PGPASSWORD="$NEON_DB_PASSWORD" PGDATABASE="$pg_database" \
  PGSSLMODE=verify-full PGAPPNAME=agenda_barber_backup
docker run --rm --network host \
  -e PGHOST -e PGUSER -e PGPASSWORD -e PGDATABASE -e PGSSLMODE -e PGAPPNAME \
  postgres:17 pg_dump --no-owner --no-acl --format=custom --compress=6 > "$partial"
[[ -s "$partial" ]] || { echo 'ERROR: empty backup' >&2; exit 1; }
docker run --rm --network none \
  --mount "type=bind,source=$BACKUP_DIR,target=/backup,readonly" \
  postgres:17 pg_restore --list "/backup/$(basename "$partial")" >/dev/null
mv -- "$partial" "$BACKUP_DIR/$name"
trap - EXIT
chmod 0600 "$BACKUP_DIR/$name"
(cd "$BACKUP_DIR"; sha256sum "$name" > "$name.sha256")
echo "LOCAL_BACKUP_VERIFIED name=$name bytes=$(stat -c %s "$BACKUP_DIR/$name")"

# An S3 copy is mandatory before this job reports overall success.
[[ -r "$BACKUP_CONFIG" ]] || { echo 'OFFSITE_PENDING: local dump retained' >&2; exit 2; }
# shellcheck source=/dev/null
source "$BACKUP_CONFIG"
: "${AGENDA_BACKUP_S3_BUCKET:?S3 bucket missing}"
AGENDA_BACKUP_S3_PREFIX="${AGENDA_BACKUP_S3_PREFIX:-agenda-barber-prod}"
[[ "$AGENDA_BACKUP_S3_BUCKET" =~ ^[a-z0-9][a-z0-9.-]*$ ]] || { echo 'ERROR: invalid S3 bucket' >&2; exit 1; }
[[ "$AGENDA_BACKUP_S3_PREFIX" =~ ^[a-zA-Z0-9/_-]+$ ]] || { echo 'ERROR: invalid S3 prefix' >&2; exit 1; }
command -v aws >/dev/null || { echo 'OFFSITE_PENDING: AWS CLI missing; local dump retained' >&2; exit 2; }
key="${AGENDA_BACKUP_S3_PREFIX%/}/$name"
aws s3 cp --only-show-errors --sse AES256 "$BACKUP_DIR/$name" "s3://$AGENDA_BACKUP_S3_BUCKET/$key"
aws s3 cp --only-show-errors --sse AES256 "$BACKUP_DIR/$name.sha256" "s3://$AGENDA_BACKUP_S3_BUCKET/$key.sha256"
remote_size="$(aws s3api head-object --bucket "$AGENDA_BACKUP_S3_BUCKET" --key "$key" --query ContentLength --output text)"
[[ "$remote_size" == "$(stat -c %s "$BACKUP_DIR/$name")" ]] || { echo 'ERROR: S3 size mismatch' >&2; exit 1; }
echo "OFFSITE_BACKUP_VERIFIED name=$name bytes=$remote_size"
# Never delete old backups automatically.
