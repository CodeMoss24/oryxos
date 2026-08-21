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

# 1) 同 jar 残留实例检测(孤儿进程:在 IDE 终端里起的、终端关闭后仍存活)
LEFTOVERS=$(pgrep -f "java -jar .*oryxos-boot-1.0.0-SNAPSHOT.jar" 2>/dev/null | tr '\n' ' ' || true)
if [ -n "$LEFTOVERS" ]; then
  echo "检测到本项目的残留实例(PID $LEFTOVERS),可能占着端口:" >&2
  ps -o pid,etime,cmd -p $LEFTOVERS 2>/dev/null | head -5 >&2
  echo "先执行 ./stop.sh 停掉再启动。" >&2
  exit 1
fi

# 2) PID 文件指向的进程还活着
if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  echo "服务已在运行(PID $(cat "$PID_FILE")),先执行 ./stop.sh" >&2
  exit 1
fi

# 3) 端口可用性检查
#    a) 本发行版监听检查(ss 能看到本命名空间内监听)——镜像网络下这是唯一可靠判据
if ss -tln 2>/dev/null | grep -q ":${PORT} "; then
  echo "端口 $PORT 已被占用,请先停掉占用进程再启动:" >&2
  ss -tlnp 2>/dev/null | grep ":${PORT} " >&2 || true
  exit 1
fi
#    b) curl 探测辅助判断:rc=0 表示有服务在响应(可能 Windows 侧/其他发行版),拒绝;
#       超时(28)是镜像网络的正常路由行为(本发行版无监听时连接被 Windows 防火墙丢弃),
#       不代表端口被占,只提示不阻断——Java 能否绑定以 ss 为准。
CURL_RC=0
curl -s -m 2 -o /dev/null "http://127.0.0.1:$PORT/" 2>/dev/null || CURL_RC=$?
if [ "$CURL_RC" -eq 0 ]; then
  echo "端口 $PORT 有服务在响应(可能在 Windows 侧/其他发行版),被占用,请换端口。" >&2
  exit 1
fi
if [ "$CURL_RC" -eq 28 ]; then
  echo "[提示] 端口 $PORT 探测超时——镜像网络下本发行版无监听时的正常现象,继续启动;"
  echo "       若启动失败请检查 Windows 侧:netstat -ano | findstr :$PORT" >&2
fi

mkdir -p .oryxos/logs
nohup java -jar "$JAR" serve --server.port="$PORT" >> "$LOG_FILE" 2>&1 &
PID=$!
echo "OryxOS 启动中(PID $PID,端口 $PORT,日志 $LOG_FILE)"

# 4) 等启动就绪:成功才写 PID 文件;失败给出日志并清理,避免 PID 文件指向死进程
for i in $(seq 1 90); do
  if ! kill -0 "$PID" 2>/dev/null; then
    echo "启动失败(进程 $PID 已退出),日志尾部:" >&2
    tail -25 "$LOG_FILE" >&2
    rm -f "$PID_FILE"
    exit 1
  fi
  if curl -s -m 2 -o /dev/null "http://127.0.0.1:$PORT/api/v1/health" 2>/dev/null; then
    echo "服务已就绪(用时 ${i}s)"
    echo "$PID" > "$PID_FILE"
    echo "管理台:   http://localhost:$PORT/admin"
    echo "接口文档: http://localhost:$PORT/swagger-ui.html"
    echo "停止:     ./stop.sh"
    exit 0
  fi
  sleep 1
done

echo "启动超时(90 秒),日志尾部:" >&2
tail -25 "$LOG_FILE" >&2
kill "$PID" 2>/dev/null || true
rm -f "$PID_FILE"
exit 1
