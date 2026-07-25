#!/bin/sh
set -eu

INFO='\033[1;34m[INFO]\033[0m'
WARN='\033[1;33m[WARN]\033[0m'

log_info() {
  printf '%b %s\n' "$INFO" "$*"
}

log_warn() {
  printf '%b %s\n' "$WARN" "$*"
}

VENV_DIR="${VENV_DIR:-/opt/venv}"
REQ_FILE="${REQ_FILE:-/app/requirements.txt}"
STAMP_FILE="$VENV_DIR/.requirements.sha256"
REQ_HASH="$(sha256sum "$REQ_FILE" | awk '{print $1}')"

if ! command -v rg >/dev/null 2>&1; then
  log_info "Installing OS dependencies"
  export DEBIAN_FRONTEND=noninteractive
  for i in 1 2 3; do
    if apt-get update && apt-get install -y --no-install-recommends ripgrep; then
      break
    fi
    log_warn "apt-get attempt $i failed, retrying in 3s..."
    sleep 3
  done
  rm -rf /var/lib/apt/lists/*
else
  log_info "OS dependencies already available"
fi

if [ ! -x "$VENV_DIR/bin/python" ]; then
  log_info "Creating virtual environment at $VENV_DIR"
  python -m venv "$VENV_DIR"
fi

. "$VENV_DIR/bin/activate"

if [ ! -f "$STAMP_FILE" ] || [ "$(cat "$STAMP_FILE")" != "$REQ_HASH" ]; then
  log_info "Installing Python dependencies"
  pip install --no-cache-dir --upgrade pip
  pip install --no-cache-dir -r "$REQ_FILE" watchfiles
  printf '%s' "$REQ_HASH" > "$STAMP_FILE"
else
  log_warn "Python dependencies already match requirements.txt"
fi

exec "$@"
