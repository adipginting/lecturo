#!/usr/bin/env python3
"""Mock Calibre Content Server (OPDS) for exercising the Calibre source.

Run:  python3 docs/dev/opds_server.py       # listens on 127.0.0.1:8799
Then set Settings -> Servers -> Calibre Content Server URL to http://127.0.0.1:8799
and press Sync on the Calibre tab.

Serves an Atom/OPDS feed at /opds (+ /opds.xml, /search.xml) and files from
./files (put a .epub/.pdf there). One entry exposes both formats, to exercise
"one book, two rows".
"""
import http.server
import pathlib
import socketserver

ROOT = pathlib.Path(__file__).resolve().parent / "files"
ROOT.mkdir(parents=True, exist_ok=True)

FEED = """<?xml version="1.0" encoding="utf-8"?>
<feed xmlns="http://www.w3.org/2005/Atom">
  <title>Test Calibre Library</title>
  <id>urn:test:catalog</id>
  <link rel="search" type="application/atom+xml" href="http://127.0.0.1:8799/search.xml?q={searchTerms}"/>
  <entry>
    <title>Sample Book</title>
    <id>urn:test:book:1</id>
    <author><name>A. Writer</name></author>
    <content>Two formats of the same book.</content>
    <link rel="http://opds-spec.org/acquisition" type="application/epub+zip" href="/files/sample.epub"/>
    <link rel="http://opds-spec.org/acquisition" type="application/pdf" href="/files/multipage.pdf"/>
  </entry>
  <entry>
    <title>Another Book</title>
    <id>urn:test:book:2</id>
    <author><name>B. Author</name></author>
    <content>An EPUB only.</content>
    <link rel="http://opds-spec.org/acquisition" type="application/epub+zip" href="/files/sample.epub"/>
  </entry>
  <entry>
    <title>Back to Root</title>
    <id>urn:test:root</id>
    <link rel="subsection" type="application/atom+xml;profile=opds-catalog" href="/opds.xml"/>
  </entry>
</feed>
"""


class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        path = self.path.split("?")[0]
        if path in ("/", "/opds", "/opds.xml", "/search.xml"):
            body = FEED.encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/atom+xml; charset=utf-8")
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
with socketserver.TCPServer(("127.0.0.1", 8799), Handler) as httpd:
    httpd.serve_forever()
