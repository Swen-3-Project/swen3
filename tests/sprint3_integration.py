"""Integration checks for an already running Docker Compose stack. No external packages."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parent.parent


def compose(*args, check=True, input=None):
    result = subprocess.run(["docker", "compose", *args], cwd=ROOT, input=input,
                            capture_output=True, text=True, check=check)
    return result.stdout.strip() if check else result.returncode == 0


def sql(statement):
    return compose("exec", "-T", "db", "sh", "-c",
                   'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -v ON_ERROR_STOP=1', input=statement)


def eventually(check, description, timeout=120):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            result = check()
            if result:
                return result
        except (urllib.error.URLError, TimeoutError, subprocess.CalledProcessError):
            pass
        time.sleep(1)
    raise AssertionError("Timed out: " + description)


def request(url, method="GET", body=None, headers=None):
    try:
        response = urllib.request.urlopen(urllib.request.Request(url, data=body, method=method,
                                                               headers=headers or {}), timeout=20)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        raw = response.read()
        return response.status, json.loads(raw) if raw and "json" in response.headers.get("Content-Type", "") else raw


def upload(url, content, filename="hello.pdf"):
    boundary = "pdf-" + uuid.uuid4().hex
    body = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\n'
            'Content-Type: application/pdf\r\n\r\n').encode() + content + f"\r\n--{boundary}--\r\n".encode()
    return request(url + "/api/documents/upload", "POST", body,
                   {"Content-Type": "multipart/form-data; boundary=" + boundary})


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", default=os.environ.get("PAPERLESS_URL", "http://localhost"))
    url = parser.parse_args().url.rstrip("/")
    pdf = (ROOT / "tests/fixtures/hello.pdf").read_bytes()
    ids = []

    def create(content=pdf):
        status, document = upload(url, content)
        assert status == 201, f"Upload returned {status}"
        id = str(uuid.UUID(document["id"]))
        ids.append(id)
        print("Uploaded test document " + id)
        assert document["mimeType"] == "application/pdf"
        assert document["fileSize"] == len(content)
        assert document["status"] == "CREATED"
        key = sql(f"SELECT storage_key FROM documents WHERE id='{id}';")
        assert key == id + ".pdf"
        digest = compose("exec", "-T", "rest", "sha256sum", "/app/uploads/" + key).split()[0]
        assert digest == hashlib.sha256(content).hexdigest()
        return id

    def received(id):
        return any("ocr_job_received" in line and id in line for line in compose("logs", "--no-color", "ocr-worker").splitlines())

    def ready():
        return request(url + "/api/documents")[0] == 200

    try:
        eventually(ready, "REST ready")
        id = create()
        eventually(lambda: received(id), "worker receipt")
        print("PASS: real PDF stored byte-for-byte and received by worker")

        before = sql("SELECT count(*) FROM documents;")
        assert upload(url, b"not a PDF")[0] == 400
        assert upload(url, b"")[0] == 400
        assert upload(url, b"%PDF-" + bytes(20 * 1024 * 1024))[0] == 413
        assert sql("SELECT count(*) FROM documents;") == before
        print("PASS: invalid, empty and oversized PDFs rejected without document creation")

        compose("stop", "rabbitmq")
        id = create()
        assert sql(f"SELECT state FROM pending_jobs WHERE document_id='{id}' AND kind='OCR_REQUESTED';") == "PENDING"
        compose("restart", "rest")
        eventually(ready, "REST restarted without broker")
        assert request(url + "/api/documents/" + id)[0] == 200
        compose("start", "rabbitmq")
        eventually(lambda: received(id), "durable pending job delivered after broker recovery")
        print("PASS: broker outage accepted; job survived REST restart and delivered on recovery")

        compose("stop", "ocr-worker")
        id = create()
        eventually(lambda: sql(f"SELECT state FROM pending_jobs WHERE document_id='{id}' AND kind='OCR_REQUESTED';") == "SENT",
                   "broker confirmed job while worker stopped")
        compose("restart", "rabbitmq")
        eventually(lambda: compose("exec", "-T", "rabbitmq", "rabbitmq-diagnostics", "-q", "ping", check=False), "broker restart")
        compose("start", "ocr-worker")
        eventually(lambda: received(id), "queued message survives broker restart")
        print("PASS: persistent queued message survived broker restart")

        user = compose("exec", "-T", "rabbitmq", "printenv", "RABBITMQ_DEFAULT_USER")
        password = compose("exec", "-T", "rabbitmq", "printenv", "RABBITMQ_DEFAULT_PASS")
        auth = {"Authorization": "Basic " + base64.b64encode((user + ":" + password).encode()).decode(), "Content-Type": "application/json"}
        manager = "http://127.0.0.1:15672/api"
        queue = manager + "/queues/%2F/paperless.ocr.dead-letter"
        old_count = request(queue, headers=auth)[1]["messages"]
        payload = json.dumps({"properties": {"content_type": "application/json", "delivery_mode": 2},
                              "routing_key": "ocr.requested", "payload": '{"schemaVersion":99}', "payload_encoding": "string"}).encode()
        assert request(manager + "/exchanges/%2F/paperless.documents/publish", "POST", payload, auth)[1]["routed"]
        eventually(lambda: request(queue, headers=auth)[1]["messages"] > old_count, "malformed message dead-lettered")
        print("PASS: malformed message sent to dead-letter queue")

        compose("stop", "rabbitmq")
        id = create()
        assert request(url + "/api/documents/" + id, "DELETE")[0] == 204
        eventually(lambda: compose("exec", "-T", "rest", "test", "!", "-e", "/app/uploads/" + id + ".pdf", check=False), "file cleanup without broker")
        assert sql(f"SELECT state FROM pending_jobs WHERE document_id='{id}' AND kind='OCR_REQUESTED';") == "CANCELLED"
        compose("start", "rabbitmq")
        print("PASS: deletion cancels unsent OCR job and removes the stored file")
    finally:
        compose("start", "rabbitmq", check=False)
        compose("start", "ocr-worker", check=False)
        for id in ids:
            try:
                status, _ = request(url + "/api/documents/" + id, "DELETE")
                if status in (204, 404):
                    eventually(lambda: compose("exec", "-T", "rest", "test", "!", "-e", "/app/uploads/" + id + ".pdf", check=False), "test PDF cleanup", timeout=30)
            except (urllib.error.URLError, TimeoutError, AssertionError):
                print("Cleanup could not finish for test document " + id)


if __name__ == "__main__":
    main()
