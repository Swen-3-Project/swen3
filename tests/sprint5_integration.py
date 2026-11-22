"""Check the summary flow on a running stack configured for Gemini."""
import argparse
import os
import urllib.parse
import uuid

from sprint3_integration import ROOT, eventually, request, sql, upload


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", default=os.environ.get("PAPERLESS_URL", "http://localhost:8080"))
    parser.add_argument("--expected-summary")
    args = parser.parse_args()
    base = args.url.rstrip("/")
    pdf = ROOT / "tests/fixtures/scan.pdf"
    code, document = upload(base, pdf.read_bytes(), pdf.name)
    assert code == 201, (code, document)
    document_id = str(uuid.UUID(document["id"]))
    detail_url = f"{base}/api/documents/{document_id}"

    def completed():
        status, detail = request(detail_url)
        assert status == 200, status
        assert detail["status"] != "FAILED", "OCR or indexing failed"
        if detail["status"] == "INDEXED" and detail["summary"]:
            return detail

    try:
        detail = eventually(completed, "summary saved and document indexed", timeout=150)
        assert "Sonnenblume" in detail["ocrText"]
        if args.expected_summary:
            assert detail["summary"] == args.expected_summary
        assert sql(f"SELECT summary FROM documents WHERE id = '{document_id}';") == detail["summary"]
        query = urllib.parse.urlencode({"q": "Sonnenblume"})
        code, hits = request(f"{base}/api/documents/search?{query}")
        assert code == 200 and any(hit["id"] == document_id for hit in hits)
        print("PASS: summary matches PostgreSQL and PDF is searchable")
    finally:
        status, _ = request(detail_url, "DELETE")
        assert status in (204, 404), status


if __name__ == "__main__":
    main()
