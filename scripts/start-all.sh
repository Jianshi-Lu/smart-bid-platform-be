#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib.sh"

if (( $# != 0 )); then
  error "用法：$0"
  exit 2
fi

declare -a STARTED_SERVICES=()

cleanup_started_services() {
  local index
  for ((index = ${#STARTED_SERVICES[@]} - 1; index >= 0; index--)); do
    stop_service "${STARTED_SERVICES[index]}" || true
  done
}

handle_interrupt() {
  error "启动过程被中断，关闭本次已启动的服务"
  cleanup_started_services
  exit 130
}

start_and_track() {
  local service="$1"
  shift

  if ! start_service "${service}" "$@"; then
    cleanup_started_services
    exit 1
  fi
  if (( STARTED_BY_CURRENT_INVOCATION == 1 )); then
    STARTED_SERVICES+=("${service}")
  fi
}

trap handle_interrupt INT TERM

validate_start_prerequisites

start_and_track to-markdown "${TO_MARKDOWN_DIR}" \
  uv run python -m api.server
start_and_track vector-service "${VECTOR_SERVICE_DIR}" \
  uv run python -m src.api.server
start_and_track backend "${BACKEND_DIR}" \
  env SPRING_DEVTOOLS_RESTART_ENABLED=false \
  "${BACKEND_DIR}/mvnw" spring-boot:run
start_and_track chat-agent "${CHAT_AGENT_DIR}" \
  uv run python -m src.api.server
start_and_track frontend "${FRONTEND_DIR}" \
  npm run dev -- --host 127.0.0.1

if ! print_health_summary to-markdown backend vector-service chat-agent frontend; then
  cleanup_started_services
  exit 1
fi
log "全部服务已启动"
