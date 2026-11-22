# SWEN3 Document Management System

A REST API and German web interface for PDF uploads, OCR, full-text search, Gemini summaries, document metadata, and reminders.

## Start

Start Docker Desktop, then run:

```sh
docker compose up --build
```

- Website: http://localhost:8080/
- REST API: http://localhost:8081/api/documents
- RabbitMQ management: http://localhost:15672/
- RustFS console: http://localhost:19001/
- Elasticsearch: http://localhost:9200/
- Kibana: http://localhost:5601/

Set `UI_PORT` in `.env` to use another website port. RustFS uses configurable host ports `S3_PORT` (19000) and `S3_CONSOLE_PORT` (19001). Development credentials are listed in `.env.example`.

In IntelliJ IDEA, use **Start Sprint 5**.

## Upload and search

Choose **PDF hochladen**, select a PDF up to 20 MiB, and submit it. Use **Aktualisieren** on its detail page to see the recognized text and processing status. Search document contents on the document list; small typing mistakes are supported.

```sh
curl -F "file=@invoice.pdf" -F "title=Invoice" http://localhost:8081/api/documents/upload
curl "http://localhost:8081/api/documents/search?q=Hello"
```

The REST service stores original PDFs in RustFS through its S3 API and metadata in PostgreSQL. It sends an OCR job directly to RabbitMQ after the document is saved. The OCR worker renders PDF pages with Poppler and recognizes German and English text with Tesseract. Results return through RabbitMQ. The REST service stores the text and sends it to the separate indexing worker, which writes it to Elasticsearch.

The status changes from `CREATED` to `OCR_COMPLETED` and `INDEXED`. Failed processing is marked `FAILED`. Workers try processing three times before reporting failure and rejecting the message to a dead-letter queue. RabbitMQ stores accepted messages durably; repeated indexing replaces the same document ID.

If RabbitMQ cannot accept an upload job, the API returns HTTP 503 and cleans up the failed upload. Retry the upload manually. Storage and search outages also produce controlled error responses. Deleting a document directly removes its file, search entry, metadata, and reminders. The JSON creation endpoint still creates metadata without a file.

Files from earlier local storage are not imported automatically; upload them again when needed.

## PDF summaries

Set `GEMINI_API_KEY` in your local `.env` and run `docker compose up -d --build`. The worker uses `gemini-3.1-flash-lite` by default. Set `GEMINI_MODEL` to use another Gemini model.

After OCR, REST queues the text for indexing and summarizing. The GenAI worker sends the first 50,000 characters of OCR text to Gemini, then returns a short German summary through RabbitMQ. REST stores it in PostgreSQL. Open the document and click **Aktualisieren** to see **Zusammenfassung**.

With no key configured, uploads, OCR, and search work and the GenAI worker skips summary jobs. After setting the key, upload a PDF to try it. Temporary Gemini failures are retried up to three attempts. Failed jobs go to the dead-letter queue and can be traced by document ID in the logs.

## Android demo

Start Docker Compose on port 8080. Run an Android emulator and install the included debug APK. In Paperless, choose **PDF hochladen**, pick `tests/fixtures/scan.pdf` after copying it onto the emulator, and upload it. Open the detail page and click **Aktualisieren** to see the summary.

To build the APK, use Android Studio with SDK Platform 33, Build Tools 34.0.0, and JDK 17 or 21. Open the `mobile` directory, or run `gradlew.bat assembleDebug` there on Windows (`sh ./gradlew assembleDebug` on macOS or Linux). The result is at `mobile/app/build/outputs/apk/debug/app-debug.apk`.

The emulator APK connects to `http://10.0.2.2:8080`. For a phone on the same network, build with `-PpaperlessUrl=http://<computer-ip>:8080`.

## Logs

Java services write structured JSON logs containing document and job IDs. Logstash reads the log volume and writes to `paperless-logs-*` in Elasticsearch. In Kibana, create a data view for that pattern with `@timestamp` as the time field, then use Discover to inspect processing events.

## Development and tests

The project uses Java 25, Spring Boot, PostgreSQL 17, RabbitMQ, Angular 21, Bootstrap, and Docker Compose. Frontend development requires Node 24:

```sh
cd frontend
npm ci
npm start
```

Open http://localhost:4200/ with the backend running. Run `npm test`, `npm run build`, and `npm run e2e:typecheck` in `frontend`.

From the repository root:

```sh
mvn verify
mvn -f ocr-worker/pom.xml verify
mvn -f indexing-worker/pom.xml verify
mvn -f genai-worker/pom.xml verify
```

Each Java application requires more than 70% line coverage. Reports are generated under its `target/site/jacoco/` directory.

With all containers running, run integration checks sequentially:

```sh
python tests/sprint3_integration.py --url http://localhost:8080
python tests/sprint4_integration.py --url http://localhost:8080
```

With a Gemini key configured, run `python tests/sprint5_integration.py` to verify a summary in the API and database. Adjust the URL to the configured UI port. These checks upload and remove test documents and temporarily stop or restart the broker, workers, Elasticsearch, and RustFS.

For browser tests, set `PAPERLESS_URL` to the website URL and run `npm run e2e` in `frontend`. The tests cover metadata and reminders, PDF upload, real OCR, full-text search, and fuzzy matching. `tests/fixtures/scan.pdf` contains only a scanned image to verify recognition without a PDF text layer.
