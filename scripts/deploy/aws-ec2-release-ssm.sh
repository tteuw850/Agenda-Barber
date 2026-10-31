#!/usr/bin/env bash
# Executed by AWS-RunRemoteScript after SSM Agent downloads the private S3 release folder.
# No SSH, long-lived AWS credentials or AWS CLI on EC2 are needed.
set -Eeuo pipefail
sha="${1:?7-char Git SHA required}"
expected_digest="${2:?64-char SHA-256 required}"
[[ "$sha" =~ ^[a-f0-9]{7}$ ]] || { echo "Invalid release SHA" >&2; exit 2; }
[[ "$expected_digest" =~ ^[a-f0-9]{64}$ ]] || { echo "Invalid SHA-256" >&2; exit 2; }

# AWS-RunRemoteScript runs the command inside the downloaded script directory.
archive="./agenda-barber-image.tar.gz"
release="./aws-ec2-release.sh"
test -f "$archive"
test -f "$release"
printf '%s  %s\n' "$expected_digest" "$archive" | sha256sum --check --status || {
  echo "Downloaded Docker image checksum mismatch; refusing deployment." >&2
  exit 3
}

# The existing rollback script expects this fixed temporary archive path.
target="/tmp/agenda-barber-$sha.tar.gz"
install -m 600 "$archive" "$target"
bash "$release" "$sha"
