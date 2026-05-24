#!/usr/bin/env bash
# Stop processes started by start-all.sh (Maven PIDs in logs/*.pid).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
for pidf in "$ROOT"/logs/*.pid; do
  [[ -f "$pidf" ]] || continue
  pid="$(cat "$pidf")"
  if kill -0 "$pid" 2>/dev/null; then
    echo "Stopping PID $pid ($pidf)"
    kill "$pid" 2>/dev/null || true
  fi
  rm -f "$pidf"
done
echo "Done."
