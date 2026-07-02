#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TAG="${1:-latest}"
PLATFORM="${DOCKER_PLATFORM:-linux/amd64}"
OUT_DIR="$ROOT_DIR/dist/deploy-hook"
IMAGE="${DEPLOY_HOOK_IMAGE:-ruiwen-deploy-hook}:$TAG"
DOCKER_CLI_IMAGE="${DOCKER_CLI_IMAGE:-docker:27-cli}"
ALPINE_MIRROR="${ALPINE_MIRROR:-https://dl-cdn.alpinelinux.org/alpine}"

docker_no_proxy() {
  env -u http_proxy -u https_proxy -u HTTP_PROXY -u HTTPS_PROXY -u all_proxy -u ALL_PROXY docker "$@"
}

mkdir -p "$OUT_DIR"

echo "==> Build deploy hook image: $IMAGE ($PLATFORM)"
docker_no_proxy build --platform "$PLATFORM" \
  --build-arg "DOCKER_CLI_IMAGE=$DOCKER_CLI_IMAGE" \
  --build-arg "ALPINE_MIRROR=$ALPINE_MIRROR" \
  -t "$IMAGE" \
  -f "$ROOT_DIR/deploy/hook/Dockerfile" \
  "$ROOT_DIR"

if [[ "$TAG" != "latest" ]]; then
  docker_no_proxy tag "$IMAGE" "${DEPLOY_HOOK_IMAGE:-ruiwen-deploy-hook}:latest"
fi

echo "==> Save deploy hook image"
docker_no_proxy image save -o "$OUT_DIR/ruiwen-deploy-hook-$TAG.tar" "$IMAGE"

cp "$ROOT_DIR/deploy/hook/docker-compose.yml" "$OUT_DIR/docker-compose.deploy-hook.yml"
cp "$ROOT_DIR/deploy/hook/deploy-hook.env.example" "$OUT_DIR/deploy-hook.env.example"

echo
echo "Deploy hook package:"
echo "  $OUT_DIR/ruiwen-deploy-hook-$TAG.tar"
echo "  $OUT_DIR/docker-compose.deploy-hook.yml"
echo "  $OUT_DIR/deploy-hook.env.example"
