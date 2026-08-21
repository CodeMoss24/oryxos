#!/usr/bin/env bash
# 停止 OryxOS 服务:先杀 PID 文件里的,再按 jar 名兜底清理残留实例(含孤儿进程)。
# 只杀本项目 jar(1.0.0-SNAPSHOT),不会误杀其他项目的 OryxOS 实例。
set -u
cd "$(dirname "$0")"

JAR="oryxos-boot-1.0.0-SNAPSHOT.jar"
PID_FILE=".oryxos/server.pid"
KILLED=""

if [ -f "$PID_FILE" ]; then
  PID=$(cat "$PID_FILE")
  if kill -0 "$PID" 2>/dev/null; then
    kill "$PID" 2>/dev/null && echo "已发送停止信号(PID $PID)" && KILLED="$KILLED $PID"
  else
    echo "PID 文件指向的进程已不在($PID)"
  fi
  rm -f "$PID_FILE"
else
  echo "没有 PID 文件"
fi

# 兜底:同 jar 的残留实例(多次启动残留、IDE 终端遗留的孤儿进程)
# 匹配 "java -jar .../oryxos-boot-1.0.0-SNAPSHOT.jar",不会命中 shell/测试进程
REMAINS=$(pgrep -f "java -jar .*oryxos-boot-1.0.0-SNAPSHOT.jar" 2>/dev/null || true)
if [ -n "$REMAINS" ]; then
  echo "发现残留实例:$REMAINS(终止)"
  for p in $REMAINS; do
    kill "$p" 2>/dev/null && echo "已终止 PID $p" && KILLED="$KILLED $p"
  done
  sleep 2
  STILL=$(pgrep -f "java -jar .*oryxos-boot-1.0.0-SNAPSHOT.jar" 2>/dev/null || true)
  if [ -n "$STILL" ]; then
    echo "以下进程未退出,强杀:$STILL" >&2
    for p in $STILL; do
      kill -9 "$p" 2>/dev/null
    done
  fi
fi

if [ -z "$KILLED" ]; then
  echo "没有运行中的 OryxOS 实例"
else
  echo "停止完成。"
fi
