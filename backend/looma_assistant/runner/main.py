#!/usr/bin/env python3
"""
runner/main.py — Synchronous runner layer for the Looma autograder harness.

The Looma backend (backend/looma_assistant) is an asynchronous Akka actor
system. The autograder, however, only interacts through a *synchronous* I/O
contract: it writes tasks.json, expects the process to block until every task
is done, then reads results.json.

This module is the synchronous boundary. It:
  1. Synchronously reads & validates the input tasks (TaskInput schema).
  2. Launches the Java backend as a *blocking* subprocess. The async Akka work
     happens inside the JVM; from the harness's point of view the call is
     synchronous because subprocess.run() blocks until the JVM exits.
  3. Blocks until the backend exits (non-zero exit => failure).
  4. Synchronously reads & validates the produced results (TaskResultOutput).
  5. Emits the final results and exits with a proper status code.

Exit codes:
  0  success
  1  generic failure (bad input, backend error, validation error, etc.)
  2  missing backend JAR
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from pathlib import Path
from typing import Any

try:
    from pydantic import BaseModel, ValidationError
except ImportError:  # pragma: no cover - defensive, deps installed by runner.bash
    sys.stderr.write(
        "ERROR: pydantic is not installed. Run `pip install -r requirements.text` "
        "or use runner.bash which installs dependencies automatically.\n"
    )
    sys.exit(1)


# ---------------------------------------------------------------------------
# Synchronous I/O contract (mirrors the Java DTOs)
# ---------------------------------------------------------------------------
class TaskInput(BaseModel):
    task_id: str
    prompt: str


class TaskResultOutput(BaseModel):
    task_id: str
    answer: str


# Environment variables forwarded verbatim into the Java subprocess.
FORWARDED_ENV = (
    "FIREWORKS_BASE_URL",
    "FIREWORKS_API_KEY",
    "ALLOWED_MODELS",
    "SPRING_PROFILES_ACTIVE",
)


def log(msg: str) -> None:
    print(f"[runner] {msg}", file=sys.stderr, flush=True)


def resolve_path(candidate: str, fallback: str) -> Path:
    """Return candidate if it exists, otherwise the fallback path (not checked)."""
    p = Path(candidate)
    if p.exists():
        return p
    return Path(fallback)


def load_input_tasks(path: Path) -> list[TaskInput]:
    """Synchronously read and validate the harness input file."""
    if not path.exists():
        raise FileNotFoundError(f"Input file not found: {path}")

    raw = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(raw, list):
        raise ValueError("Input JSON must be an array of task objects")

    tasks: list[TaskInput] = []
    for i, item in enumerate(raw):
        try:
            tasks.append(TaskInput.model_validate(item))
        except ValidationError as exc:
            raise ValueError(f"Invalid task at index {i}: {exc}") from exc
    return tasks


def load_output_results(path: Path) -> list[TaskResultOutput]:
    """Synchronously read and validate the backend-produced results file."""
    if not path.exists():
        raise FileNotFoundError(f"Output file not found: {path}")

    raw = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(raw, list):
        raise ValueError("Output JSON must be an array of result objects")

    results: list[TaskResultOutput] = []
    for i, item in enumerate(raw):
        try:
            results.append(TaskResultOutput.model_validate(item))
        except ValidationError as exc:
            raise ValueError(f"Invalid result at index {i}: {exc}") from exc
    return results


def find_jar(explicit: str | None) -> Path:
    """Locate the backend JAR relative to this script's parent (backend root)."""
    backend_root = Path(__file__).resolve().parent.parent
    candidates = []
    if explicit:
        candidates.append(Path(explicit))
    candidates.append(backend_root / "looma_assistant" / "target" / "looma_assistant-0.0.1-SNAPSHOT.jar")
    for c in candidates:
        if c.exists():
            return c
    raise FileNotFoundError(
        "Backend JAR not found. Build it with `mvn -f looma_assistant/pom.xml "
        "clean package -DskipTests` or run runner.bash which builds it for you."
    )


def run_backend(jar: Path, input_file: Path, output_file: Path, timeout: int) -> None:
    """Launch the Java backend as a blocking subprocess (synchronous boundary)."""
    env = os.environ.copy()
    env["INPUT_FILE"] = str(input_file)
    env["OUTPUT_FILE"] = str(output_file)
    for key in FORWARDED_ENV:
        if key in os.environ:
            env[key] = os.environ[key]
    # Default to prod profile if the harness did not specify one.
    env.setdefault("SPRING_PROFILES_ACTIVE", "prod")

    java_opts = os.environ.get("JAVA_OPTS", "-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0")

    cmd = ["java", *java_opts.split(), "-jar", str(jar)]
    log(f"Launching backend (blocking): {' '.join(cmd)}")
    log(f"  INPUT_FILE={input_file}")
    log(f"  OUTPUT_FILE={output_file}")
    log(f"  SPRING_PROFILES_ACTIVE={env['SPRING_PROFILES_ACTIVE']}")

    proc = subprocess.run(cmd, env=env, timeout=timeout)
    if proc.returncode != 0:
        raise RuntimeError(f"Backend exited with non-zero status {proc.returncode}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Synchronous runner for the Looma autograder harness.")
    parser.add_argument("--input", default="input_data/tasks.json",
                        help="Path to input tasks.json (default: input_data/tasks.json, fallback /input/tasks.json)")
    parser.add_argument("--output", default="output_data/results.json",
                        help="Path to output results.json (default: output_data/results.json, fallback /output/results.json)")
    parser.add_argument("--jar", default=None, help="Explicit path to the backend JAR")
    parser.add_argument("--timeout", type=int, default=540,
                        help="Max seconds to wait for the backend to finish (default: 540)")
    args = parser.parse_args(argv)

    try:
        input_file = resolve_path(args.input, "/input/tasks.json")
        output_file = resolve_path(args.output, "/output/results.json")

        log(f"Reading input from {input_file}")
        tasks = load_input_tasks(input_file)
        log(f"Validated {len(tasks)} input task(s)")

        jar = find_jar(args.jar)
        log(f"Using backend JAR: {jar}")

        # Ensure the output directory exists before the backend writes to it.
        output_file.parent.mkdir(parents=True, exist_ok=True)

        # Synchronous blocking call into the async backend.
        run_backend(jar, input_file, output_file, args.timeout)

        results = load_output_results(output_file)
        log(f"Validated {len(results)} output result(s)")

        # Re-emit the results to stdout for any caller that pipes the runner.
        print(json.dumps([r.model_dump() for r in results], indent=2), flush=True)

        log("Synchronous run complete.")
        return 0

    except FileNotFoundError as exc:
        log(f"File error: {exc}")
        return 2 if "JAR" in str(exc) else 1
    except json.JSONDecodeError as exc:
        log(f"JSON parse error: {exc}")
        return 1
    except ValueError as exc:
        log(f"Validation error: {exc}")
        return 1
    except subprocess.TimeoutExpired:
        log(f"Backend timed out after {args.timeout}s")
        return 1
    except RuntimeError as exc:
        log(f"Backend failure: {exc}")
        return 1
    except Exception as exc:  # pragma: no cover - last-resort guard
        log(f"Unexpected error: {exc}")
        return 1


if __name__ == "__main__":
    sys.exit(main())