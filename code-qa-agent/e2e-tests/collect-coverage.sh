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
  echo "       Try: docker compose --profile default up -d && ./gradlew cucumber"
  exit 1
fi

log_info "Found coverage data files:"
echo "$DATA_FILES" | while read -r f; do echo "  $(basename "$f")"; done

# Run coverage inside the container where source paths are /app/...
# The coverage-output directory is already bind-mounted to /app/coverage/ inside the container.
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yml"

# Find any running code-qa-agent container (default or deepseek profile)
CONTAINER_NAME=""
for name in "code-qa-agent-e2e-code-qa-agent-1" "code-qa-agent-e2e-code-qa-agent-deepseek-1" "code-qa-agent-e2e-code-qa-agent-anthropic-1"; do
  if docker inspect "$name" --format '{{.State.Running}}' 2>/dev/null | grep -q true; then
    CONTAINER_NAME="$name"
    break
  fi
done

if [ -z "$CONTAINER_NAME" ]; then
  echo "ERROR: No code-qa-agent container running."
  echo "       Start one with: docker compose --profile default up -d"
  exit 1
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

log_ok "HTML report: $COVERAGE_DIR/html/index.html"
