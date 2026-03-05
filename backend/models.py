from dataclasses import dataclass, asdict
from typing import Any, Dict, List, Optional, Tuple


@dataclass
class StudentInput:
    student_name: str
    roll_number: str
    cgpa: float
    attendance_pct: float
    assignment_marks: float
    class_behavior: int
    lab_behavior: int

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


@dataclass
class Suggestion:
    area: str
    issue: str
    recommendation: str


@dataclass
class PredictionOutput:
    risk_level: str
    risk_score: float
    summary: str
    suggestions: List[Dict[str, str]]
    engine_used: str
    llm_failed: bool = False
    fallback_reason: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


CSV_KEY_ALIASES = {
    "student name": "student_name",
    "student_name": "student_name",
    "name": "student_name",
    "roll number": "roll_number",
    "roll_number": "roll_number",
    "roll": "roll_number",
    "cgpa": "cgpa",
    "attendance %": "attendance_pct",
    "attendance_pct": "attendance_pct",
    "attendance": "attendance_pct",
    "assignment marks": "assignment_marks",
    "assignment_marks": "assignment_marks",
    "assignments": "assignment_marks",
    "class behavior": "class_behavior",
    "class_behavior": "class_behavior",
    "lab behavior": "lab_behavior",
    "lab_behavior": "lab_behavior",
}


def normalize_input_keys(payload: Dict[str, Any]) -> Dict[str, Any]:
    normalized: Dict[str, Any] = {}
    for key, value in payload.items():
        lookup = str(key).strip().lower()
        normalized_key = CSV_KEY_ALIASES.get(lookup, lookup)
        normalized[normalized_key] = value
    return normalized


def _to_float(value: Any, field_name: str, errors: List[Dict[str, str]]) -> Optional[float]:
    try:
        if value is None or str(value).strip() == "":
            errors.append({"field": field_name, "message": "Field is required."})
            return None
        return float(value)
    except (TypeError, ValueError):
        errors.append({"field": field_name, "message": "Must be a numeric value."})
        return None


def _to_int(value: Any, field_name: str, errors: List[Dict[str, str]]) -> Optional[int]:
    try:
        if value is None or str(value).strip() == "":
            errors.append({"field": field_name, "message": "Field is required."})
            return None
        return int(float(value))
    except (TypeError, ValueError):
        errors.append({"field": field_name, "message": "Must be an integer value."})
        return None


def validate_student_payload(raw_payload: Dict[str, Any]) -> Tuple[Optional[StudentInput], List[Dict[str, str]]]:
    payload = normalize_input_keys(raw_payload)
    errors: List[Dict[str, str]] = []

    student_name = str(payload.get("student_name", "")).strip()
    roll_number = str(payload.get("roll_number", "")).strip()

    if not student_name:
        errors.append({"field": "student_name", "message": "Student Name is required."})
    if not roll_number:
        errors.append({"field": "roll_number", "message": "Roll Number is required."})

    cgpa = _to_float(payload.get("cgpa"), "cgpa", errors)
    attendance_pct = _to_float(payload.get("attendance_pct"), "attendance_pct", errors)
    assignment_marks = _to_float(payload.get("assignment_marks"), "assignment_marks", errors)
    class_behavior = _to_int(payload.get("class_behavior"), "class_behavior", errors)
    lab_behavior = _to_int(payload.get("lab_behavior"), "lab_behavior", errors)

    if cgpa is not None and not (0 <= cgpa <= 10):
        errors.append({"field": "cgpa", "message": "CGPA must be between 0 and 10."})

    if attendance_pct is not None and not (0 <= attendance_pct <= 100):
        errors.append({"field": "attendance_pct", "message": "Attendance must be between 0 and 100."})

    if assignment_marks is not None and not (0 <= assignment_marks <= 100):
        errors.append({"field": "assignment_marks", "message": "Assignment Marks must be between 0 and 100."})

    if class_behavior is not None and not (1 <= class_behavior <= 5):
        errors.append({"field": "class_behavior", "message": "Class Behavior must be between 1 and 5."})

    if lab_behavior is not None and not (1 <= lab_behavior <= 5):
        errors.append({"field": "lab_behavior", "message": "Lab Behavior must be between 1 and 5."})

    if errors:
        return None, errors

    return (
        StudentInput(
            student_name=student_name,
            roll_number=roll_number,
            cgpa=round(float(cgpa), 2),
            attendance_pct=round(float(attendance_pct), 2),
            assignment_marks=round(float(assignment_marks), 2),
            class_behavior=int(class_behavior),
            lab_behavior=int(lab_behavior),
        ),
        [],
    )
