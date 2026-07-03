# Distributed Job Scheduler

A production-grade distributed job scheduler built with Spring Boot 3, Java 21, PostgreSQL, and React+TypeScript.

![Java](https://img.shields.io/badge/Java-21-orange) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-green) ![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue) ![React](https://img.shields.io/badge/React-18-blue)

## Features

- **Atomic job claiming** via `SELECT ... FOR UPDATE SKIP LOCKED` — no duplicate execution under concurrent workers
- **Multiple job types**: immediate, delayed, scheduled, recurring (cron), batch
- **Retry policies**: fixed, linear, and exponential backoff with configurable max attempts
- **Dead Letter Queue**: permanently failed jobs preserved with full error context
- **Virtual threads**: Java 21 virtual thread executor for efficient job execution
- **Real-time dashboard**: React + TypeScript with live metrics, charts, and WebSocket updates
- **Full observability**: per-execution logs, metrics, worker heartbeats
- **JWT authentication** with organization/project/queue hierarchy
- **OpenAPI documentation** via Swagger UI

## Prerequisites

- Java 21+ (JDK)
- Node.js 18+ & npm
- Docker & Docker Compose
- Maven 3.9+ (or use the included wrapper)

## Quick Start

### 1. Start PostgreSQL

```bash
docker-compose up -d
```

### 2. Run the Backend

```bash
# On Windows:
mvnw.cmd spring-boot:run

# On Linux/Mac:
./mvnw spring-boot:run
```

The backend starts at `http://localhost:8080` with both API and Worker profiles active.

### 3. Run the Frontend

```bash
cd frontend
npm install
npm run dev
```

The dashboard opens at `http://localhost:5173`.

### 4. Access Swagger UI

Open `http://localhost:8080/swagger-ui.html` for interactive API documentation.

## Architecture

```
┌────────────────────────────────────────────────────────┐
│                   React Dashboard                      │
│    (Vite + TypeScript + Recharts + WebSocket)          │
└──────────┬─────────────────────────────┬───────────────┘
           │ REST API                    │ WebSocket (STOMP)
           ▼                            ▼
┌──────────────────────────────────────────────────────────┐
│              Spring Boot Application                      │
│  ┌──────────────────┐  ┌──────────────────────────────┐  │
│  │   API Service     │  │   Worker Service              │  │
│  │   (Controllers)   │  │   (Poll → Claim → Execute)    │  │
│  │   JWT Auth Filter │  │   Virtual Thread Executor     │  │
│  │   Swagger/OpenAPI │  │   Heartbeat Service           │  │
│  │   WebSocket Pub   │  │   Cron Scheduler Service      │  │
│  └──────────────────┘  │   Retry Policy Engine          │  │
│                         └──────────────────────────────┘  │
│  ┌────────────────────────────────────────────────────┐   │
│  │              Service Layer (Business Logic)          │   │
│  │   AuthService · QueueService · JobService            │   │
│  │   MetricsService · RetryPolicyEngine                 │   │
│  └────────────────────────────────────────────────────┘   │
│  ┌────────────────────────────────────────────────────┐   │
│  │              Repository Layer (Spring Data JPA)      │   │
│  │   Atomic Claim: SELECT FOR UPDATE SKIP LOCKED        │   │
│  └──────────────────────┬─────────────────────────────┘   │
└──────────────────────────┼────────────────────────────────┘
                           ▼
                  ┌─────────────────┐
                  │   PostgreSQL 16  │
                  │   12 Tables      │
                  │   Flyway Managed │
                  └─────────────────┘
```

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/auth/register` | Register user |
| POST | `/api/v1/auth/login` | Login, get JWT |
| GET/POST | `/api/v1/projects` | Project CRUD |
| GET/POST | `/api/v1/queues` | Queue CRUD |
| POST | `/api/v1/queues/{id}/pause` | Pause queue |
| POST | `/api/v1/queues/{id}/resume` | Resume queue |
| GET | `/api/v1/queues/{id}/stats` | Queue statistics |
| POST | `/api/v1/jobs` | Create job |
| POST | `/api/v1/jobs/batch` | Create batch |
| GET | `/api/v1/jobs/{id}` | Job detail + history |
| POST | `/api/v1/jobs/{id}/retry` | Retry failed job |
| GET | `/api/v1/workers` | List workers |
| GET | `/api/v1/metrics/global` | Global metrics |
| GET | `/api/v1/dlq` | Dead letter entries |
| POST | `/api/v1/dlq/{id}/retry` | Re-enqueue DLQ entry |

## Running Tests

```bash
# All tests (requires Docker for Testcontainers)
mvnw test

# Critical atomic claim test
mvnw test -Dtest=AtomicJobClaimTest

# Retry policy unit tests
mvnw test -Dtest=RetryPolicyEngineTest
```

## Configuration

Key settings in `application.yml`:

| Property | Default | Description |
|----------|---------|-------------|
| `worker.poll-interval-ms` | 500 | How often workers poll for jobs |
| `worker.heartbeat-interval-ms` | 15000 | Heartbeat frequency |
| `worker.max-concurrent-jobs` | 10 | Max jobs per worker instance |
| `worker.stale-worker-threshold-ms` | 60000 | Time before a worker is considered dead |
| `cron-scheduler.poll-interval-ms` | 10000 | Cron trigger polling interval |

## License

MIT
