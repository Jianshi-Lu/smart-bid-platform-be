#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib.sh"

if (( $# != 0 )); then
  error "用法：$0"
  exit 2
fi

ensure_runtime_dirs
require_command curl

status=0
for service in frontend chat-agent backend vector-service to-markdown; do
  if ! stop_service "${service}"; then
    status=1
  fi
done

if (( status == 0 )); then
  log "全部脚本管理的服务已关闭"
fi
exit "${status}"
