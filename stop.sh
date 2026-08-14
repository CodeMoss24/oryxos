#!/usr/bin/env bash
# 停止 OryxOS 服务(读 start.sh 留下的 pid 文件)。
set -u
cd "$(dirname "$0")"

PID_FILE=".oryxos/server.pid"

if [ -f "$PID_FILE" ]; then
  PID=$(cat "$PID_FILE")
  if kill -0 "$PID" 2>/dev/null; then
    kill "$PID"
    echo "已发送停止信号(PID $PID),稍候进程退出"
  else
    echo "进程已不在(PID $PID)"
  fi
  rm -f "$PID_FILE"
else
  echo "没有运行中的服务(找不到 $PID_FILE)"
fi
