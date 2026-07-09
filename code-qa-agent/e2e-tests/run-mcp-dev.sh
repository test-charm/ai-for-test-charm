#!/bin/sh
set -eu

INFO='\033[1;34m[INFO]\033[0m'

log_info() {
  printf '%b %s\n' "$INFO" "$*"
}

. "${VENV_DIR:-/opt/venv}/bin/activate"
export WATCHFILES_FORCE_POLLING="${WATCHFILES_FORCE_POLLING:-true}"

log_info "Starting MCP server in hot-reload mode"
exec watchfiles --filter python --verbosity info \
  "sh -c 'printf \"\\033[1;34m[INFO]\\033[0m Restarting MCP server\\n\"; cd /app && exec python mcp_server.py --transport streamable-http --host 0.0.0.0 --port 3001'" \
  /app
