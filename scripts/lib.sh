#!/usr/bin/env bash

# 仅供 start-all.sh 与 stop-all.sh source；不要直接执行本文件。

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECTS_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

RUNTIME_DIR="${SCRIPT_DIR}/.runtime"
PID_DIR="${RUNTIME_DIR}/pids"
LOG_DIR="${RUNTIME_DIR}/logs"

TO_MARKDOWN_DIR="${PROJECTS_ROOT}/to-markdown"
BACKEND_DIR="${PROJECTS_ROOT}/smart-bid-platform-be"
VECTOR_SERVICE_DIR="${PROJECTS_ROOT}/vector-service"
CHAT_AGENT_DIR="${PROJECTS_ROOT}/chat-agent"
FRONTEND_DIR="${PROJECTS_ROOT}/smart-bid-platform-fe"

HEALTH_POLL_INTERVAL_SECONDS="${HEALTH_POLL_INTERVAL_SECONDS:-1}"
START_TIMEOUT_SECONDS="${START_TIMEOUT_SECONDS:-180}"
STOP_TIMEOUT_SECONDS="${STOP_TIMEOUT_SECONDS:-30}"
CURL_TIMEOUT_SECONDS="${CURL_TIMEOUT_SECONDS:-2}"

STARTED_BY_CURRENT_INVOCATION=0

log() {
  printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

error() {
  printf '[%s] ERROR: %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*" >&2
}

service_health_url() {
  case "$1" in
    to-markdown) printf '%s\n' 'http://127.0.0.1:8081/health' ;;
    backend) printf '%s\n' 'http://127.0.0.1:8080/health' ;;
    vector-service) printf '%s\n' 'http://127.0.0.1:8083/health' ;;
    chat-agent) printf '%s\n' 'http://127.0.0.1:8082/health' ;;
    frontend) printf '%s\n' 'http://127.0.0.1:5173/health' ;;
    *)
      error "未知服务：$1"
      return 1
      ;;
  esac
}

service_expected_status() {
  case "$1" in
    to-markdown|frontend) printf '%s\n' 'ok' ;;
    backend) printf '%s\n' 'UP' ;;
    vector-service|chat-agent) printf '%s\n' 'ready' ;;
    *)
      error "未知服务：$1"
      return 1
      ;;
  esac
}

service_pid_file() {
  printf '%s/%s.pid\n' "${PID_DIR}" "$1"
}

service_log_file() {
  printf '%s/%s.log\n' "${LOG_DIR}" "$1"
}

ensure_runtime_dirs() {
  mkdir -p "${PID_DIR}" "${LOG_DIR}"
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    error "未找到命令：$1"
    return 1
  fi
}

validate_start_prerequisites() {
  local directory

  ensure_runtime_dirs
  for directory in \
    "${TO_MARKDOWN_DIR}" \
    "${BACKEND_DIR}" \
    "${VECTOR_SERVICE_DIR}" \
    "${CHAT_AGENT_DIR}" \
    "${FRONTEND_DIR}"; do
    if [[ ! -d "${directory}" ]]; then
      error "服务目录不存在：${directory}"
      return 1
    fi
  done

  if [[ ! -f "${TO_MARKDOWN_DIR}/pyproject.toml" ]]; then
    error "to-markdown uv 项目配置不存在：${TO_MARKDOWN_DIR}/pyproject.toml"
    return 1
  fi
  if [[ ! -x "${BACKEND_DIR}/mvnw" ]]; then
    error "Java Maven Wrapper 不可执行：${BACKEND_DIR}/mvnw"
    return 1
  fi

  require_command curl
  require_command java
  require_command uv
  require_command npm
}

is_service_healthy() {
  local service="$1"
  local url expected_status body
  url="$(service_health_url "${service}")" || return 1
  expected_status="$(service_expected_status "${service}")" || return 1

  if ! body="$(curl --silent --fail \
    --connect-timeout "${CURL_TIMEOUT_SECONDS}" \
    --max-time "${CURL_TIMEOUT_SECONDS}" \
    "${url}" 2>/dev/null)"; then
    return 1
  fi

  printf '%s' "${body}" | grep -Eq \
    "\"status\"[[:space:]]*:[[:space:]]*\"${expected_status}\""
}

# 与 is_service_healthy 不同：HTTP 5xx 仍表示服务进程尚在监听。
is_service_health_endpoint_reachable() {
  local url
  url="$(service_health_url "$1")" || return 1
  curl --silent --output /dev/null \
    --connect-timeout "${CURL_TIMEOUT_SECONDS}" \
    --max-time "${CURL_TIMEOUT_SECONDS}" \
    "${url}" >/dev/null 2>&1
}

read_service_pid() {
  local pid_file
  pid_file="$(service_pid_file "$1")"
  [[ -f "${pid_file}" ]] || return 1
  cat "${pid_file}"
}

is_pid_running() {
  [[ "$1" =~ ^[0-9]+$ ]] && kill -0 "$1" >/dev/null 2>&1
}

