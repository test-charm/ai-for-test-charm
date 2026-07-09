#!/bin/sh
set -eu

INFO='\033[1;34m[INFO]\033[0m'

log_info() {
  printf '%b %s\n' "$INFO" "$*"
}

. "${VENV_DIR:-/opt/venv}/bin/activate"
export WATCHFILES_FORCE_POLLING="${WATCHFILES_FORCE_POLLING:-true}"

log_info "Starting Chainlit in hot-reload mode"
exec watchfiles --filter python --verbosity info \
  "sh -c 'printf \"\\033[1;34m[INFO]\\033[0m Restarting Chainlit\\n\"; cd /app && python init_db.py && exec chainlit run app.py --host 0.0.0.0 --port 8000'" \
  /app
