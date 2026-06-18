#!/usr/bin/env bash
set -euo pipefail

# Start script for the Telegram Spring Boot app (module: teste)
# Usage:
#   ./start.sh        -> builds (if needed) and runs the app in foreground
#   ./start.sh bg     -> runs the app in background (writes logs to start.log)

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_DIR="$ROOT_DIR/teste"
JAR_NAME="teste-0.0.1-SNAPSHOT.jar"
JAR_PATH="$APP_DIR/target/$JAR_NAME"

cd "$APP_DIR"

if [ ! -f "$JAR_PATH" ]; then
  echo "JAR não encontrado em $JAR_PATH — executando build..."
  mvn -B clean package
fi

if [ "${1:-}" = "bg" ]; then
  echo "Iniciando em background (logs em $ROOT_DIR/start.log)"
  nohup java -jar "$JAR_PATH" > "$ROOT_DIR/start.log" 2>&1 &
  echo $! > "$ROOT_DIR/start.pid"
  echo "PID salvo em $ROOT_DIR/start.pid"
else
  echo "Iniciando aplicação (foreground)..."
  exec java -jar "$JAR_PATH"
fi
