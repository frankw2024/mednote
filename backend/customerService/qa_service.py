"""Load the RotaCare HTML guide and find the nearest documented Q&A."""

from __future__ import annotations

import math
import os
import re
from dataclasses import dataclass
from difflib import SequenceMatcher
from html.parser import HTMLParser
from pathlib import Path
from typing import Iterable

DEFAULT_WINDOWS_QA_PATH = (
    r"D:\mydoc\ant\niu\college2027\summar2026\volunteer\rotaCare"
    r"\RotaCare_San_Jose_Complete_QA.html"
)
DEFAULT_WSL_QA_PATH = Path(
    "/mnt/d/mydoc/ant/niu/college2027/summar2026/volunteer/rotaCare/"
    "RotaCare_San_Jose_Complete_QA.html"
)
BUNDLED_QA_PATH = Path(__file__).with_name("RotaCare_San_Jose_Complete_QA.html")
TOKEN_RE = re.compile(r"[a-z0-9]+", re.IGNORECASE)
PHRASE_NORMALIZATIONS = (
    (re.compile(r"\btooth\s+ache\b", re.IGNORECASE), "toothache"),
    (re.compile(r"\b(?:make|book|set up)\b", re.IGNORECASE), "schedule"),
    (re.compile(r"\bvaccines?\b", re.IGNORECASE), "vaccination"),
    (re.compile(r"\bdentist\b", re.IGNORECASE), "dental"),
)
STOP_WORDS = {
    "a", "an", "and", "are", "can", "do", "does", "for", "have", "how",
    "i", "is", "it", "me", "my", "of", "or", "the", "to", "what", "when",
    "with",
}


@dataclass(frozen=True)
class QAEntry:
    number: int
    section: str
    question: str
    answer: str
    follow_up: str = ""
    source_note: str = ""


class _QAHTMLParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.entries: list[QAEntry] = []
        self.section = ""
        self.article_depth = 0
        self.capture: str | None = None
        self.buffer: list[str] = []
        self.question = self.answer = self.follow_up = self.source_note = ""

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        classes = set((dict(attrs).get("class") or "").split())
        if tag == "article" and "qa" in classes:
            self.article_depth = 1
            self.question = self.answer = self.follow_up = self.source_note = ""
            return
        if self.article_depth:
            self.article_depth += 1
            if tag == "h3":
                self._start_capture("question")
            elif tag == "p":
                if "followup" in classes:
                    self._start_capture("follow_up")
                elif "source-note" in classes:
                    self._start_capture("source_note")
                else:
                    self._start_capture("answer")
        elif tag == "h2":
            self._start_capture("section")

    def handle_endtag(self, tag: str) -> None:
        if self.capture and tag in {"h2", "h3", "p"}:
            value = " ".join("".join(self.buffer).split())
            value = re.sub(r"^\d+\.\s*", "", value)
            value = re.sub(
                r"^(?:Answer|Follow-up|Source note):\s*",
                "", value, flags=re.IGNORECASE,
            )
            setattr(self, self.capture, value)
            self.capture = None
            self.buffer = []
        if self.article_depth:
            self.article_depth -= 1
            if self.article_depth == 0 and tag == "article" and self.question and self.answer:
                self.entries.append(QAEntry(
                    number=len(self.entries) + 1,
                    section=self.section,
                    question=self.question,
                    answer=self.answer,
                    follow_up=self.follow_up,
                    source_note=self.source_note,
                ))

    def handle_data(self, data: str) -> None:
        if self.capture:
            self.buffer.append(data)

    def _start_capture(self, name: str) -> None:
        self.capture = name
        self.buffer = []


def resolve_qa_path() -> Path:
    configured = os.environ.get("ROTACARE_QA_HTML", "").strip()
    candidates = ([Path(configured)] if configured else []) + [
        Path(DEFAULT_WINDOWS_QA_PATH), DEFAULT_WSL_QA_PATH, BUNDLED_QA_PATH,
    ]
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    raise FileNotFoundError(
        "RotaCare Q&A HTML was not found. Set ROTACARE_QA_HTML or bundle "
        f"{BUNDLED_QA_PATH.name} beside qa_service.py."
    )


def load_entries(path: Path | None = None) -> list[QAEntry]:
    source = path or resolve_qa_path()
    parser = _QAHTMLParser()
    parser.feed(source.read_text(encoding="utf-8"))
    parser.close()
    if not parser.entries:
        raise ValueError(f"No Q&A articles were found in {source}")
    return parser.entries


def _tokens(value: str) -> list[str]:
    normalized = value.lower()
    for pattern, replacement in PHRASE_NORMALIZATIONS:
        normalized = pattern.sub(replacement, normalized)
    tokens = []
    for token in TOKEN_RE.findall(normalized):
        if len(token) <= 1 or token in STOP_WORDS:
            continue
        if token.endswith("s") and len(token) > 4:
            token = token[:-1]
        tokens.append(token)
    return tokens


def _idf(entries: Iterable[QAEntry]) -> dict[str, float]:
    token_sets = [set(_tokens(entry.question + " " + entry.answer)) for entry in entries]
    total = len(token_sets)
    frequencies: dict[str, int] = {}
    for tokens in token_sets:
        for token in tokens:
            frequencies[token] = frequencies.get(token, 0) + 1
    return {
        token: math.log((total + 1) / (count + 1)) + 1
        for token, count in frequencies.items()
    }


class QAIndex:
    def __init__(self, entries: list[QAEntry] | None = None) -> None:
        self.entries = entries or load_entries()
        self.idf = _idf(self.entries)

    def search(self, query: str, limit: int = 3) -> list[tuple[QAEntry, float]]:
        normalized = " ".join(query.lower().split())
        query_tokens = set(_tokens(normalized))
        if not query_tokens:
            return []
        ranked: list[tuple[QAEntry, float]] = []
        for entry in self.entries:
            question = entry.question.lower()
            question_tokens = set(_tokens(question))
            answer_tokens = set(_tokens(entry.answer))
            question_overlap = sum(
                self.idf.get(token, 1.0) for token in query_tokens & question_tokens
            )
            answer_overlap = sum(
                self.idf.get(token, 1.0) for token in query_tokens & answer_tokens
            )
            query_weight = sum(self.idf.get(token, 1.0) for token in query_tokens)
            lexical = (
                (question_overlap + 0.35 * answer_overlap) / query_weight
                if query_weight else 0.0
            )
            sequence = SequenceMatcher(None, normalized, question).ratio()
            substring = 0.25 if normalized in question or question in normalized else 0.0
            score = lexical * 0.72 + sequence * 0.28 + substring
            ranked.append((entry, min(score, 1.0)))
        ranked.sort(key=lambda item: item[1], reverse=True)
        return ranked[:max(1, min(limit, 5))]
