#!/usr/bin/env python3
from __future__ import annotations

import fcntl
import hashlib
import hmac
import json
import os
import posixpath
import re
import shutil
import subprocess
import tempfile
import time
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any


HOST = os.getenv("DEPLOY_BIND", "0.0.0.0")
PORT = int(os.getenv("DEPLOY_PORT", "8989"))
DEPLOY_SECRET = os.getenv("DEPLOY_SECRET", "")
APP_DIR = Path(os.getenv("RUIWEN_DIR", "/opt/ruiwen"))
RELEASES_DIR = Path(os.getenv("RELEASES_DIR", str(APP_DIR / "releases")))
LOCK_FILE = Path(os.getenv("DEPLOY_LOCK_FILE", "/tmp/ruiwen-deploy.lock"))
MAX_UPLOAD_BYTES = int(os.getenv("MAX_UPLOAD_BYTES", str(4 * 1024 * 1024 * 1024)))
SIGNATURE_TTL_SECONDS = int(os.getenv("SIGNATURE_TTL_SECONDS", "600"))
DEPLOY_SERVICES = os.getenv("DEPLOY_SERVICES", "backend frontend nginx").split()
KEEP_RELEASES = int(os.getenv("KEEP_RELEASES", "3"))
TAG_RE = re.compile(r"^[0-9A-Za-z._-]{1,80}$")


class DeployError(Exception):
    def __init__(self, status: HTTPStatus, message: str) -> None:
        super().__init__(message)
        self.status = status
        self.message = message


def json_response(handler: BaseHTTPRequestHandler, status: HTTPStatus, payload: dict[str, Any]) -> None:
    body = json.dumps(payload, ensure_ascii=False, indent=2).encode("utf-8")
    handler.send_response(status.value)
    handler.send_header("Content-Type", "application/json; charset=utf-8")
    handler.send_header("Content-Length", str(len(body)))
    handler.end_headers()
    handler.wfile.write(body)


def header(handler: BaseHTTPRequestHandler, name: str) -> str:
    return (handler.headers.get(name) or "").strip()


def verify_headers(handler: BaseHTTPRequestHandler) -> tuple[str, str, int]:
    if not DEPLOY_SECRET:
        raise DeployError(HTTPStatus.INTERNAL_SERVER_ERROR, "DEPLOY_SECRET is not configured")

    tag = header(handler, "X-Deploy-Tag")
    expected_sha = header(handler, "X-Deploy-Sha256").lower()
    timestamp_raw = header(handler, "X-Deploy-Timestamp")
    signature = header(handler, "X-Deploy-Signature")

    if not TAG_RE.fullmatch(tag):
        raise DeployError(HTTPStatus.BAD_REQUEST, "invalid X-Deploy-Tag")
    if not re.fullmatch(r"[0-9a-f]{64}", expected_sha):
        raise DeployError(HTTPStatus.BAD_REQUEST, "invalid X-Deploy-Sha256")

    try:
        timestamp = int(timestamp_raw)
    except ValueError as exc:
        raise DeployError(HTTPStatus.BAD_REQUEST, "invalid X-Deploy-Timestamp") from exc

    now = int(time.time())
    if abs(now - timestamp) > SIGNATURE_TTL_SECONDS:
        raise DeployError(HTTPStatus.UNAUTHORIZED, "deploy signature timestamp expired")

    message = f"{timestamp}.{tag}.{expected_sha}".encode("utf-8")
    expected_sig = "sha256=" + hmac.new(
        DEPLOY_SECRET.encode("utf-8"),
        message,
        hashlib.sha256,
    ).hexdigest()
    if not hmac.compare_digest(signature, expected_sig):
        raise DeployError(HTTPStatus.UNAUTHORIZED, "invalid deploy signature")

    try:
        content_length = int(header(handler, "Content-Length"))
    except ValueError as exc:
        raise DeployError(HTTPStatus.LENGTH_REQUIRED, "Content-Length is required") from exc
    if content_length <= 0:
        raise DeployError(HTTPStatus.BAD_REQUEST, "empty upload")
    if content_length > MAX_UPLOAD_BYTES:
        raise DeployError(HTTPStatus.REQUEST_ENTITY_TOO_LARGE, "upload is too large")

    return tag, expected_sha, content_length


def save_upload(handler: BaseHTTPRequestHandler, tag: str, expected_sha: str, content_length: int) -> Path:
    RELEASES_DIR.mkdir(parents=True, exist_ok=True)
    temp_path = RELEASES_DIR / f".upload-{tag}-{int(time.time())}.tgz.part"
    hasher = hashlib.sha256()
    remaining = content_length

    with temp_path.open("wb") as file:
        while remaining > 0:
            chunk = handler.rfile.read(min(1024 * 1024, remaining))
            if not chunk:
                temp_path.unlink(missing_ok=True)
                raise DeployError(HTTPStatus.BAD_REQUEST, "upload ended early")
            file.write(chunk)
            hasher.update(chunk)
            remaining -= len(chunk)

    actual_sha = hasher.hexdigest()
    if actual_sha != expected_sha:
        temp_path.unlink(missing_ok=True)
        raise DeployError(HTTPStatus.BAD_REQUEST, f"sha256 mismatch: {actual_sha}")

    final_path = RELEASES_DIR / f"ruiwen-release-{tag}-{actual_sha[:12]}.tgz"
    temp_path.replace(final_path)
    return final_path


