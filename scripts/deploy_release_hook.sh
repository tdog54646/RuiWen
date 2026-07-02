#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TAG="${1:-$(date +%Y%m%d-%H%M%S)}"
DEPLOY_ENV_FILE="${DEPLOY_ENV_FILE:-$ROOT_DIR/.env.deploy}"

if [[ -f "$DEPLOY_ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$DEPLOY_ENV_FILE"
  set +a
fi

HOOK_URL="${DEPLOY_HOOK_URL:-http://localhost:8989/deploy/upload}"
SECRET="${DEPLOY_SECRET:-}"
RELEASE_TGZ="$ROOT_DIR/dist/docker-release/ruiwen-release-$TAG.tgz"

usage() {
  cat <<'EOF'
Usage:
  scripts/deploy_release_hook.sh [TAG]

Environment:
  .env.deploy       Local private deploy settings.
  DEPLOY_SECRET     Required. Must match the server deploy hook secret.
  DEPLOY_HOOK_URL   Default: http://localhost:8989/deploy/upload
  SKIP_BUILD=1      Upload an existing dist/docker-release/ruiwen-release-TAG.tgz.
EOF
}

if [[ "$TAG" == "-h" || "$TAG" == "--help" ]]; then
  usage
  exit 0
fi

if [[ -z "$SECRET" ]]; then
  echo "DEPLOY_SECRET is required. Put it in $DEPLOY_ENV_FILE or export it." >&2
  usage >&2
  exit 2
fi

if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 is required to sign deploy request." >&2
  exit 1
fi

cd "$ROOT_DIR"

if [[ "${SKIP_BUILD:-0}" != "1" ]]; then
  ./scripts/package_docker_release.sh "$TAG"
fi

if [[ ! -f "$RELEASE_TGZ" ]]; then
  echo "Release archive not found: $RELEASE_TGZ" >&2
  exit 1
fi

if tar -tf "$RELEASE_TGZ" | grep -qE '(^|/)\.env(\.docker|\.deploy)?$'; then
  echo "Release archive includes a private env file; aborted." >&2
  exit 1
fi

SHA256="$(python3 - "$RELEASE_TGZ" <<'PY'
import hashlib
import sys

h = hashlib.sha256()
with open(sys.argv[1], "rb") as f:
    for chunk in iter(lambda: f.read(1024 * 1024), b""):
        h.update(chunk)
print(h.hexdigest())
PY
)"

TIMESTAMP="$(date +%s)"
MESSAGE="$TIMESTAMP.$TAG.$SHA256"
SIGNATURE="$(DEPLOY_SECRET_VALUE="$SECRET" DEPLOY_MESSAGE="$MESSAGE" python3 - <<'PY'
import hashlib
import hmac
import os

secret = os.environ["DEPLOY_SECRET_VALUE"].encode("utf-8")
message = os.environ["DEPLOY_MESSAGE"].encode("utf-8")
print("sha256=" + hmac.new(secret, message, hashlib.sha256).hexdigest())
PY
)"

RELEASE_BYTES="$(stat -f%z "$RELEASE_TGZ" 2>/dev/null || stat -c%s "$RELEASE_TGZ")"
RESPONSE_FILE="$(mktemp /tmp/ruiwen-deploy-response.XXXXXX.json)"
trap 'rm -f "$RESPONSE_FILE"' EXIT

echo "==> Upload and deploy via hook"
echo "url: $HOOK_URL"
echo "tag: $TAG"
echo "sha256: $SHA256"

curl \
  --fail-with-body \
  --connect-timeout 10 \
  -X POST "$HOOK_URL" \
  -H "Content-Type: application/gzip" \
  -H "Content-Length: $RELEASE_BYTES" \
  -H "X-Deploy-Tag: $TAG" \
  -H "X-Deploy-Sha256: $SHA256" \
  -H "X-Deploy-Timestamp: $TIMESTAMP" \
  -H "X-Deploy-Signature: $SIGNATURE" \
  --data-binary "@$RELEASE_TGZ" \
  -o "$RESPONSE_FILE"

python3 - "$RESPONSE_FILE" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8", errors="replace") as f:
    data = json.load(f)

if not data.get("ok"):
    print("Deploy failed: " + str(data.get("error", "unknown error")))
    if data.get("log_tail"):
        print(data["log_tail"])
    raise SystemExit(1)

print("Deploy succeeded: tag=" + str(data.get("tag", "")))
if data.get("log"):
    print("log: " + data["log"])
PY
