#!/usr/bin/env python3
"""Mock Zotero Web API for exercising the Zotero source.

Run:  python3 docs/dev/zotero_server.py     # listens on 127.0.0.1:8801
Then set Settings -> Servers -> Zotero user ID (e.g. 12345) and Zotero API base
to http://127.0.0.1:8801, store any non-empty API key, and press Sync.

Returns 403 without an Authorization header, then a two-item attachment list
(a PDF and an EPUB). Files are served from ./files.
"""
import http.server
import json
import pathlib
import socketserver

ROOT = pathlib.Path(__file__).resolve().parent / "files"
ROOT.mkdir(parents=True, exist_ok=True)

ITEMS = [
    {
        "key": "AAAA1111",
        "data": {"itemType": "attachment", "contentType": "application/pdf",
                 "title": "Phone Sample", "filename": "multipage.pdf"},
        "links": {"enclosure": {"href": "http://127.0.0.1:8801/files/multipage.pdf",
                                "type": "application/pdf"}},
    },
    {
        "key": "BBBB2222",
        "data": {"itemType": "attachment", "contentType": "application/epub+zip",
                 "title": "Sample Book", "filename": "sample.epub"},
        "links": {"enclosure": {"href": "http://127.0.0.1:8801/files/sample.epub",
                                "type": "application/epub+zip"}},
    },
]


class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        path = self.path.split("?")[0]
        if path.startswith("/users/") and path.endswith("/items"):
            if not self.headers.get("Authorization"):
                self.send_error(403, "Missing API key")
                return
            body = json.dumps(ITEMS).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        if path.startswith("/files/"):
            file = ROOT / pathlib.Path(path).name
            if file.exists():
                body = file.read_bytes()
                ctype = "application/epub+zip" if file.suffix == ".epub" else "application/pdf"
                self.send_response(200)
                self.send_header("Content-Type", ctype)
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
                return
        self.send_error(404)

    def log_message(self, *args):
        pass


socketserver.TCPServer.allow_reuse_address = True
with socketserver.TCPServer(("127.0.0.1", 8801), Handler) as httpd:
    httpd.serve_forever()
