#!/bin/sh
set -euo pipefail

# ─── 颜色 ───
GREEN='\033[1;32m'
RED='\033[1;31m'
YELLOW='\033[1;33m'
CYAN='\033[1;36m'
INFO='\033[1;34m[INFO]\033[0m'
OK='\033[1;32m[OK]\033[0m'
FAIL='\033[1;31m[FAIL]\033[0m'
SECTION='\033[1;36m'

log_info()  { printf '%b %s %b\n' "$INFO" "$*" "$NC"; }
log_ok()    { printf '%b %s %b\n' "$OK" "$*" "$NC"; }
log_fail()  { printf '%b %s %b\n' "$FAIL" "$*" "$NC"; }
log_section() {
  printf '\n%b══════════════════════════════════════════════%b\n' "$SECTION" "$NC"
  printf '%b  %s  %b\n' "$CYAN" "$*" "$NC"
  printf '%b══════════════════════════════════════════════%b\n\n' "$SECTION" "$NC"
}

NC='\033[0m'
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yml"

# ─── 停掉之前的 profile ───
log_info "Stopping existing containers..."
docker compose --profile default --profile deepseek --profile anthropic --profile eval down --remove-orphans 2>/dev/null || true

# ─── 启动 eval profile ───
log_info "Starting eval containers (agent + embedding + postgres + mock-server)..."
docker compose --profile eval up -d --build --wait 2>&1 | sed 's/^/  /'
echo

# ─── 运行 eval 测试 ───
log_info "Running eval tests..."
if ./gradlew cucumber -Pfile=src/test/resources/evals/eval_system_prompt.feature 2>&1 | sed 's/^/  /'; then
  log_ok "Eval tests passed"
else
  log_fail "Eval tests failed"
  echo ""
  echo "  💡 提示: 检查 agent 日志:"
  echo "     docker compose --profile eval logs code-qa-agent-eval | tail -100"
  exit 1
fi

# ─── 停止 ───
log_info "Stopping eval containers..."
docker compose --profile eval down --remove-orphans 2>/dev/null || true

echo
log_ok "Done!"
