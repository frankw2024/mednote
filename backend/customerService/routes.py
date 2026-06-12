"""FastAPI routes for RotaCare customer-service questions."""

from __future__ import annotations

import json
import os
from functools import lru_cache
from pathlib import Path

import requests
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from .qa_service import QAIndex, resolve_qa_path

router = APIRouter(prefix="/api/customer-service", tags=["customer-service"])
LANGUAGE_NAMES = {"zh": "Simplified Chinese", "es": "Spanish"}
TRANSLATIONS = json.loads(
    Path(__file__).with_name("translations.json").read_text(encoding="utf-8")
)


class AskPayload(BaseModel):
    question: str = Field(min_length=2, max_length=1000)


class TranslatePayload(BaseModel):
    text: str = Field(min_length=1, max_length=6000)
    language: str


@lru_cache(maxsize=1)
def get_index() -> QAIndex:
    return QAIndex()


@router.get("/health")
def customer_service_health():
    try:
        index = get_index()
        return {
            "ok": True,
            "questions": len(index.entries),
            "source": str(resolve_qa_path()),
            "translationConfigured": bool(os.environ.get("GROQ_API_KEY", "").strip()),
        }
    except (FileNotFoundError, ValueError) as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc


@router.post("/ask")
def ask_customer_service(payload: AskPayload):
    try:
        matches = get_index().search(payload.question, limit=3)
    except (FileNotFoundError, ValueError) as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    if not matches:
        raise HTTPException(status_code=400, detail="Please enter a more specific question.")
    best, score = matches[0]
    return {
        "match": _serialize_match(best, score),
        "alternatives": [_serialize_match(entry, value) for entry, value in matches[1:]],
    }


@router.post("/translate")
def translate_customer_service(payload: TranslatePayload):
    language = payload.language.strip().lower()
    if language not in LANGUAGE_NAMES:
        raise HTTPException(status_code=400, detail="Supported languages are zh and es.")
    api_key = os.environ.get("GROQ_API_KEY", "").strip()
    if not api_key:
        raise HTTPException(status_code=503, detail="Server translation is not configured. Add GROQ_API_KEY.")
    try:
        response = requests.post(
            "https://api.groq.com/openai/v1/chat/completions",
            headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
            json={
                "model": os.environ.get("CUSTOMER_SERVICE_TRANSLATION_MODEL", "llama-3.3-70b-versatile"),
                "messages": [
                    {"role": "system", "content": (
                        f"Translate customer-service answers into {LANGUAGE_NAMES[language]}. "
                        "Preserve phone numbers, addresses, times, eligibility rules, and "
                        "medical-service limitations exactly. Return only the translation."
                    )},
                    {"role": "user", "content": payload.text},
                ],
                "temperature": 0.0,
                "max_tokens": 1800,
            },
            timeout=30,
        )
        response.raise_for_status()
        translated = response.json()["choices"][0]["message"]["content"].strip()
    except (requests.RequestException, KeyError, IndexError, TypeError, ValueError) as exc:
        raise HTTPException(status_code=502, detail="Translation service failed.") from exc
    return {"language": language, "text": translated}


def _serialize_match(entry, score: float) -> dict:
    return {
        "number": entry.number,
        "section": entry.section,
        "question": entry.question,
        "answer": entry.answer,
        "followUp": entry.follow_up or None,
        "sourceNote": entry.source_note or None,
        "answerZh": TRANSLATIONS[str(entry.number)]["zh"],
        "answerEs": TRANSLATIONS[str(entry.number)]["es"],
        "score": round(score, 4),
    }
