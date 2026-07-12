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
