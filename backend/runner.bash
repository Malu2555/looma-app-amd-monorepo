#!/usr/bin/env bash
#
# runner.bash — System runner entry point for the Looma autograder harness.
#
# This is the single synchronous, signal-forwarding process the autograder
# launches. It prepares the environment, ensures Python dependencies and the
# backend JAR are present, then exec's the Python orchestrator (runner/main.py)
# so that the autograder's synchronous I/O contract is honoured: the process
# blocks until all tasks are processed and results.json is written.
#
# Usage:
#   bash runner.bash [--input ...] [--output ...] [--jar ...] [--timeout ...]
#
# Environment (forwarded to the Java backend by main.py):
#   FIREWORKS_BASE_URL, FIREWORKS_API_KEY, ALLOWED_MODELS, SPRING_PROFILES_ACTIVE
set -euo pipefail

# Resolve the backend root (this script lives at backend/runner.bash).
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${BACKEND_ROOT}"

echo "[runner.bash] Backend root: ${BACKEND_ROOT}" >&2

# Ensure harness I/O directories exist (relative mounts for local runs).
mkdir -p "${BACKEND_ROOT}/input_data" "${BACKEND_ROOT}/output_data"

# ── Python dependencies ────────────────────────────────────────────────
# Install the synchronous layer's deps (best-effort; skip if pip missing).
if command -v pip3 >/dev/null 2>&1; then
  echo "[runner.bash] Installing Python dependencies from requirements.text..." >&2
  pip3 install -r "${BACKEND_ROOT}/requirements.text" || \
    echo "[runner.bash] WARNING: pip install failed; continuing (deps may already be present)." >&2
elif command -v pip >/dev/null 2>&1; then
  echo "[runner.bash] Installing Python dependencies from requirements.text..." >&2
  pip install -r "${BACKEND_ROOT}/requirements.text" || \
    echo "[runner.bash] WARNING: pip install failed; continuing (deps may already be present)." >&2
else
  echo "[runner.bash] WARNING: pip not found; assuming pydantic is already available." >&2
fi

# ── Backend JAR (build only if missing) ────────────────────────────────
JAR_PATH="${BACKEND_ROOT}/looma_assistant/target/looma_assistant-0.0.1-SNAPSHOT.jar"
if [ ! -f "${JAR_PATH}" ]; then
  if command -v mvn >/dev/null 2>&1; then
    echo "[runner.bash] Backend JAR not found — building with Maven..." >&2
    (cd "${BACKEND_ROOT}/looma_assistant" && mvn -q clean package -DskipTests -B --no-transfer-progress) || \
      echo "[runner.bash] WARNING: Maven build failed; main.py will report the missing JAR." >&2
  elif [ -f "${BACKEND_ROOT}/looma_assistant/mvnw" ]; then
    echo "[runner.bash] Backend JAR not found — building with Maven wrapper..." >&2
    (cd "${BACKEND_ROOT}/looma_assistant" && ./mvnw -q clean package -DskipTests -B --no-transfer-progress) || \
      echo "[runner.bash] WARNING: Maven wrapper build failed; main.py will report the missing JAR." >&2
  else
    echo "[runner.bash] WARNING: Backend JAR missing and no Maven available; main.py will fail." >&2
  fi
else
  echo "[runner.bash] Backend JAR present: ${JAR_PATH}" >&2
fi

# ── Exec the synchronous Python orchestrator ───────────────────────────
# exec replaces this shell with python so signals (SIGTERM/SIGINT) are
# forwarded directly to the orchestrator, preserving the synchronous contract.
echo "[runner.bash] Executing synchronous runner (main.py)..." >&2
exec python3 "${SCRIPT_DIR}/main.py" "$@"