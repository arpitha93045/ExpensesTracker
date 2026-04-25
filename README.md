# Smart Expense Analyzer

A full-stack personal finance tracker with AI-powered expense categorization, analytics, budget planning, and bank statement imports.

**Stack:** Spring Boot 3 · Angular 18 · PostgreSQL 16 · Claude AI (Anthropic)

---

## Quick Start

### Option 1 — Docker Compose (everything in one command)

> Requires: Docker, Docker Compose, and an Anthropic API key

```bash
# 1. Copy and fill in environment variables
cp .env.example .env
# Open .env and set: DB_PASSWORD, JWT_SECRET, ANTHROPIC_API_KEY

# 2. Start PostgreSQL + Backend + Frontend together
docker compose up --build
```

| Service | URL |
|---------|-----|
| Frontend | http://localhost:80 |
| Backend API | http://localhost:8081/api |
| Swagger UI | http://localhost:8081/api/swagger-ui.html |

---

### Option 2 — Local Dev (hot reload on both frontend and backend)

> Requires: Java 21, Node 20, PostgreSQL 16 running locally

**Terminal 1 — Backend:**
```bash
# Set required env vars, then:
export DB_HOST=localhost DB_PORT=5432 DB_NAME=expensetracker
export DB_USERNAME=postgres DB_PASSWORD=your_password
export JWT_SECRET=your_jwt_secret ANTHROPIC_API_KEY=sk-ant-...
export CORS_ORIGINS=http://localhost:4200 STORAGE_PATH=./uploads

./mvnw spring-boot:run
# Backend runs at http://localhost:8080/api
```

**Terminal 2 — Frontend:**
```bash
cd expense-tracker-ui
npm install        # first time only
npm start
# Frontend runs at http://localhost:4200
```

Open `http://localhost:4200` in your browser. The Angular dev server automatically proxies all `/api` requests to `http://localhost:8080` — no CORS configuration needed.

---

## Features

- **AI Categorization** — Automatically categorizes transactions using Claude (claude-3-5-haiku)
- **Bank Statement Import** — Upload CSV or PDF bank statements; processed asynchronously
- **Dashboard & Analytics** — Spending summaries, category breakdowns, monthly trends
- **Budget Planning** — Set monthly budgets per category and track spend
- **Calendar View** — Daily expense heatmap
- **Spending Autopsy** — Deep-dive analysis and anomaly detection
- **What-If Simulator** — Model spending scenarios
- **Inflation Analysis** — Track purchasing power over time
- **JWT Auth** — Secure login with HttpOnly cookies and per-device token revocation

---

## Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| Java | 21 | JDK (not JRE) |
| Maven | 3.9+ | Or use `./mvnw` wrapper |
| Node.js | 20+ | For Angular development |
| npm | 10+ | Included with Node 20 |
| PostgreSQL | 16 | Only for local dev (not needed for Docker) |
| Docker | 24+ | Required for Docker Compose setup |
| Docker Compose | 2.x | Required for Docker setup |
| Anthropic API Key | — | Get one at [console.anthropic.com](https://console.anthropic.com) |

---

## Environment Variables

Create a `.env` file in the project root (copy from `.env.example`):

```bash
cp .env.example .env
```

Edit `.env` with your values:

```env
# Database
DB_HOST=localhost
DB_PORT=5433
DB_NAME=expensetracker
DB_USERNAME=postgres
DB_PASSWORD=your_db_password

# JWT (use a long random string, 256+ bits)
JWT_SECRET=your_jwt_secret_key_at_least_256_bits_long

# Anthropic Claude AI
ANTHROPIC_API_KEY=sk-ant-...
ANTHROPIC_MODEL=claude-3-5-haiku-20241022

# File Storage
STORAGE_PATH=./uploads

# CORS (comma-separated allowed origins)
CORS_ORIGINS=http://localhost:4200

# Email Notifications (optional)
NOTIFICATIONS_ENABLED=false
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your_email@gmail.com
MAIL_PASSWORD=your_app_password
MAIL_FROM=your_email@gmail.com

# Server
SERVER_PORT=8080
```

---

## Running with Docker Compose (Recommended)

This starts PostgreSQL, the Spring Boot backend, and the Angular frontend together.

```bash
# 1. Clone the repository
git clone <repo-url>
cd ExpensesTracker

# 2. Set up environment variables
cp .env.example .env
# Edit .env with your values (especially ANTHROPIC_API_KEY, JWT_SECRET, DB_PASSWORD)

# 3. Build and start all services
docker compose up --build

# 4. Stop all services
docker compose down

# 5. Stop and remove volumes (resets the database)
docker compose down -v
```

Once running:

| Service | URL |
|---------|-----|
| Frontend | http://localhost:80 |
| Backend API | http://localhost:8081/api |
| Swagger UI | http://localhost:8081/api/swagger-ui.html |
| API Docs (JSON) | http://localhost:8081/api/v3/api-docs |
| Health Check | http://localhost:8081/api/actuator/health |

---

## Running Locally (Without Docker)

### 1. Start PostgreSQL

You need a PostgreSQL 16 instance running locally.

```bash
# macOS (Homebrew)
brew install postgresql@16
brew services start postgresql@16

# Create the database
createdb expensetracker
```

### 2. Start the Backend

```bash
# From project root
cd /path/to/ExpensesTracker

# Set environment variables
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=expensetracker
export DB_USERNAME=postgres
export DB_PASSWORD=your_password
export JWT_SECRET=your_jwt_secret
export ANTHROPIC_API_KEY=sk-ant-...
export STORAGE_PATH=./uploads
export CORS_ORIGINS=http://localhost:4200

# Run with Maven wrapper
./mvnw spring-boot:run

# Or build a JAR and run it
./mvnw clean package -DskipTests
java -jar target/expense-tracker-*.jar
```

Backend starts on `http://localhost:8080/api`

### 3. Start the Frontend

```bash
# Navigate to the frontend directory
cd expense-tracker-ui

# Install dependencies (first time only)
npm install

# Start the dev server (proxies /api calls to localhost:8080)
npm start
```

Frontend starts on `http://localhost:4200`

---

## Running Tests

### Backend Tests

```bash
# Run all tests
./mvnw test

# Run a specific test class
./mvnw test -Dtest=AuthControllerTest

# Run tests and generate coverage report
./mvnw verify
```

Tests use H2 in-memory database (no PostgreSQL needed for unit tests). Integration tests use Testcontainers.

### Frontend Tests

```bash
cd expense-tracker-ui

# Run tests once
npm test

# Run tests in watch mode
npm run test -- --watch
```

---

## Building for Production

### Backend JAR

```bash
./mvnw clean package -DskipTests
# Output: target/expense-tracker-*.jar
```

### Frontend

```bash
cd expense-tracker-ui
npm run build
# Output: dist/expense-tracker-ui/
```

### Docker Images

```bash
# Backend image
docker build -f Dockerfile.backend -t expense-tracker-backend .

# Frontend image
docker build -f expense-tracker-ui/Dockerfile.frontend -t expense-tracker-frontend ./expense-tracker-ui
```

---

## Project Structure

```
ExpensesTracker/
├── src/                            # Spring Boot backend
│   └── main/
│       ├── java/com/expensetracker/
│       │   ├── config/             # Security, OpenAPI, Async config
│       │   ├── controller/         # REST controllers
│       │   ├── domain/             # JPA entities and enums
│       │   ├── dto/                # Request/response DTOs
│       │   ├── repository/         # Spring Data JPA repositories
│       │   ├── security/           # JWT filter and service
│       │   └── service/            # Business logic
│       └── resources/
│           ├── application.yml     # App configuration
│           └── db/migration/       # Flyway SQL migrations
├── expense-tracker-ui/             # Angular 18 frontend
│   └── src/app/
│       ├── core/                   # Services, guards, interceptors, models
│       ├── features/               # Feature modules (dashboard, transactions, etc.)
│       └── shared/                 # Shared components and pipes
├── docker-compose.yml              # Multi-service Docker setup
├── Dockerfile.backend              # Backend Docker image
├── expense-tracker-ui/
│   ├── Dockerfile.frontend         # Frontend Docker image
│   └── nginx.conf                  # Nginx production config
├── .env.example                    # Environment variable template
└── pom.xml                         # Maven build config
```

---

## API Overview

| Group | Base Path | Description |
|-------|-----------|-------------|
| Auth | `/api/auth` | Register, login, refresh token, logout |
| Transactions | `/api/transactions` | List, filter, export, delete transactions |
| Analytics | `/api/analytics` | Summaries, trends, bills, inflation, calendar |
| Categories | `/api/categories` | List system and user-defined categories |
| Budgets | `/api/budgets` | Get, set, and delete monthly budgets |
| Uploads | `/api/uploads` | Upload CSV/PDF, poll job status |

Full interactive API documentation is available at `/api/swagger-ui.html` when the backend is running.

---

## Database Migrations

Flyway runs migrations automatically on startup. Migration files live in `src/main/resources/db/migration/`:

| File | Description |
|------|-------------|
| `V1__init_schema.sql` | Core tables: users, transactions, categories, upload_jobs, refresh_tokens |
| `V2__seed_categories.sql` | 14 default expense categories |
| `V3__budgets.sql` | Monthly budgets table |
| `V4__transactions_dedup_constraint.sql` | Deduplication constraint |

---

## Common Issues

**Port already in use**
```bash
# Find and kill the process using port 8080
lsof -ti:8080 | xargs kill
```

**Database connection refused**
- Make sure PostgreSQL is running: `pg_isready -h localhost -p 5432`
- Check `DB_HOST`, `DB_PORT`, `DB_USERNAME`, `DB_PASSWORD` in `.env`

**Frontend can't reach the backend**
- Ensure backend is running on port 8080 (local dev) or 8081 (Docker)
- Check `proxy.conf.json` in the frontend directory — it proxies `/api` to `http://localhost:8081`

**Claude AI categorization not working**
- Verify `ANTHROPIC_API_KEY` is set correctly
- Check backend logs for `401 Unauthorized` from the Anthropic API

**Flyway migration fails**
- If you've modified a migration file after it ran, Flyway will reject startup
- To reset: `docker compose down -v` (destroys the database) and start again
