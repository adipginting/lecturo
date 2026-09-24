#!/usr/bin/env python3
"""Create the desktop UI boards in the Penpot file via the MCP server.

The Penpot MCP plugin must be connected to the file (the same setup
docs/design/export.py uses). Reads docs/penpot/desktop_boards.js and runs it
with the plugin's `execute_code` tool.

    python3 docs/penpot/create_desktop_boards.py
"""
import json
import pathlib
import subprocess
import sys

URL = "http://localhost:4401/mcp"
HERE = pathlib.Path(__file__).resolve().parent


def post(sid, body, timeout=120):
    args = [
        "curl", "-s", "--max-time", str(timeout), "-X", "POST", URL,
        "-H", "Content-Type: application/json",
        "-H", "Accept: application/json, text/event-stream",
        "-d", json.dumps(body),
    ]
    if sid:
        args[4:4] = ["-H", f"mcp-session-id: {sid}"]
    out = subprocess.run(args, capture_output=True, text=True, check=True).stdout
    for line in out.splitlines():
        if line.startswith("data: "):
            return json.loads(line[6:])
    return json.loads(out) if out.strip() else None


def init():
    args = [
        "curl", "-sD", "-", "-o", "/dev/null", "--max-time", "10", "-X", "POST", URL,
        "-H", "Content-Type: application/json",
        "-H", "Accept: application/json, text/event-stream",
        "-d", json.dumps({
            "jsonrpc": "2.0", "id": 1, "method": "initialize",
            "params": {"protocolVersion": "2025-06-18", "capabilities": {},
                       "clientInfo": {"name": "lecturo-desktop", "version": "0"}},
        }),
    ]
    headers = subprocess.run(args, capture_output=True, text=True, check=True).stdout
    for line in headers.splitlines():
        if line.lower().startswith("mcp-session-id:"):
            return line.split(":", 1)[1].strip()
    raise SystemExit("no session id — is the Penpot MCP plugin connected?")


def main():
    code = (HERE / "desktop_boards.js").read_text()
    sid = init()
    post(sid, {"jsonrpc": "2.0", "method": "notifications/initialized"})
    response = post(sid, {
        "jsonrpc": "2.0", "id": 3, "method": "tools/call",
        "params": {"name": "execute_code", "arguments": {"code": code}},
    })
    content = response.get("result", {}).get("content", [])
    if not content:
        print(json.dumps(response, indent=2), file=sys.stderr)
        raise SystemExit("unexpected MCP response")
    print(content[0].get("text", ""))


if __name__ == "__main__":
    main()
