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
NC='\033[0m'

log_info()  { printf '%b %s %b\n' "$INFO" "$*" "$NC"; }
log_ok()    { printf '%b %s %b\n' "$OK" "$*" "$NC"; }
log_fail()  { printf '%b %s %b\n' "$FAIL" "$*" "$NC"; }
log_section() {
  printf '\n%b══════════════════════════════════════════════%b\n' "$SECTION" "$NC"
  printf '%b  %s  %b\n' "$CYAN" "$*" "$NC"
  printf '%b══════════════════════════════════════════════%b\n\n' "$SECTION" "$NC"
}

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# 清理遗留覆盖率数据，避免跨次运行干扰
COVERAGE_DIR="$SCRIPT_DIR/coverage-output"
if [ -d "$COVERAGE_DIR" ]; then
  find "$COVERAGE_DIR" -maxdepth 1 -name '.coverage-*' -type f -delete 2>/dev/null || true
  log_info "Cleaned old coverage data from $COVERAGE_DIR"
fi

COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yml"

run_profile() {
  profile="$1"
  tags="$2"
  description="$3"

  log_section "Profile: $profile — $description"

  # 停掉之前的所有容器
  log_info "Stopping existing containers..."
  docker compose --profile default --profile deepseek --profile anthropic down --remove-orphans 2>/dev/null || true

  # 启动当前 profile
  log_info "Starting $profile containers..."
  docker compose --profile "$profile" up -d --build --wait 2>&1 | sed 's/^/  /'
  echo

  # 运行测试
  log_info "Running tests (tags: $tags)..."
  if ./gradlew cucumber -Ptags="$tags" 2>&1 | sed 's/^/  /'; then
    log_ok "$profile tests passed"
  else
    log_fail "$profile tests failed — exiting"
    exit 1
  fi
  echo
}

# ─── 1. default profile ───
run_profile "default" \
  "not @deepseek-model and not @anthropic-provider" \
  "mock-gpt / tool_choice=required"

# ─── 2. deepseek profile ───
run_profile "deepseek" \
  "@deepseek-model" \
  "mock-deepseek-chat / tool_choice=auto"

# ─── 3. anthropic profile ───
run_profile "anthropic" \
  "@anthropic-provider" \
  "mock-claude / tool_choice=any"

# ─── 4. collect coverage ───
log_section "Collecting Coverage"
if [ -x "$SCRIPT_DIR/collect-coverage.sh" ]; then
  "$SCRIPT_DIR/collect-coverage.sh"
else
  log_fail "collect-coverage.sh not found or not executable"
fi

# ─── 5. stop all containers ───
log_info "Stopping all containers..."
docker compose --profile default --profile deepseek --profile anthropic down --remove-orphans 2>/dev/null || true

# ─── 6. summary ───
echo
log_ok "All profiles passed!"
