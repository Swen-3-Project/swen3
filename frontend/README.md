# Paperless frontend

Angular 21, Bootstrap and TypeScript. The interface is German. See the repository README for the Docker setup.

```sh
npm ci
npm start
```

Open http://localhost:4200. The development proxy forwards `/api` to http://localhost:8081, so the REST service and PostgreSQL must also be running.

```sh
npm test
npm run build
npm run e2e
```

The end-to-end tests require the complete Docker Compose stack at http://localhost/. Install Chromium once with `npx playwright install chromium`.

For the local port 8080 workaround, set `PAPERLESS_URL=http://localhost:8080` before running the browser tests. In PowerShell use `$env:PAPERLESS_URL = 'http://localhost:8080'`.
