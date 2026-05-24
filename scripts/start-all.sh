#!/usr/bin/env bash
# Start all Dot-Blog microservices in background
# Usage: ./scripts/start-all.sh
# Stop all: ./scripts/start-all.sh stop

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
LOG_DIR="$PROJECT_DIR/logs"
mkdir -p "$LOG_DIR"

SERVICES=("gateway" "auth-service" "user-service" "blog-service" "engagement-service")
PORTS=(8080 8081 8082 8083 8084)

stop_all() {
    echo "Stopping all services..."
    for i in "${!SERVICES[@]}"; do
        PORT="${PORTS[$i]}"
        # Prefer lsof; fall back to ss (lsof isn't installed by default on Ubuntu)
        PID=$(lsof -ti :$PORT 2>/dev/null || true)
        if [ -z "$PID" ]; then
            PID=$(ss -ltnp 2>/dev/null | awk -v p=":$PORT" '$4 ~ p {match($0,/pid=([0-9]+)/,a); print a[1]; exit}')
        fi
        if [ -n "$PID" ]; then
            kill "$PID" 2>/dev/null && echo "  Stopped ${SERVICES[$i]} (port $PORT, pid $PID)"
        fi
    done
    echo "All stopped."
}

if [ "${1:-}" = "stop" ]; then
    stop_all
    exit 0
fi

echo "Starting Dot-Blog backend services..."
echo "JAVA_HOME=$JAVA_HOME"
echo "Logs: $LOG_DIR/"
echo ""

for i in "${!SERVICES[@]}"; do
    SERVICE="${SERVICES[$i]}"
    PORT="${PORTS[$i]}"

    # Skip if already running
    if curl -s "http://localhost:$PORT/actuator/health" > /dev/null 2>&1; then
        echo "  ✓ $SERVICE already running on port $PORT"
        continue
    fi

    echo "  Starting $SERVICE (port $PORT)..."
    cd "$PROJECT_DIR/$SERVICE"
    ../mvnw spring-boot:run > "$LOG_DIR/$SERVICE.log" 2>&1 &
    echo "    PID=$! → log: logs/$SERVICE.log"
done

echo ""
echo "Waiting for services to start..."
sleep 12

echo ""
for i in "${!SERVICES[@]}"; do
    SERVICE="${SERVICES[$i]}"
    PORT="${PORTS[$i]}"
    STATUS=$(curl -s "http://localhost:$PORT/actuator/health" 2>/dev/null | grep -o '"status":"[^"]*"' || echo '"status":"DOWN"')
    echo "  $SERVICE (port $PORT): $STATUS"
done

echo ""
echo "Done! Access via Gateway: http://localhost:8080/api/v2/..."
echo "Stop all: $SCRIPT_DIR/start-all.sh stop"
