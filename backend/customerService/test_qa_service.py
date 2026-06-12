"""Focused tests for RotaCare nearest-Q&A matching."""

from customerService.qa_service import QAIndex, load_entries


def test_loads_complete_guide():
    entries = load_entries()
    assert len(entries) == 79
    assert entries[0].question
    assert not entries[0].question.startswith("1.")
    assert entries[-1].answer


def test_toothache_routes_to_dental_answer():
    best, score = QAIndex().search("I have a tooth ache", limit=1)[0]
    assert "toothache" in best.question.lower()
    assert "dental services" in best.answer.lower()
    assert score > 0.35


def test_schedule_routes_to_appointment_answer():
    best, _ = QAIndex().search("How can I make an appointment?", limit=1)[0]
    assert "schedule an appointment" in best.question.lower()
    assert "408-715-3088" in best.answer


def test_office_routes_to_clinic_location():
    best, _ = QAIndex().search("where is your office", limit=1)[0]
    assert best.number == 30
    assert "100 Oak Street" in best.answer
