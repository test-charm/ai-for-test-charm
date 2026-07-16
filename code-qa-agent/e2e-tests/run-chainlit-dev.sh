#!/bin/sh
set -eu

INFO='\033[1;34m[INFO]\033[0m'

log_info() {
  printf '%b %s\n' "$INFO" "$*"
}

. "${VENV_DIR:-/opt/venv}/bin/activate"

log_info "Initializing database"
cd /app && python init_db.py

log_info "Starting Chainlit with coverage"
export COVERAGE_DATA_FILE="${COVERAGE_DATA_FILE:-/app/coverage/.coverage-chainlit}"
export COVERAGE_RCFILE="${COVERAGE_RCFILE:-/app/.coveragerc}"
exec chainlit run app.py --host 0.0.0.0 --port 8000
