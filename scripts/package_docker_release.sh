#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TAG="${1:-$(date +%Y%m%d-%H%M%S)}"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"
RELEASE_ROOT="$ROOT_DIR/dist/docker-release"
RELEASE_DIR="$RELEASE_ROOT/ruiwen-$TAG"
IMAGES_TAR="ruiwen-images-$TAG.tar"
RELEASE_TGZ="$RELEASE_ROOT/ruiwen-release-$TAG.tgz"

env_value() {
  local key="$1"
  local default="$2"
  local current="${!key:-}"
  local line value
  if [[ -n "$current" ]]; then
    printf '%s' "$current"
    return
  fi
  if [[ -f "$ENV_FILE" ]]; then
    line="$(grep -E "^${key}=" "$ENV_FILE" | tail -n 1 || true)"
    if [[ -n "$line" ]]; then
      value="${line#*=}"
      value="${value%\"}"
      value="${value#\"}"
      value="${value%\'}"
      value="${value#\'}"
      printf '%s' "$value"
      return
    fi
  fi
  printf '%s' "$default"
}

docker_no_proxy() {
  env -u http_proxy -u https_proxy -u HTTP_PROXY -u HTTPS_PROXY -u all_proxy -u ALL_PROXY docker "$@"
}

PLATFORM="$(env_value DOCKER_PLATFORM linux/amd64)"
BACKEND_IMAGE="$(env_value BACKEND_IMAGE ruiwen-backend)"
FRONTEND_IMAGE="$(env_value FRONTEND_IMAGE ruiwen-frontend)"
NEXT_PUBLIC_API_BASE_URL="$(env_value NEXT_PUBLIC_API_BASE_URL http://backend:8080)"

mkdir -p "$RELEASE_ROOT"
rm -rf "$RELEASE_DIR"
mkdir -p "$RELEASE_DIR"

echo "==> Build backend image: $BACKEND_IMAGE:$TAG ($PLATFORM)"
docker_no_proxy build --platform "$PLATFORM" \
  -t "$BACKEND_IMAGE:$TAG" \
  -f "$ROOT_DIR/backend/Dockerfile" \
  "$ROOT_DIR/backend"
docker_no_proxy tag "$BACKEND_IMAGE:$TAG" "$BACKEND_IMAGE:latest"

echo "==> Build frontend image: $FRONTEND_IMAGE:$TAG ($PLATFORM)"
docker_no_proxy build --platform "$PLATFORM" \
  --build-arg "NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL" \
  -t "$FRONTEND_IMAGE:$TAG" \
  -f "$ROOT_DIR/frontend/Dockerfile" \
  "$ROOT_DIR/frontend"
docker_no_proxy tag "$FRONTEND_IMAGE:$TAG" "$FRONTEND_IMAGE:latest"

echo "==> Save application images"
docker_no_proxy image save \
  -o "$RELEASE_DIR/$IMAGES_TAR" \
  "$BACKEND_IMAGE:$TAG" \
  "$BACKEND_IMAGE:latest" \
  "$FRONTEND_IMAGE:$TAG" \
  "$FRONTEND_IMAGE:latest"

echo "==> Assemble runtime bundle"
cp "$ROOT_DIR/deploy/docker-compose.runtime.yml" "$RELEASE_DIR/docker-compose.yml"
sed "s/^IMAGE_TAG=.*/IMAGE_TAG=$TAG/" "$ROOT_DIR/.env.docker.example" > "$RELEASE_DIR/.env.docker.example"
cp "$ROOT_DIR/DEPLOY_DOCKER.md" "$RELEASE_DIR/DEPLOY_DOCKER.md"

mkdir -p "$RELEASE_DIR/deploy" "$RELEASE_DIR/logs" "$RELEASE_DIR/certs/nginx"
cp -R "$ROOT_DIR/deploy/nginx" "$RELEASE_DIR/deploy/nginx"
cp -R "$ROOT_DIR/deploy/mysql" "$RELEASE_DIR/deploy/mysql"
cp -R "$ROOT_DIR/deploy/canal" "$RELEASE_DIR/deploy/canal"
cp -R "$ROOT_DIR/deploy/elasticsearch" "$RELEASE_DIR/deploy/elasticsearch"

cat > "$RELEASE_DIR/certs/nginx/README.md" <<'EOF'
Put Nginx TLS files here before starting the runtime stack:

- line68.cn_bundle.crt
- line68.cn.key

The files are mounted into the nginx container at /etc/nginx/certs.
EOF

COPYFILE_DISABLE=1 tar -C "$RELEASE_ROOT" -czf "$RELEASE_TGZ" "ruiwen-$TAG"

echo
echo "Release directory: $RELEASE_DIR"
echo "Release archive:   $RELEASE_TGZ"
echo
echo "Server bootstrap:"
echo "  tar -xzf $(basename "$RELEASE_TGZ")"
echo "  cd ruiwen-$TAG"
echo "  docker load -i $IMAGES_TAR"
echo "  cp .env.docker.example .env.docker"
echo "  docker compose --env-file .env.docker up -d"
