#!/bin/sh
set -eu

GREEN='\033[1;32m[OK]\033[0m'
INFO='\033[1;34m[INFO]\033[0m'

log_info()  { printf '%b %s\n' "$INFO" "$*"; }
log_ok()    { printf '%b %s\n' "$GREEN" "$*"; }

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
COVERAGE_DIR="$SCRIPT_DIR/coverage-output"
PARENT_DIR="$(dirname "$SCRIPT_DIR")"

if [ ! -d "$COVERAGE_DIR" ]; then
  echo "ERROR: coverage-output/ directory not found."
  exit 1
fi

DATA_FILES="$(find "$COVERAGE_DIR" -maxdepth 1 -name '.coverage-*' -type f 2>/dev/null || true)"
if [ -z "$DATA_FILES" ]; then
  echo "ERROR: No .coverage-* data files found in $COVERAGE_DIR"
  echo "       Make sure the containers were started with coverage enabled."
  echo "       Try: docker compose up -d && ./gradlew cucumber"
  exit 1
fi

log_info "Found coverage data files:"
echo "$DATA_FILES" | while read -r f; do echo "  $(basename "$f")"; done

# Run coverage inside the container where source paths are /app/...
# The coverage-output directory is already bind-mounted to /app/coverage/ inside the container.
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yml"
CONTAINER_NAME="code-qa-agent-e2e-code-qa-agent-1"

# Check if container is running; if not, start it temporarily
NEED_STOP=false
if ! docker inspect "$CONTAINER_NAME" --format '{{.State.Running}}' 2>/dev/null | grep -q true; then
  log_info "Starting code-qa-agent container for report generation..."
  docker compose -f "$COMPOSE_FILE" up -d code-qa-agent 2>&1 | tail -1
  NEED_STOP=true
  sleep 5
fi

log_info "Combining coverage data..."
docker exec "$CONTAINER_NAME" sh -c \
  '/opt/venv/bin/coverage combine --keep /app/coverage/.coverage-*'

log_info "Coverage summary:"
docker exec "$CONTAINER_NAME" sh -c \
  '/opt/venv/bin/coverage report -m'

log_info "Generating HTML report..."
docker exec "$CONTAINER_NAME" sh -c \
  '/opt/venv/bin/coverage html -d /app/coverage/html'

if [ "$NEED_STOP" = true ]; then
  log_info "Stopping temporary container..."
  docker compose -f "$COMPOSE_FILE" stop code-qa-agent 2>&1 | tail -1
fi

log_ok "HTML report: $COVERAGE_DIR/html/index.html"
