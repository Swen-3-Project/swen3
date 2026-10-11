# SWEN3 Document Management System

A REST API and German web interface for PDF uploads, document metadata, and reminders.

## Stack

- Java 25 and Spring Boot
- Angular 21 and Bootstrap
- PostgreSQL 17
- RabbitMQ and a Java OCR worker
- Docker Compose and nginx

## Start the project

Start Docker Desktop, then run:

```sh
docker compose up --build
```

- Website: http://localhost/
- REST API: http://localhost:8081/api/documents
- Health check: http://localhost:8081/actuator/health
- RabbitMQ management: http://localhost:15672/

If port 80 is occupied, set `UI_PORT=8080` in `.env` and open http://localhost:8080/.

In IntelliJ IDEA, use the **Start Sprint 3** run configuration. Development credentials are in `.env.example`.

## PDF upload

Choose **PDF hochladen** on the website, or use the REST API:

```sh
curl -F "file=@invoice.pdf" -F "title=Invoice" http://localhost:8081/api/documents/upload
```

PDFs up to 20 MiB are stored in a Docker volume. Each upload creates a document and a pending OCR job in PostgreSQL. The sender publishes jobs to RabbitMQ and retries failed deliveries. The worker logs receipt; text recognition is added later. Delivery is at least once, so a retry can produce another receipt log for the same job ID.

Deleting a document cancels pending OCR jobs and schedules file cleanup. The JSON creation endpoint still creates metadata records without a file.

## Frontend development

With Node 24 installed and the backend running:

```sh
cd frontend
npm ci
npm start
```

Open http://localhost:4200/. Run `npm test` for frontend tests, `npm run build` for the production build, and `mvn verify` from the repository root for backend tests.

## Tests

Run `mvn -f ocr-worker/pom.xml verify` for worker tests. Both Java services require more than 70% line coverage.

With the containers running, run `python tests/sprint3_integration.py --url http://localhost:8080` (adjust the port), then `npm run e2e` in `frontend`, setting `PAPERLESS_URL` to the website URL. The integration test checks upload, storage, worker receipt, broker recovery, durable delivery, and file cleanup. It temporarily restarts the REST server and broker and stops the worker.
