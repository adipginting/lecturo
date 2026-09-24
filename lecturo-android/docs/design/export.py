#!/usr/bin/env python3
"""Export one Penpot board to a PNG by talking to the MCP server over HTTP.

Keeps the image bytes out of the terminal/agent context: the base64 comes back
inside the tool result and is decoded straight to a file here.
"""
import base64
import json
import subprocess
import sys

URL = "http://localhost:4401/mcp"


def post(sid, body, timeout=60):
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
                       "clientInfo": {"name": "lecturo", "version": "0"}},
        }),
    ]
    headers = subprocess.run(args, capture_output=True, text=True, check=True).stdout
    for line in headers.splitlines():
        if line.lower().startswith("mcp-session-id:"):
            return line.split(":", 1)[1].strip()
    raise SystemExit("no session id")


def call(sid, tool, arguments):
    return post(sid, {
        "jsonrpc": "2.0", "id": 3, "method": "tools/call",
        "params": {"name": tool, "arguments": arguments},
    })


def main():
    board_name, out_path = sys.argv[1], sys.argv[2]
    sid = init()
    post(sid, {"jsonrpc": "2.0", "method": "notifications/initialized"})
    code = (
        "const b = penpot.currentPage.root.children.find(s => s.name === " + json.dumps(board_name) + ");"
        "if (!b) throw new Error('board not found');"
        "const bytes = await b.export({type: 'png', scale: 1});"
        "let s = ''; const CH = 8192;"
        "for (let i = 0; i < bytes.length; i += CH) { s += String.fromCharCode.apply(null, bytes.subarray(i, i + CH)); }"
        "return btoa(s);"
    )
    response = call(sid, "execute_code", {"code": code})
    text = response["result"]["content"][0]["text"]
    payload = json.loads(text)
    data = base64.b64decode(payload["result"])
    with open(out_path, "wb") as handle:
        handle.write(data)
    print(f"{board_name} -> {out_path} ({len(data)} bytes)")


if __name__ == "__main__":
    main()
