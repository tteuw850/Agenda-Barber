#!/usr/bin/env bash
set -Eeuo pipefail
sha="${1:?Short Git SHA required}"
[[ "$sha" =~ ^[a-f0-9]{7}$ ]] || { echo 'Invalid SHA' >&2; exit 2; }
dir=/opt/agenda-barber-dev/deploy
compose="$dir/compose.yaml"
archive="/tmp/agenda-barber-$sha.tar.gz"
new="agenda-barber-dev:aws-$sha"
backup="$dir/compose.before-$sha.yaml"
cd "$dir"
test -r "$archive"
test -f "$dir/.env"
test ! -e "$backup"
sudo docker load -i "$archive"
sudo docker image inspect "$new" > /dev/null
cp -p "$compose" "$backup"
old="$(awk '/^[[:space:]]*image: agenda-barber-dev:/{print $2;exit}' "$compose")"
[[ "$old" =~ ^agenda-barber-dev:aws(-[a-f0-9]{7})?$ ]] || exit 3
rollback() {
  echo 'AWS new image unhealthy: restoring previous container' >&2
  cp "$backup" "$compose"
  sudo docker compose -f "$compose" --env-file "$dir/.env" \
    --project-directory "$dir" up -d --no-deps --no-build api
}
sed -i "s#image: $old#image: $new#" "$compose"
if ! sudo docker compose -f "$compose" --env-file "$dir/.env" \
  --project-directory "$dir" up -d --no-deps --no-build api; then
  rollback
  exit 4
fi
ready=0
for i in $(seq 1 75); do
  if curl -fsS --max-time 4 http://127.0.0.1:8080/actuator/health \
    | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"'; then
    ready=1
    break
  fi
  sleep 3
done
if [ "$ready" -ne 1 ]; then rollback; exit 5; fi
rm -f "$archive" "/tmp/agenda-barber-release-$sha.sh"
echo "AWS DEV live: $new"
