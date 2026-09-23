# SWEN3 Document Management System

Sprint 1 provides a Java REST service for document metadata and reminders. The course brief and rating matrix are at the repository root. Document content storage, OCR, search, messaging, and the user interface belong to later sprints.

## Stack

- Java 25, Spring Boot 4.1.1, Spring MVC, Spring Data JPA, Hibernate, and MapStruct
- PostgreSQL 17 with versioned Flyway migrations
- Maven for builds and JUnit, Mockito, and Spring MVC tests
- Docker Compose for the REST service and database

## Run locally

Install Docker Desktop with a working Docker engine, or install Java 25, Maven 3.9+, and PostgreSQL 17. The default database credentials in `compose.yaml` and `application.yml` are for local development only. Copy `.env.example` to `.env` to override them.

With Docker:

```sh
docker compose up --build
```

The REST service listens at `http://localhost:8081`. Check `http://localhost:8081/actuator/health` after startup. PostgreSQL data is stored in the `postgres_data` named volume, so `docker compose down` keeps it. Use `docker compose down --volumes` only when you intend to erase local data.

With a local Java and PostgreSQL installation, create a `paperless` database and set `DB_URL`, `DB_USER`, and `DB_PASSWORD`, then run:

```sh
mvn verify
mvn spring-boot:run
```

Flyway applies `V1__create_documents.sql` and `V2__create_reminders.sql` at startup. Hibernate validates the resulting schema. Tests mock repositories and do not need a running database.

`mvn verify` runs the test suite, creates the executable JAR, and writes a JaCoCo coverage report to `target/site/jacoco/index.html`. The Sprint 1 release has 18 passing tests and 88.4% line coverage.

## REST API

All timestamps are UTC. IDs are UUIDs. Invalid request bodies return HTTP 400, and missing documents or reminders return HTTP 404. Document records in this sprint store metadata only; `fileSize` and `mimeType` describe a file but no bytes are uploaded.

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/documents` | Create document metadata |
| `GET` | `/api/documents` | List documents |
| `GET` | `/api/documents/{id}` | Get a document |
| `PUT` | `/api/documents/{id}` | Update title and description |
| `DELETE` | `/api/documents/{id}` | Delete document and its reminders |
| `POST` | `/api/documents/{id}/reminders` | Create a reminder |
| `GET` | `/api/documents/{id}/reminders` | List reminders |
| `PATCH` | `/api/documents/{id}/reminders/{reminderId}/complete` | Complete a reminder |
| `GET` | `/api/documents/{id}/reminders/{reminderId}/history` | View reminder events |

Example requests (replace the UUIDs in later commands with returned values):

```sh
curl -i -H 'Content-Type: application/json' -d '{"filename":"contract.pdf","mimeType":"application/pdf","fileSize":12345,"title":"Contract"}' http://localhost:8081/api/documents
curl -i http://localhost:8081/api/documents
curl -i -X PUT -H 'Content-Type: application/json' -d '{"title":"Signed contract","description":"Reviewed"}' http://localhost:8081/api/documents/DOCUMENT_ID
curl -i -H 'Content-Type: application/json' -d '{"title":"Renew contract","dueDate":"2026-12-01"}' http://localhost:8081/api/documents/DOCUMENT_ID/reminders
curl -i -X PATCH http://localhost:8081/api/documents/DOCUMENT_ID/reminders/REMINDER_ID/complete
curl -i http://localhost:8081/api/documents/DOCUMENT_ID/reminders/REMINDER_ID/history
```

Completing a reminder twice returns the completed reminder without writing a duplicate completion event. Deleting a document removes its reminders and history through database foreign keys.

## Project structure

`document` contains the document controller, service, repository, MapStruct mapper, entity, and DTOs. `reminder` follows the same pattern and adds an event history repository. `common` contains HTTP error handling. Migrations are in `src/main/resources/db/migration`; tests are in `src/test/java`.

## Team workflow

`main` contains the accepted Sprint 1 release. `dev` is the integration branch, `feature/document-crud` and `feature/reminders` retain the individual feature commits, and `release/sprint-1` records the tested release. The feature branches were merged through pull requests #52 and #53, followed by release pull request #54 into `main`. Sprint 1 issues were closed by that release merge. Future work should follow the same branch, review, CI, and release sequence.
