# Rock Paper Scissors Battle

A web game where the player chooses rock, paper, or scissors, then watches 30 animated pieces battle until one type remains.

## Stack

- Backend: Kotlin + Ktor
- Database: PostgreSQL
- Frontend: React + TypeScript + Vite
- Local orchestration: Docker Compose

## Run With Docker

From this folder:

```bash
docker compose up --build
```

Then open:

- Frontend: http://localhost:3000
- Backend health: http://localhost:8080/api/health

PostgreSQL is started by Docker Compose and stores game sessions in the `game_sessions` table.
The React app talks to the backend at `http://localhost:8080/api`.

If the backend container is already running and you only changed the frontend:

```bash
docker compose up frontend --build
```

## Try A No-Install Preview

If Docker, Java, Gradle, or Node are not installed yet, open this file directly in a browser:

```text
preview/index.html
```

This preview lets you try the swipeable cards and animated battle. It is a standalone browser preview, so it does not use the Kotlin backend or PostgreSQL database.

## Run Locally Without Docker

Start PostgreSQL first. One local option is:

```bash
docker run --name rpsbattle-postgres \
  -e POSTGRES_DB=rpsbattle \
  -e POSTGRES_USER=rps_user \
  -e POSTGRES_PASSWORD=rps_password \
  -p 5432:5432 \
  postgres:16-alpine
```

Then set these environment variables for the backend:

```bash
DB_URL=jdbc:postgresql://localhost:5432/rpsbattle
DB_USER=rps_user
DB_PASSWORD=rps_password
PORT=8080
```

Backend:

```bash
cd backend
gradle run
```

Frontend:

```bash
cd frontend
npm install
npm run dev
```

The Vite dev server runs on http://localhost:3000 and proxies `/api` to `http://localhost:8080`.

Build React web:

```bash
cd frontend
npm install
npm run build
```

## Test

Run backend tests:

```bash
cd backend
gradle test
```

Run one test class:

```bash
cd backend
gradle test --tests com.example.rpsbattle.ApplicationTest
```

Run frontend checks:

```bash
cd frontend
npm install
npm test
npm run build
```

## Verify In Browser And Curl

Browser:

1. Open http://localhost:3000.
2. Confirm the page shows `API: http://localhost:8080/api` when running through Docker Compose.
3. Choose Rock, Paper, or Scissors.
4. Click `Start session`.
5. Confirm the Session panel shows a UUID, player choice, and `started` status.
6. Under `Play opponent move`, click Rock, Paper, or Scissors.
7. If the round is not a tie, confirm the session changes to `completed` and shows the winner.

Backend health:

```bash
curl http://localhost:8080/api/health
```

PowerShell-safe curl health check:

```powershell
curl.exe --% http://localhost:8080/api/health
```

CORS preflight from the frontend origin:

```powershell
curl.exe --% -i -X OPTIONS http://localhost:8080/api/sessions -H "Origin: http://localhost:3000" -H "Access-Control-Request-Method: POST" -H "Access-Control-Request-Headers: Content-Type"
```

Create a session:

```powershell
curl.exe --% -i -X POST http://localhost:8080/api/sessions -H "Content-Type: application/json" -d "{\"playerChoice\":\"rock\"}"
```

Complete a session, replacing `<session-id>` with the id from the create response:

```powershell
curl.exe --% -i -X POST http://localhost:8080/api/sessions/<session-id>/complete -H "Content-Type: application/json" -d "{\"winner\":\"rock\",\"finalRockCount\":30,\"finalPaperCount\":0,\"finalScissorsCount\":0,\"transformations\":0,\"durationMs\":0}"
```

## API

Full endpoint documentation with examples is in [API.md](API.md).

- `GET /api/health`
- `GET /api/rules`
- `POST /api/sessions`
- `GET /api/sessions/{id}`
- `POST /api/sessions/{id}/complete`
- `GET /api/sessions?limit=10`

The frontend creates a session after the player picks a card and completes it when the battle has a single winner.
