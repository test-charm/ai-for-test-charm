#!/bin/sh
set -eu

GREEN='\033[1;32m[OK]\033[0m'
INFO='\033[1;34m[INFO]\033[0m'

log_info()  { printf '%b %s\n' "$INFO" "$*"; }
log_ok()    { printf '%b %s\n' "$GREEN" "$*"; }

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
COVERAGE_DIR="$SCRIPT_DIR/coverage-output"

if [ ! -d "$COVERAGE_DIR" ]; then
  echo "ERROR: coverage-output/ directory not found."
  exit 1
fi

DATA_FILES="$(find "$COVERAGE_DIR" -maxdepth 1 -name '.coverage-*' -type f 2>/dev/null || true)"
if [ -z "$DATA_FILES" ]; then
  echo "ERROR: No .coverage-* data files found in $COVERAGE_DIR"
  echo "       Run the e2e tests first."
  exit 1
fi

log_info "Found coverage data files:"
echo "$DATA_FILES" | while read -r f; do echo "  $(basename "$f")"; done

# Use any running container, or start a temporary one for the combine step
find_container() {
  for name in \
    "code-qa-agent-e2e-code-qa-agent-1" \
    "code-qa-agent-e2e-code-qa-agent-deepseek-1" \
    "code-qa-agent-e2e-code-qa-agent-anthropic-1"; do
    if docker inspect "$name" --format '{{.State.Running}}' 2>/dev/null | grep -q true; then
      echo "$name"
      return 0
    fi
  done
  return 1
}

cleanup_tmp() {
  if [ -n "${TMP_CONTAINER:-}" ]; then
    docker rm -f "$TMP_CONTAINER" 2>/dev/null || true
  fi
}
trap cleanup_tmp EXIT

RUNNER=$(find_container || true)
if [ -z "$RUNNER" ]; then
  log_info "No block-club container running, starting temporary one..."
  TMP_CONTAINER="coverage-collector-tmp-$$"
  docker run -d --rm --name "$TMP_CONTAINER" \
    -v "$(dirname "$SCRIPT_DIR"):/app" \
    python:3.12-slim \
    tail -f /dev/null
  docker exec "$TMP_CONTAINER" sh -c '
    python -m venv /opt/venv 2>/dev/null
    /opt/venv/bin/pip install coverage -q
  '
  RUNNER="$TMP_CONTAINER"
  log_info "Using temporary container: $TMP_CONTAINER"
else
  log_info "Using running container: $RUNNER"
fi

# Combine ALL .coverage-* files (chainlit + mcp, from all profiles)
log_info "Combining all coverage data..."
docker exec "$RUNNER" sh -c \
  'rm -f /app/.coverage && /opt/venv/bin/coverage combine --keep /app/coverage/.coverage-*'

log_info "Coverage summary:"
docker exec "$RUNNER" sh -c \
  '/opt/venv/bin/coverage report -m'

log_info "Generating HTML report..."
docker exec "$RUNNER" sh -c \
  '/opt/venv/bin/coverage html -d /app/coverage/html'

log_ok "HTML report: $COVERAGE_DIR/html/index.html"
