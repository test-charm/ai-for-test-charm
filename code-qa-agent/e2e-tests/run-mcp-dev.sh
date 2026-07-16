#!/bin/sh
set -eu

INFO='\033[1;34m[INFO]\033[0m'

log_info() {
  printf '%b %s\n' "$INFO" "$*"
}

. "${VENV_DIR:-/opt/venv}/bin/activate"

log_info "Starting MCP server with coverage"
export COVERAGE_DATA_FILE="${COVERAGE_DATA_FILE:-/app/coverage/.coverage-mcp}"
export COVERAGE_RCFILE="${COVERAGE_RCFILE:-/app/.coveragerc}"
exec python mcp_server.py --transport streamable-http --host 0.0.0.0 --port 3001