wait_for_service_health() {
  local service="$1"
  local deadline pid
  deadline=$((SECONDS + START_TIMEOUT_SECONDS))

  while ! is_service_healthy "${service}"; do
    pid="$(read_service_pid "${service}" 2>/dev/null || true)"
    if [[ -n "${pid}" ]] && ! is_pid_running "${pid}"; then
      error "${service} 启动进程已退出，请查看 $(service_log_file "${service}")"
      return 1
    fi
    if (( SECONDS >= deadline )); then
      error "等待 ${service} 健康检查超时，请查看 $(service_log_file "${service}")"
      return 1
    fi
    sleep "${HEALTH_POLL_INTERVAL_SECONDS}"
  done
}

wait_for_service_stopped() {
  local service="$1"
  local deadline
  deadline=$((SECONDS + STOP_TIMEOUT_SECONDS))

  while is_service_health_endpoint_reachable "${service}"; do
    if (( SECONDS >= deadline )); then
      error "${service} 健康端点仍可访问，未确认关闭"
      return 1
    fi
    sleep "${HEALTH_POLL_INTERVAL_SECONDS}"
  done
}

start_service() {
  local service="$1"
  local directory="$2"
  local pid_file pid log_file
  shift 2

  STARTED_BY_CURRENT_INVOCATION=0
  pid_file="$(service_pid_file "${service}")"
  log_file="$(service_log_file "${service}")"

  if [[ -f "${pid_file}" ]]; then
    pid="$(read_service_pid "${service}" 2>/dev/null || true)"
    if [[ -z "${pid}" ]] || ! is_pid_running "${pid}"; then
      rm -f "${pid_file}"
    fi
  fi

  if is_service_healthy "${service}"; then
    log "${service} 已健康运行，跳过启动"
    return 0
  fi

  if [[ -f "${pid_file}" ]]; then
    pid="$(read_service_pid "${service}")"
    error "${service} 的已记录 PID ${pid} 仍在运行但健康检查失败；不会覆盖该进程"
    return 1
  fi

  if is_service_health_endpoint_reachable "${service}"; then
    error "${service} 健康端点可访问但状态不符合要求；不会覆盖未知进程"
    return 1
  fi

  log "启动 ${service}"
  (
    cd "${directory}"
    nohup "$@" >"${log_file}" 2>&1 < /dev/null &
    printf '%s\n' "$!" >"${pid_file}"
  )
  STARTED_BY_CURRENT_INVOCATION=1

  if ! wait_for_service_health "${service}"; then
    return 1
  fi
  log "${service} 健康检查通过：$(service_health_url "${service}")"
}

stop_service() {
  local service="$1"
  local pid_file pid
  pid_file="$(service_pid_file "${service}")"

  if [[ ! -f "${pid_file}" ]]; then
    if is_service_health_endpoint_reachable "${service}"; then
      error "${service} 仍在运行，但没有脚本记录的 PID；为避免误杀，跳过关闭"
      return 1
    fi
    log "${service} 已停止"
    return 0
  fi

  pid="$(read_service_pid "${service}" 2>/dev/null || true)"
  if [[ -z "${pid}" ]] || ! is_pid_running "${pid}"; then
    if is_service_health_endpoint_reachable "${service}"; then
      error "${service} 健康端点可访问，但 PID 文件已失效；为避免误杀，保留 PID 文件"
      return 1
    fi
    rm -f "${pid_file}"
    log "${service} 已停止，已清理失效 PID 文件"
    return 0
  fi

  log "关闭 ${service}（PID ${pid}）"
  if ! kill -TERM "${pid}"; then
    error "无法向 ${service} 的 PID ${pid} 发送 SIGTERM"
    return 1
  fi

  wait_for_service_stopped "${service}"
  rm -f "${pid_file}"
  log "${service} 已关闭"
}

print_health_summary() {
  local service
  for service in "$@"; do
    if is_service_healthy "${service}"; then
      log "${service}: healthy"
    else
      error "${service}: unhealthy"
      return 1
    fi
  done
}

# 启动成功后输出统一日志索引和即将执行的实时查看命令。
print_log_dashboard() {
  local service pid log_file health_url

  printf '\n'
  log "服务日志面板"
  printf '%-16s %-10s %-34s %s\n' "服务" "PID" "健康地址" "日志文件"
  for service in "$@"; do
    pid="$(read_service_pid "${service}" 2>/dev/null || printf '%s' '-')"
    health_url="$(service_health_url "${service}")"
    log_file="$(service_log_file "${service}")"
    printf '%-16s %-10s %-34s %s\n' \
      "${service}" "${pid}" "${health_url}" "${log_file}"
  done

  printf '\n'
  log "启动完成后将自动进入实时日志查看（Ctrl+C 仅退出日志查看，不会停止服务）。"
  printf 'tail -n 100 -F'
  for service in "$@"; do
    printf ' %q' "$(service_log_file "${service}")"
  done
  printf '\n'
}

# 复用各服务原有落盘日志，统一输出到当前终端，不改变 nohup 的重定向行为。
follow_service_logs() {
  local service
  local -a log_files=()

  for service in "$@"; do
    log_files+=("$(service_log_file "${service}")")
  done

  log "全部服务已启动，开始实时打印服务日志"
  exec tail -n 100 -F "${log_files[@]}"
}
