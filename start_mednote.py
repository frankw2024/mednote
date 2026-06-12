"""Serve MedNote on localhost so browser microphone access is permitted."""

from functools import partial
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from pathlib import Path

HOST = "127.0.0.1"
PORT = 8765
ROOT = Path(__file__).resolve().parent

class MedNoteHandler(SimpleHTTPRequestHandler):
    def end_headers(self) -> None:
        self.send_header("Permissions-Policy", "microphone=(self)")
        self.send_header("Cache-Control", "no-store")
        super().end_headers()

def main() -> None:
    handler = partial(MedNoteHandler, directory=str(ROOT))
    url = f"http://{HOST}:{PORT}/index.html"
    print(f"MedNote is running at {url}")
    ThreadingHTTPServer((HOST, PORT), handler).serve_forever()

if __name__ == "__main__":
    main()
