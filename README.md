# Looma Assistant — Headless Backend (Harness)

## Accessing the Harness

### Option 1 — Pull & Run the Docker Container (Recommended)

```bash
# Pull the published image from GitHub Container Registry
docker pull ghcr.io/malu2555/looma:latest

# Create input/output directories
mkdir -p input_data output_data

# Place your tasks.json in ./input_data/tasks.json
# Example tasks.json:
# [
#   {"task_id": "code-task-1", "prompt": "Write a sorting algorithm in Python"},
#   {"task_id": "reason-task-1", "prompt": "Prove that sqrt(2) is irrational"}
# ]

# Run the container (mount input/output directories)
docker run -it --rm \
  -v $(pwd)/input_data:/input \
  -v $(pwd)/output_data:/output \
  -e FIREWORKS_BASE_URL=${FIREWORKS_BASE_URL} \
  -e FIREWORKS_API_KEY=${FIREWORKS_API_KEY} \
  -e ALLOWED_MODELS=${ALLOWED_MODELS} \
  -e SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-PROD} \
  ghcr.io/malu2555/looma:latest

# Results will appear in ./output_data/results.json
```

### Option 2 — Docker Compose

```bash
# Create input/output directories
mkdir -p input_data output_data

# Place your tasks.json in ./input_data/tasks.json
# Example tasks.json:
# [
#   {"task_id": "code-task-1", "prompt": "Write a sorting algorithm in Python"},
#   {"task_id": "reason-task-1", "prompt": "Prove that sqrt(2) is irrational"}
# ]

# Set your env vars (mirrors docker-compose.yml) and run
set FIREWORKS_BASE_URL=https://api.fireworks.ai/inference
set FIREWORKS_API_KEY=your-api-key
set ALLOWED_MODELS=accounts/fireworks/models/deepseek-coder-33b-instruct
docker-compose up --build

# Results will appear in ./output_data/results.json
```

### Option 3 — Clone & Build Locally

```bash
git clone https://github.com/Malu2555/looma-app-amd-monorepo.git
cd looma-app-amd-monorepo/backend/looma_assistant

# Build the Docker image
docker build -t looma:latest .

# Run the container (mount input/output directories)
docker run -it --rm \
  -v $(pwd)/input_data:/input \
  -v $(pwd)/output_data:/output \
  -e FIREWORKS_BASE_URL=${FIREWORKS_BASE_URL} \
  -e FIREWORKS_API_KEY=${FIREWORKS_API_KEY} \
  -e ALLOWED_MODELS=${ALLOWED_MODELS} \
  -e SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-PROD} \
  looma-assistant
```

---

## Synchronous Runner Layer (Autograder Harness)

The Java backend is an **asynchronous** Akka actor system. The autograder
harness, however, only interacts through a **synchronous** I/O contract: it
writes `tasks.json`, expects the process to *block* until every task is done,
then reads `results.json`. To bridge this, a thin Python layer in `backend/`
provides the synchronous boundary.

### Files

| File | Purpose |
|------|---------|
| `backend/runner/main.py` | Synchronous orchestrator: validates I/O, launches the JVM as a blocking subprocess, validates results |
| `backend/requirements.text` | Python dependencies (`pydantic` for I/O contract validation) |
| `backend/runner.bash` | System runner entry point: installs deps, builds the JAR if missing, then `exec`s `main.py` |

### How it works

1. Synchronously reads & validates the input tasks (`task_id`, `prompt`) against the `TaskInput` schema.
2. Launches the Java backend as a **blocking** subprocess (`subprocess.run`). The async Akka work happens inside the JVM; from the harness's view the call is synchronous because the process blocks until the JVM exits.
3. Blocks until the backend exits (non-zero exit ⇒ failure).
4. Synchronously reads & validates the produced results (`task_id`, `answer`) against the `TaskResultOutput` schema.
5. Emits the final results and exits with a proper status code.

### Synchronous contract (exit codes)

