"""Build offline Chinese and Spanish translations for RotaCare answers."""

from __future__ import annotations

import json
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

import requests

from qa_service import load_entries


HERE = Path(__file__).resolve().parent
TRANSLATIONS_PATH = HERE / "translations.json"
TARGETS = {"zh": "zh-CN", "es": "es"}


def translate(text: str, target: str) -> str:
    params = {"client": "gtx", "sl": "en", "tl": target, "dt": "t", "q": text}
    last_error: Exception | None = None
    for attempt in range(4):
        try:
            response = requests.get(
                "https://translate.googleapis.com/translate_a/single",
                params=params,
                timeout=30,
            )
            response.raise_for_status()
            parts = response.json()[0]
            translated = "".join(part[0] for part in parts if part and part[0]).strip()
            if translated:
                return translated
        except (requests.RequestException, ValueError, TypeError, IndexError) as exc:
            last_error = exc
        time.sleep(0.5 * (attempt + 1))
    raise RuntimeError(f"Translation failed for {target}: {last_error}")


def build() -> Path:
    entries = load_entries(HERE / "RotaCare_San_Jose_Complete_QA.html")
    existing = {}
    if TRANSLATIONS_PATH.exists():
        existing = json.loads(TRANSLATIONS_PATH.read_text(encoding="utf-8"))

    work = []
    for entry in entries:
        item = existing.setdefault(str(entry.number), {})
        for language, target in TARGETS.items():
            if not item.get(language):
                work.append((entry.number, language, target, entry.answer))

    with ThreadPoolExecutor(max_workers=6) as executor:
        futures = {
            executor.submit(translate, answer, target): (number, language)
            for number, language, target, answer in work
        }
        for future in as_completed(futures):
            number, language = futures[future]
            existing[str(number)][language] = future.result()
            print(f"translated Q{number} {language}")

    TRANSLATIONS_PATH.write_text(
        json.dumps(existing, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    return TRANSLATIONS_PATH


if __name__ == "__main__":
    print(build())
