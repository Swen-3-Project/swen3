"""Integration checks for an already running Docker Compose stack. No external packages."""
import argparse
import base64
import hashlib
import hmac
import datetime
import urllib.parse
import xml.etree.ElementTree as ET
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


_storage_credentials = None


def storage_request(key="", method="GET", query=""):
    global _storage_credentials
    if _storage_credentials is None:
        _storage_credentials = (
            compose("exec", "-T", "rest", "printenv", "S3_ACCESS_KEY"),
            compose("exec", "-T", "rest", "printenv", "S3_SECRET_KEY"),
        )
    access, secret = _storage_credentials
    now = datetime.datetime.now(datetime.timezone.utc)
    stamp, day = now.strftime("%Y%m%dT%H%M%SZ"), now.strftime("%Y%m%d")
    host = "localhost:" + compose("port", "rustfs", "9000").rsplit(":", 1)[1]
    path = "/documents" + ("/" + urllib.parse.quote(key) if key else "")
    payload_hash = hashlib.sha256(b"").hexdigest()
    signed = "host;x-amz-content-sha256;x-amz-date"
    canonical_headers = f"host:{host}\nx-amz-content-sha256:{payload_hash}\nx-amz-date:{stamp}\n"
    canonical = f"{method}\n{path}\n{query}\n{canonical_headers}\n{signed}\n{payload_hash}"
    scope = day + "/us-east-1/s3/aws4_request"
    to_sign = "AWS4-HMAC-SHA256\n" + stamp + "\n" + scope + "\n" + hashlib.sha256(canonical.encode()).hexdigest()

    def sign(key_bytes, text):
        return hmac.new(key_bytes, text.encode(), hashlib.sha256).digest()

    signing = sign(sign(sign(sign(("AWS4" + secret).encode(), day), "us-east-1"), "s3"), "aws4_request")
    signature = hmac.new(signing, to_sign.encode(), hashlib.sha256).hexdigest()
    headers = {
        "x-amz-date": stamp, "x-amz-content-sha256": payload_hash,
        "Authorization": f"AWS4-HMAC-SHA256 Credential={access}/{scope}, SignedHeaders={signed}, Signature={signature}",
    }
    return request("http://" + host + path + ("?" + query if query else ""), method, headers=headers)


def storage_count():
    status, body = storage_request(query="list-type=2")
    assert status == 200, f"RustFS listing returned {status}"
    return len(ET.fromstring(body).findall("{*}Contents"))


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

    def create(content=pdf, wait_for_broker=False):
        status, document = upload(url, content)
        if wait_for_broker and status == 503:
            return None
        assert status == 201, f"Upload returned {status}"
        id = str(uuid.UUID(document["id"]))
        ids.append(id)
        print("Uploaded test document " + id)
        assert document["mimeType"] == "application/pdf"
        assert document["fileSize"] == len(content)
        assert document["status"] == "CREATED"
        key = sql(f"SELECT storage_key FROM documents WHERE id='{id}';")
        assert key == id + ".pdf"
        stored_status, stored = storage_request(key)
        assert stored_status == 200
        assert stored == content
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

        before = sql("SELECT count(*) FROM documents;")
        files_before = storage_count()
        compose("stop", "rabbitmq")
        assert upload(url, pdf)[0] == 503
        assert storage_count() == files_before
        assert sql("SELECT count(*) FROM documents;") == before
        compose("start", "rabbitmq")
        eventually(lambda: compose("exec", "-T", "rabbitmq", "rabbitmq-diagnostics", "-q", "ping", check=False), "broker recovery")
        id = eventually(lambda: create(wait_for_broker=True), "manual upload after broker recovery")
        eventually(lambda: received(id), "manual retry after broker recovery")
        print("PASS: broker outage returns 503; a manual retry succeeds")

        compose("stop", "ocr-worker")
        id = create()
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

        id = create()
        eventually(lambda: received(id), "last worker receipt")
        compose("stop", "rabbitmq")
        assert request(url + "/api/documents/" + id, "DELETE")[0] == 204
        eventually(lambda: storage_request(id + ".pdf", "HEAD")[0] == 404, "file cleanup without broker")
        compose("start", "rabbitmq")
        print("PASS: deletion removes the stored file directly")
    finally:
        compose("start", "rabbitmq", check=False)
        compose("start", "ocr-worker", check=False)
        for id in ids:
            try:
                status, _ = request(url + "/api/documents/" + id, "DELETE")
                if status in (204, 404):
                    eventually(lambda: storage_request(id + ".pdf", "HEAD")[0] == 404, "test PDF cleanup", timeout=30)
            except (urllib.error.URLError, TimeoutError, AssertionError):
                print("Cleanup could not finish for test document " + id)


if __name__ == "__main__":
    main()
