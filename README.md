# SWEN3 Document Management System

A REST API and German web interface for document metadata and reminders. Sprint 1 provides the backend; Sprint 2 adds the website. File storage, OCR, and search are planned for later sprints.

## Stack

- Java 25 and Spring Boot
- Angular 21 and Bootstrap
- PostgreSQL 17
- Docker Compose and nginx

## Start the project

Start Docker Desktop, then run:

```sh
docker compose up --build
```

- Website: http://localhost/
- REST API: http://localhost:8081/api/documents
- Health check: http://localhost:8081/actuator/health

If port 80 is occupied, set `UI_PORT=8080` in `.env` and open http://localhost:8080/.

In IntelliJ IDEA, use the **Start Sprint 2** run configuration.

## Frontend development

With Node 24 installed and the backend running:

```sh
cd frontend
npm ci
npm start
```

Open http://localhost:4200/. Run `npm test` for frontend tests, `npm run build` for the production build, and `mvn verify` from the repository root for backend tests.
