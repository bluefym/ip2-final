# ACADEMIQ — Student Performance Predictor

ACADEMIQ is a production-ready full-stack web application that predicts student academic risk using a rule-based model and an optional LLM advisory layer with automatic fallback.

## Tech Stack
- Frontend: HTML5, CSS3, Vanilla JavaScript (single-page app)
- Backend: Python Flask REST API
- Backend (Tomcat option): Java Spring Boot 2.7 WAR for external Tomcat 9
- Database: Oracle SQL via `cx_Oracle` (primary) with `oracledb` fallback and session pooling
- AI/ML layer: Python scoring engine + OpenAI API integration (optional)
- Charts: Chart.js

## Project Structure

```
frontend/
  index.html
  styles.css
  charts.js
backend/
  app.py
  db.py
  predictor.py
  models.py
backend-tomcat/
  pom.xml
  src/main/java/com/academiq/app/**
  src/main/resources/application.properties
  src/main/resources/static/**
sql/
  schema.sql
  seed.sql
config/
  .env.example
README.md
```

## Prerequisites
1. Python 3.10+
2. Oracle Database instance
3. Oracle Instant Client installed and available in system path (required by `cx_Oracle`)

## Setup

1. Clone or open this project.
2. Copy environment template:
   - Windows PowerShell:
     ```powershell
     Copy-Item .\config\.env.example .\.env
     ```
3. Update `.env` with your Oracle credentials.
4. Install dependencies:
   ```powershell
  pip install flask python-dotenv oracledb
   ```

Optional (if your Python version supports it and you prefer the legacy driver):

```powershell
pip install cx_Oracle
```

## Database Initialization (Oracle)

Run in SQL*Plus or SQL Developer with your target schema:

1. Execute `sql/schema.sql`
2. Execute `sql/seed.sql`

## Running the App

1. Start backend from project root:
   ```powershell
   python .\backend\app.py
   ```
2. Open browser:
   - `http://localhost:5000`

The Flask app serves the SPA from `frontend/`.

## Running on Tomcat 9 (WAR Deployment)

Use this path when you want the app to run under external Tomcat 9.

### Prerequisites
1. JDK 8+ (project is configured for Java 8 compatibility)
2. Apache Maven installed and available on PATH (`mvn -version`)
3. Apache Tomcat 9
4. Oracle DB reachable from Tomcat host

### Build WAR

From project root:

```powershell
cd .\backend-tomcat
mvn -DskipTests clean package
```

Generated artifact:
- `backend-tomcat\target\academiq-tomcat9-1.0.0.war`

### Deploy to Tomcat 9

1. Copy WAR to Tomcat webapps as `academiq.war`:
  - `%CATALINA_HOME%\webapps\academiq.war`
2. Set environment variables for Tomcat process:
  - `ORACLE_HOST`, `ORACLE_PORT`, `ORACLE_SID`, `ORACLE_USER`, `ORACLE_PASSWORD`
  - optional: `OPENAI_API_KEY`, `OPENAI_MODEL`, `OPENAI_BASE_URL`
3. Start/restart Tomcat.
4. Open:
  - `http://localhost:8080/academiq/`

### One-click deployment script (PowerShell)

From project root:

```powershell
.\scripts\deploy-tomcat.ps1 -TomcatHome "C:\Program Files\Apache Software Foundation\Tomcat 9.0" -OpenBrowser
```

Or use the launcher:

```powershell
.\scripts\deploy-tomcat.cmd -TomcatHome "C:\Program Files\Apache Software Foundation\Tomcat 9.0" -OpenBrowser
```

Useful options:
- `-SkipBuild` deploy existing WAR without rebuilding
- `-NoRestart` copy WAR only, do not stop/start Tomcat
- `-WarPath <path>` deploy a specific WAR file
- `-EnvFile <path>` load env vars from a custom `.env` file
- `-NoEnvLoad` skip `.env` loading
- `-DryRun` print actions without making changes

By default the script auto-loads `.env` from project root and exports variables to the deployment process before starting Tomcat.

Notes:
- The WAR serves the same SPA from `src/main/resources/static`.
- API base remains `/api/*` under context path, so calls resolve as `/academiq/api/*`.

## API Endpoints

- `GET /api/health`
- `POST /api/predict`
- `POST /api/predict/bulk`
- `POST /api/save`
- `GET /api/students`
- `GET /api/students/export`

All endpoints return structured JSON errors on failure.

## Prediction Engines

### A) Rule-based (always available)

Score formula:

```
score = (
  (CGPA / 10) * 30 +
  (attendance_pct / 100) * 25 +
  (assignment_marks / 100) * 20 +
  (class_behavior / 5) * 12.5 +
  (lab_behavior / 5) * 12.5
)
```

Classification:
- `score >= 70` → `CLEAR`
- `score >= 45` → `ATTENTION`
- `score < 45` → `DANGER`

### B) LLM-based suggestions

Set `OPENAI_API_KEY` and choose engine `llm` in the UI.
If API call fails, backend automatically falls back to rule-based scoring and suggestions.

## Oracle Connection & Resilience

- Oracle credentials are loaded only from these environment variables:
  - `ORACLE_HOST`, `ORACLE_PORT`, `ORACLE_SID`, `ORACLE_USER`, `ORACLE_PASSWORD`
- Connection pooling is implemented with `SessionPool` from Oracle Python driver (`cx_Oracle` when installed, otherwise `oracledb`)
- Student + prediction inserts are transactional and rollback on failure
- If Oracle is unreachable, predictions still run and UI displays:
  - `DATABASE UNAVAILABLE — PREDICTIONS WILL NOT BE SAVED`

## CSV Bulk Upload Format

Required CSV headers:
- `Student Name`
- `Roll Number`
- `CGPA`
- `Attendance %`
- `Assignment Marks`
- `Class Behavior`
- `Lab Behavior`

Bulk upload behavior:
- validates each row
- computes prediction for valid rows
- attempts DB persistence row-by-row
- returns aggregated validation and DB errors without silent failures

## UI Features

- Minimal top nav: `PREDICT` / `STUDENTS`
- Single student prediction form with 5-dot behavior selectors
- Bulk CSV processing
- Result cards with score bar, risk badge, suggestions, radar/bar charts
- Students dashboard with:
  - donut risk breakdown
  - attendance vs CGPA scatter plot
  - top-10 at-risk horizontal bar chart
- Sortable, searchable student table with inline expandable suggestion details
- Export to CSV
- Mobile responsive behavior for screens below 768px
