"""End-to-end checks for the running Compose stack; Python standard library only."""
import argparse
import base64
import uuid
import json
import os
from pathlib import Path
import urllib.parse
from sprint3_integration import ROOT, compose, eventually, request, storage_request, upload


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", default=os.environ.get("PAPERLESS_URL", "http://localhost"))
    url = parser.parse_args().url.rstrip("/")
    ids = []

    def create(name):
        code, document = upload(url, (ROOT / "tests/fixtures" / name).read_bytes(), name)
        assert code == 201, (code, document)
        ids.append(document["id"])
        return document["id"]

    def detail(id):
        code, document = request(url + "/api/documents/" + id)
        assert code == 200, (code, document)
        return document

    def indexed(id):
        document = detail(id)
        assert document["status"] != "FAILED", document
        return document if document["status"] == "INDEXED" else None

    def search(term):
        code, documents = request(url + "/api/documents/search?q=" + urllib.parse.quote(term))
        assert code == 200, (code, documents)
        return documents

    try:
        hello = create("HelloWorld.pdf")
        hello_detail = eventually(lambda: indexed(hello), "HelloWorld indexed")
        assert "Hello" in hello_detail["ocrText"]
        assert any(document["id"] == hello for document in search("Hello"))
        print("PASS: HelloWorld.pdf is recognized, stored and searchable", flush=True)

        scan = create("scan.pdf")
        scanned = eventually(lambda: indexed(scan), "scanned PDF indexed")
        assert "Sonnenblume" in scanned["ocrText"]
        assert any(document["id"] == scan for document in search("Sonnenblume"))
        assert any(document["id"] == scan for document in search("Sonnenbluem"))
        print("PASS: scanned text and fuzzy matching work without filename matches", flush=True)

        code, updated = request(url + "/api/documents/" + scan, "PUT",
                                json.dumps({"title": "Changed title", "description": "Metadata update"}).encode(),
                                {"Content-Type": "application/json"})
        assert code == 200, (code, updated)
        assert detail(scan)["ocrText"] == scanned["ocrText"]
        print("PASS: editing metadata preserves OCR text", flush=True)

        user = compose("exec", "-T", "rabbitmq", "printenv", "RABBITMQ_DEFAULT_USER")
        password = compose("exec", "-T", "rabbitmq", "printenv", "RABBITMQ_DEFAULT_PASS")
        headers = {"Authorization": "Basic " + base64.b64encode((user + ":" + password).encode()).decode(),
                   "Content-Type": "application/json"}
        duplicate_jobs = [str(uuid.uuid4()), str(uuid.uuid4())]
        for job in duplicate_jobs:
            payload = {"schemaVersion": 1, "jobId": job, "documentId": scan,
                       "filename": "scan.pdf", "content": scanned["ocrText"]}
            envelope = {"properties": {"content_type": "application/json", "delivery_mode": 2},
                        "routing_key": "index.requested", "payload": json.dumps(payload), "payload_encoding": "string"}
            code, sent = request("http://localhost:15672/api/exchanges/%2F/paperless.documents/publish",
                                 "POST", json.dumps(envelope).encode(), headers)
            assert code == 200 and sent["routed"], (code, sent)
        eventually(lambda: all(any("index_job_completed" in line and job in line
                                  for line in compose("logs", "--no-color", "indexing-worker").splitlines())
                               for job in duplicate_jobs), "repeated index jobs completed")
        code, count = request("http://localhost:9200/paperless-documents/_count", "POST",
                              json.dumps({"query": {"ids": {"values": [scan]}}}).encode(),
                              {"Content-Type": "application/json"})
        assert code == 200 and count["count"] == 1, (code, count)
        assert detail(scan)["status"] == "INDEXED"
        print("PASS: repeated jobs keep one index entry and the completed status", flush=True)

        # A structurally broken PDF passes the header check, then fails actual recognition.
        code, broken = upload(url, b"%PDF-1.7\nnot a valid PDF", "broken.pdf")
        assert code == 201, (code, broken)
        ids.append(broken["id"])
        eventually(lambda: detail(broken["id"])["status"] == "FAILED", "OCR failure status")
        print("PASS: OCR errors produce a visible FAILED status", flush=True)

        # A queued index job must not recreate a document deleted while the worker is stopped.
        compose("stop", "indexing-worker")
        deleted = create("scan.pdf")
        eventually(lambda: detail(deleted)["status"] == "OCR_COMPLETED", "OCR stored before indexing")
        assert request(url + "/api/documents/" + deleted, "DELETE")[0] == 204
        compose("start", "indexing-worker")
        eventually(lambda: any("index_job_completed" in line and deleted in line
                               for line in compose("logs", "--no-color", "indexing-worker").splitlines()),
                   "queued index job completed after deletion")
        eventually(lambda: request("http://localhost:9200/paperless-documents/_doc/" + deleted)[0] == 404,
                   "deleted document absent from index")
        assert not any(document["id"] == deleted for document in search("Sonnenblume"))
        assert storage_request(deleted + ".pdf", "HEAD")[0] == 404
        print("PASS: deletion during processing does not recreate visible documents", flush=True)

        compose("stop", "elasticsearch")
        assert request(url + "/api/documents/search?q=Hello")[0] == 503
        compose("start", "elasticsearch")
        eventually(lambda: request("http://localhost:9200/_cluster/health?wait_for_status=yellow&timeout=1s")[0] == 200, "search recovery")
        eventually(lambda: request(url + "/api/documents/search?q=Hello")[0] == 200, "search API recovery")
        assert any(document["id"] == hello for document in search("Hello"))
        print("PASS: search outage is reported as HTTP 503 and recovers", flush=True)

        original = (ROOT / "tests/fixtures/scan.pdf").read_bytes()
        compose("restart", "rustfs")
        eventually(lambda: storage_request(scan + ".pdf")[0] == 200, "RustFS restarted")
        assert storage_request(scan + ".pdf")[1] == original
        print("PASS: original PDF survives a RustFS restart", flush=True)

        assert request("http://localhost:5601/api/status")[0] == 200
        query = json.dumps({"query": {"match_phrase": {"message": "ocr_result_saved"}}}).encode()

        def log_found():
            code, result = request("http://localhost:9200/paperless-logs-*/_search", "POST", query,
                                   {"Content-Type": "application/json"})
            return code == 200 and result["hits"]["total"]["value"] > 0

        eventually(log_found, "application logs indexed by Logstash", timeout=150)
        print("PASS: ELK stores structured application logs and Kibana is ready", flush=True)
    finally:
        compose("start", "elasticsearch", "indexing-worker", check=False)
        eventually(lambda: request("http://localhost:9200/_cluster/health?wait_for_status=yellow&timeout=1s")[0] == 200, "search available for cleanup")
        for id in ids:
            code, _ = request(url + "/api/documents/" + id, "DELETE")
            assert code in (204, 404), (id, code)
            assert storage_request(id + ".pdf", "HEAD")[0] == 404


if __name__ == "__main__":
    main()