| Code | Meaning |
|------|---------|
| `0` | Success — `results.json` written |
| `1` | Generic failure (bad input, backend error, validation error, timeout) |
| `2` | Backend JAR missing (build it or let `runner.bash` build it) |

### Running the runner

```bash
cd backend

# Default: reads input_data/tasks.json, writes output_data/results.json
bash runner.bash

# With overrides
bash runner.bash \
  --input input_data/tasks.json \
  --output output_data/results.json \
  --timeout 540
```

`runner.bash` uses `exec` to replace the shell with the Python process, so
signals (SIGTERM/SIGINT) are forwarded directly to the orchestrator and the
autograder sees `main.py`'s exit code unchanged.

### Environment variables forwarded to the backend

- `FIREWORKS_BASE_URL`
- `FIREWORKS_API_KEY`
- `ALLOWED_MODELS`
- `SPRING_PROFILES_ACTIVE` (defaults to `prod` if unset)

### Virtual threads

`backend/looma_assistant/src/main/resources/application.yml` sets
`spring.threads.virtual.enabled: true` (Java 21 / Project Loom). This lets the
synchronous blocking I/O be served by virtual threads without exhausting
platform threads, keeping the headless run responsive under the harness budget.

### Docker

The image build context is now `./backend` (so it can contain both the Java
backend and the Python runner). The container `ENTRYPOINT` is
`bash /app/runner.bash` — it installs nothing at runtime (Python deps are baked
in during the build) and exec's the orchestrator, which blocks until
`/output/results.json` is written.

---

## How the Actor System Works

The backend uses an **Akka actor system** to process tasks concurrently through specialized workers. Here's the architecture:

### TaskSupervisorActor (Orchestrator)

The root actor that receives batches of tasks. It:

1. Receives a `SubmitTasks` message containing a list of `TaskInput` records
2. Routes each task to the correct specialized worker using the **TaskRouter**
3. Tracks pending completions
4. Replies to the caller with aggregated `List<TaskResultOutput>` when all tasks finish

### Specialized Workers (5 types)

Each worker is a dedicated Akka actor that handles a specific category of task:

| Worker | Purpose | Example Prompts |
|--------|---------|----------------|
| **CodeWorkerActor** | Code generation, debugging, parsing | "Write a sorting algorithm", "Fix this bug" |
| **ReasoningActor** | Math, logic, multi-step analysis | "Prove this theorem", "Solve this puzzle" |
| **FactualActor** | Lookups, definitions, historical data | "What is the capital of France?" |
| **StructuredTextActor** | Prose, summaries, chat responses | "Summarize this article" |
| **LabelSentimentActor** | Classification, NER, sentiment | "Classify this review as positive/negative" |

### TaskRouter (Classifier)

The `TaskRouter` decides which worker handles a task using a two-step approach:

1. **Task ID keywords** — checks if the `task_id` contains type hints (e.g. `"code-task-1"` → CodeWorker)
2. **Prompt analysis** — if the ID is opaque (e.g. `"task-42"`), it scores the prompt text against signal patterns for code, reasoning, factual, and label categories

### TelemetryBridgeActor

A centralized metrics collector that receives timing and token-usage data from all workers after each task completes. Logs execution metrics for observability.

### Data Flow

```
Client (CLI / REST / Docker)
        │
        ▼
TaskSupervisorEngine / ActorBridgeService
        │
        ▼  SubmitTasks
TaskSupervisorActor
        │
        ├──► CodeWorkerActor ──► ModelHolder.call() ──► Fireworks AI
        ├──► ReasoningActor  ──► ModelHolder.call() ──► Fireworks AI
        ├──► FactualActor    ──► ModelHolder.call() ──► Fireworks AI
        ├──► TextActor       ──► ModelHolder.call() ──► Fireworks AI
        └──► LabelActor      ──► ModelHolder.call() ──► Fireworks AI
                │
                ▼  TaskCompleted
        TaskSupervisorActor (aggregates results)
                │
                ▼
        List<TaskResultOutput>
```