def run(cmd: list[str], *, cwd: Path, log_file: Path) -> None:
    with log_file.open("a", encoding="utf-8") as log:
        log.write(f"\n$ {' '.join(cmd)}\n")
        log.flush()
        proc = subprocess.run(
            cmd,
            cwd=str(cwd),
            text=True,
            stdout=log,
            stderr=subprocess.STDOUT,
            check=False,
        )
        if proc.returncode != 0:
            raise DeployError(HTTPStatus.INTERNAL_SERVER_ERROR, f"command failed: {' '.join(cmd)}")


def tail(path: Path, max_chars: int = 6000) -> str:
    if not path.exists():
        return ""
    data = path.read_bytes()
    return data[-max_chars:].decode("utf-8", errors="replace")


def validate_release_archive(release_path: Path) -> None:
    proc = subprocess.run(
        ["tar", "-tzf", str(release_path)],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if proc.returncode != 0:
        raise DeployError(HTTPStatus.BAD_REQUEST, f"invalid release archive: {proc.stderr.strip()}")

    for raw_entry in proc.stdout.splitlines():
        entry = raw_entry.strip()
        normalized = posixpath.normpath(entry)
        parts = [part for part in normalized.split("/") if part]
        if entry.startswith("/") or ".." in parts:
            raise DeployError(HTTPStatus.BAD_REQUEST, f"unsafe tar entry: {entry}")
        if parts and parts[-1] in {".env", ".env.docker", ".env.deploy"}:
            raise DeployError(HTTPStatus.BAD_REQUEST, f"release archive includes private env file: {parts[-1]}")


def deploy_release(release_path: Path, tag: str) -> Path:
    timestamp = time.strftime("%Y%m%d-%H%M%S")
    log_dir = RELEASES_DIR / "logs"
    log_dir.mkdir(parents=True, exist_ok=True)
    log_file = log_dir / f"deploy-{tag}-{timestamp}.log"

    APP_DIR.mkdir(parents=True, exist_ok=True)
    env_path = APP_DIR / ".env.docker"
    if not env_path.exists():
        raise DeployError(HTTPStatus.INTERNAL_SERVER_ERROR, f"{env_path} does not exist")

    with log_file.open("a", encoding="utf-8") as log:
        log.write(f"release={release_path}\ntag={tag}\napp_dir={APP_DIR}\n")

    validate_release_archive(release_path)

    env_backup = Path(tempfile.mkstemp(prefix="ruiwen-env-", suffix=".docker")[1])
    shutil.copy2(env_path, env_backup)
    try:
        run(["tar", "-xzf", str(release_path), "--strip-components=1", "-C", str(APP_DIR)], cwd=APP_DIR, log_file=log_file)
        shutil.copy2(env_backup, env_path)

        images_tar = APP_DIR / f"ruiwen-images-{tag}.tar"
        if not images_tar.exists():
            raise DeployError(HTTPStatus.INTERNAL_SERVER_ERROR, f"image tar not found: {images_tar}")

        run(["docker", "load", "-i", str(images_tar)], cwd=APP_DIR, log_file=log_file)
        images_tar.unlink(missing_ok=True)
        run(
            ["docker", "compose", "--env-file", ".env.docker", "up", "-d", "--force-recreate", *DEPLOY_SERVICES],
            cwd=APP_DIR,
            log_file=log_file,
        )
        run(["docker", "compose", "--env-file", ".env.docker", "ps"], cwd=APP_DIR, log_file=log_file)
    finally:
        shutil.copy2(env_backup, env_path)
        env_backup.unlink(missing_ok=True)

    prune_old_releases()
    return log_file


def prune_old_releases() -> None:
    tarballs = sorted(RELEASES_DIR.glob("ruiwen-release-*.tgz"), key=lambda p: p.stat().st_mtime)
    old_items = tarballs[:-KEEP_RELEASES] if KEEP_RELEASES > 0 else tarballs
    for old in old_items:
        try:
            old.unlink()
        except OSError:
            pass


class DeployHandler(BaseHTTPRequestHandler):
    server_version = "RuiWenDeployHook/1.0"

    def log_message(self, fmt: str, *args: Any) -> None:
        print(f"{time.strftime('%Y-%m-%d %H:%M:%S')} {self.client_address[0]} {fmt % args}", flush=True)

    def do_GET(self) -> None:
        if self.path == "/health":
            json_response(self, HTTPStatus.OK, {"ok": True})
            return
        json_response(self, HTTPStatus.NOT_FOUND, {"ok": False, "error": "not found"})

    def do_POST(self) -> None:
        if self.path != "/deploy/upload":
            json_response(self, HTTPStatus.NOT_FOUND, {"ok": False, "error": "not found"})
            return

        LOCK_FILE.parent.mkdir(parents=True, exist_ok=True)
        with LOCK_FILE.open("w") as lock:
            try:
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError:
                json_response(self, HTTPStatus.CONFLICT, {"ok": False, "error": "deployment already running"})
                return

            release_path: Path | None = None
            try:
                tag, expected_sha, content_length = verify_headers(self)
                release_path = save_upload(self, tag, expected_sha, content_length)
                log_file = deploy_release(release_path, tag)
                json_response(
                    self,
                    HTTPStatus.OK,
                    {
                        "ok": True,
                        "tag": tag,
                        "release": str(release_path),
                        "log": str(log_file),
                        "log_tail": tail(log_file),
                    },
                )
            except DeployError as exc:
                payload: dict[str, Any] = {"ok": False, "error": exc.message}
                if release_path:
                    payload["release"] = str(release_path)
                json_response(self, exc.status, payload)
            except Exception as exc:
                json_response(self, HTTPStatus.INTERNAL_SERVER_ERROR, {"ok": False, "error": repr(exc)})


def main() -> None:
    server = ThreadingHTTPServer((HOST, PORT), DeployHandler)
    print(f"RuiWen deploy hook listening on {HOST}:{PORT}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
