#!/usr/bin/env bash
# OryxOS 一键启动:server + 管理台(同一进程)。
# 用法:./start.sh [端口]   默认 8080;8080 被占时:./start.sh 8081
set -euo pipefail
cd "$(dirname "$0")"

PORT="${1:-8080}"
JAR="oryxos-boot/target/oryxos-boot-1.0.0-SNAPSHOT.jar"
PID_FILE=".oryxos/server.pid"
LOG_FILE=".oryxos/logs/server.log"

if [ ! -f "$JAR" ]; then
  echo "未找到 $JAR,先执行:mvn clean package(纯后端可加 -Dfrontend.skip=true)" >&2
  exit 1
fi
if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  echo "服务已在运行(PID $(cat "$PID_FILE")),先执行 ./stop.sh" >&2
  exit 1
fi
# PID 文件可能指向已死进程,但端口仍被旧进程占用(如旧版本 jar 起的服务)。
# 不查端口会"静默失败":新进程起不来,浏览器访问到的还是旧服务。
if ss -tln 2>/dev/null | grep -q ":${PORT} "; then
  echo "端口 $PORT 已被占用,请先停掉占用进程再启动:" >&2
  ss -tlnp 2>/dev/null | grep ":${PORT} " >&2 || true
  exit 1
fi

mkdir -p .oryxos/logs
nohup java -jar "$JAR" serve --server.port="$PORT" >> "$LOG_FILE" 2>&1 &
echo $! > "$PID_FILE"

echo "OryxOS 启动中(PID $!,端口 $PORT,日志 $LOG_FILE)"
echo "管理台:   http://localhost:$PORT/admin"
echo "接口文档: http://localhost:$PORT/swagger-ui.html"
echo "停止:     ./stop.sh"
