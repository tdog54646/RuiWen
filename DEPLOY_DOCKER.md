# RuiWen Docker Deploy

## Directory Layout

- `backend/`: Spring Boot backend.
- `frontend/`: Next.js frontend.
- `deploy/`: runtime config for nginx, MySQL init SQL, Canal, Elasticsearch IK, and deploy hook.
- `scripts/`: release packaging and hook upload scripts.
- `.env`: local private Docker env file, ignored by git.
- `.env.example`: local compose template.
- `.env.docker.example`: server runtime template used by release bundles.

## Local One-command Run

```bash
cp .env.example .env
docker compose --env-file .env up -d --build
```

Default URLs:

- Frontend: `https://localhost`
- Backend: `http://localhost:8080`
- MySQL: `127.0.0.1:3306`
- Redis: `127.0.0.1:6379`
- Elasticsearch: `127.0.0.1:9201`

## Nginx HTTPS Certificate

The Nginx container mounts local TLS files from `certs/nginx/` to
`/etc/nginx/certs`.

Expected files:

- `certs/nginx/line68.cn_bundle.crt`
- `certs/nginx/line68.cn.key`

These real certificate files are ignored by git. In a release bundle, create or
fill the same `certs/nginx/` directory on the server before starting nginx.

Kafka UI is optional:

```bash
docker compose --env-file .env --profile tools up -d kafka-ui
```

## Build Release Bundle

```bash
scripts/package_docker_release.sh 20260702
```

Output:

- `dist/docker-release/ruiwen-20260702/`
- `dist/docker-release/ruiwen-release-20260702.tgz`

The release bundle includes backend/frontend images and runtime files. It does
not include `.env`, `.env.docker`, or `.env.deploy`.

## First Server Bootstrap Without Hook

```bash
tar -xzf ruiwen-release-20260702.tgz
cd ruiwen-20260702
docker load -i ruiwen-images-20260702.tar
cp .env.docker.example .env.docker
vim .env.docker
mkdir -p certs/nginx
# copy line68.cn_bundle.crt and line68.cn.key into certs/nginx/
docker compose --env-file .env.docker up -d
```

## Deploy Hook

Build a hook image package locally:

```bash
scripts/package_deploy_hook.sh latest
```

On the server:

```bash
docker load -i ruiwen-deploy-hook-latest.tar
cp deploy-hook.env.example deploy-hook.env
vim deploy-hook.env
docker compose --env-file deploy-hook.env -f docker-compose.deploy-hook.yml up -d
```

Before the first hook deployment, create `/opt/ruiwen/.env.docker`:

```bash
mkdir -p /opt/ruiwen
cp .env.docker.example /opt/ruiwen/.env.docker
mkdir -p /opt/ruiwen/certs/nginx
# copy line68.cn_bundle.crt and line68.cn.key into /opt/ruiwen/certs/nginx/
vim /opt/ruiwen/.env.docker
```

Then deploy from your local machine:

```bash
cp .env.deploy.example .env.deploy
vim .env.deploy
scripts/deploy_release_hook.sh 20260702
```

The upload is signed with `DEPLOY_SECRET`. The hook verifies timestamp, HMAC,
sha256, tar path safety, then loads the Docker images and restarts the runtime
services with `docker compose`.
