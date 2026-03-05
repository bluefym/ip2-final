import json
import os
from typing import Any, Dict, List, Tuple
from urllib import error, request

from models import PredictionOutput, StudentInput


LLM_SYSTEM_PROMPT = (
    "You are an academic advisor AI. Given a student's data, analyze their "
    "performance and return a JSON object with:\n"
    "{\n"
    "  'risk_level': 'DANGER' | 'ATTENTION' | 'CLEAR',\n"
    "  'risk_score': <number 0-100>,\n"
    "  'summary': '<2-sentence overall assessment>',\n"
    "  'suggestions': [\n"
    "    { 'area': '<CGPA|Attendance|Assignments|Class Behavior|Lab Behavior>',\n"
    "      'issue': '<what is wrong>',\n"
    "      'recommendation': '<specific actionable step>' }\n"
    "  ]\n"
    "}\n"
    "Be specific. If attendance is below 75%, say exactly that and recommend "
    "attending at least X more classes. If CGPA is below 6.0, suggest specific "
    "study strategies. Return ONLY the JSON, no markdown."
)

VALID_RISK_LEVELS = {"DANGER", "ATTENTION", "CLEAR"}


def compute_rule_score(student: StudentInput) -> Tuple[float, str]:
    score = (
        (student.cgpa / 10.0) * 30
        + (student.attendance_pct / 100.0) * 25
        + (student.assignment_marks / 100.0) * 20
        + (student.class_behavior / 5.0) * 12.5
        + (student.lab_behavior / 5.0) * 12.5
    )

    if score >= 70:
        risk_level = "CLEAR"
    elif score >= 45:
        risk_level = "ATTENTION"
    else:
        risk_level = "DANGER"

    return round(score, 2), risk_level


def _classes_needed_for_75(attendance_pct: float) -> int:
    if attendance_pct >= 75:
        return 0
    deficit = 75 - attendance_pct
    return max(1, int(round(deficit / 2.0)))


def _default_summary(student: StudentInput, risk_level: str) -> str:
    if risk_level == "DANGER":
        return (
            f"{student.student_name} is currently in a high-risk zone and requires immediate academic support. "
            "Performance trends indicate multiple weak areas that can quickly improve with structured intervention."
        )
    if risk_level == "ATTENTION":
        return (
            f"{student.student_name} is in the attention zone with moderate performance concerns. "
            "Targeted consistency in weak metrics can move the student into the clear zone."
        )
    return (
        f"{student.student_name} is in the clear zone with strong overall academic indicators. "
        "Maintaining current habits and monitoring consistency should sustain performance."
    )


def build_default_suggestions(student: StudentInput, risk_level: str) -> List[Dict[str, str]]:
    suggestions: List[Dict[str, str]] = []

    if student.cgpa < 6.0:
        suggestions.append(
            {
                "area": "CGPA",
                "issue": f"CGPA is {student.cgpa:.2f}, below the recommended 6.0 threshold.",
                "recommendation": "Use a weekly study plan with 90-minute focused sessions for core subjects and attend one faculty office hour per week.",
            }
        )
    elif student.cgpa < 7.0:
        suggestions.append(
            {
                "area": "CGPA",
                "issue": f"CGPA is {student.cgpa:.2f}, showing room for stronger consistency.",
                "recommendation": "Increase revision frequency to three times per week and complete at least one timed practice set per subject.",
            }
        )

    if student.attendance_pct < 75:
        needed_classes = _classes_needed_for_75(student.attendance_pct)
        suggestions.append(
            {
                "area": "Attendance",
                "issue": f"Attendance is {student.attendance_pct:.2f}%, below 75%.",
                "recommendation": f"Attend at least {needed_classes} additional classes in the coming weeks and avoid missing consecutive sessions.",
            }
        )
    elif student.attendance_pct < 85:
        suggestions.append(
            {
                "area": "Attendance",
                "issue": f"Attendance is {student.attendance_pct:.2f}%, which is acceptable but not strong.",
                "recommendation": "Target at least 90% attendance this month to improve continuity and class engagement.",
            }
        )

    if student.assignment_marks < 60:
        suggestions.append(
            {
                "area": "Assignments",
                "issue": f"Assignment marks are {student.assignment_marks:.2f}, indicating weak submission quality.",
                "recommendation": "Start assignments at least 3 days before deadline and use a checklist for rubric alignment before submission.",
            }
        )
    elif student.assignment_marks < 75:
        suggestions.append(
            {
                "area": "Assignments",
                "issue": f"Assignment marks are {student.assignment_marks:.2f}, showing moderate performance.",
                "recommendation": "Incorporate one peer review cycle before submission to improve clarity and completeness.",
            }
        )

    if student.class_behavior <= 2:
        suggestions.append(
            {
                "area": "Class Behavior",
                "issue": f"Class behavior score is {student.class_behavior}/5 and reflects low classroom engagement.",
                "recommendation": "Set a goal to contribute at least once per class and maintain distraction-free seating.",
            }
        )
    if student.lab_behavior <= 2:
        suggestions.append(
            {
                "area": "Lab Behavior",
                "issue": f"Lab behavior score is {student.lab_behavior}/5 and indicates practical engagement concerns.",
                "recommendation": "Prepare a short pre-lab plan and complete post-lab notes after every session.",
            }
        )

    if not suggestions:
        suggestions.append(
            {
                "area": "General",
                "issue": "No critical weaknesses detected in current metrics.",
                "recommendation": "Maintain current routine and track weekly goals to sustain performance.",
            }
        )

    if risk_level == "DANGER" and len(suggestions) < 3:
        suggestions.append(
            {
                "area": "General",
                "issue": "Overall risk profile is high and needs immediate stabilization.",
                "recommendation": "Schedule a mentor check-in within 7 days and create a daily academic accountability plan.",
            }
        )

    return suggestions


def _extract_json_payload(content: str) -> Dict[str, Any]:
    candidate = content.strip()
    if candidate.startswith("```"):
        candidate = candidate.strip("`")
        if candidate.lower().startswith("json"):
            candidate = candidate[4:].strip()

    start = candidate.find("{")
    end = candidate.rfind("}")
    if start == -1 or end == -1:
        raise ValueError("LLM response does not contain valid JSON object.")

    return json.loads(candidate[start : end + 1])


def call_llm_prediction(student: StudentInput) -> Dict[str, Any]:
    api_key = os.getenv("OPENAI_API_KEY", "").strip()
    model = os.getenv("OPENAI_MODEL", "gpt-4o-mini").strip()
    base_url = os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1/chat/completions").strip()

    if not api_key:
        raise RuntimeError("OPENAI_API_KEY is not configured.")

    user_payload = {
        "student_name": student.student_name,
        "roll_number": student.roll_number,
        "cgpa": student.cgpa,
        "attendance_pct": student.attendance_pct,
        "assignment_marks": student.assignment_marks,
        "class_behavior": student.class_behavior,
        "lab_behavior": student.lab_behavior,
    }

    body = {
        "model": model,
        "temperature": 0.2,
        "messages": [
            {"role": "system", "content": LLM_SYSTEM_PROMPT},
            {"role": "user", "content": json.dumps(user_payload)},
        ],
    }

    payload = json.dumps(body).encode("utf-8")
    req = request.Request(
        base_url,
        data=payload,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_key}",
        },
        method="POST",
    )

    try:
        with request.urlopen(req, timeout=20) as response:
            response_body = response.read().decode("utf-8")
            parsed = json.loads(response_body)
            content = parsed["choices"][0]["message"]["content"]
            result = _extract_json_payload(content)
    except error.HTTPError as exc:
        detail = exc.read().decode("utf-8") if hasattr(exc, "read") else str(exc)
        raise RuntimeError(f"LLM HTTP error: {exc.code} {detail}") from exc
    except Exception as exc:
        raise RuntimeError(f"LLM request failed: {exc}") from exc

    risk_level = str(result.get("risk_level", "")).upper().strip()
    risk_score = float(result.get("risk_score", 0))

    if risk_level not in VALID_RISK_LEVELS:
        raise ValueError("LLM returned invalid risk_level.")
    if not (0 <= risk_score <= 100):
        raise ValueError("LLM returned out-of-range risk_score.")

    suggestions = result.get("suggestions", [])
    if not isinstance(suggestions, list):
        raise ValueError("LLM returned invalid suggestions format.")

    return {
        "risk_level": risk_level,
        "risk_score": round(risk_score, 2),
        "summary": str(result.get("summary", "")).strip() or _default_summary(student, risk_level),
        "suggestions": suggestions,
    }


def generate_prediction(student: StudentInput, engine: str = "rule") -> PredictionOutput:
    rule_score, rule_risk = compute_rule_score(student)

    if engine == "llm":
        try:
            llm_result = call_llm_prediction(student)
            return PredictionOutput(
                risk_level=llm_result["risk_level"],
                risk_score=llm_result["risk_score"],
                summary=llm_result["summary"],
                suggestions=llm_result["suggestions"],
                engine_used="llm",
                llm_failed=False,
                fallback_reason=None,
            )
        except Exception as exc:
            fallback_suggestions = build_default_suggestions(student, rule_risk)
            return PredictionOutput(
                risk_level=rule_risk,
                risk_score=rule_score,
                summary=_default_summary(student, rule_risk),
                suggestions=fallback_suggestions,
                engine_used="rule",
                llm_failed=True,
                fallback_reason=str(exc),
            )

    rule_suggestions = build_default_suggestions(student, rule_risk)
    return PredictionOutput(
        risk_level=rule_risk,
        risk_score=rule_score,
        summary=_default_summary(student, rule_risk),
        suggestions=rule_suggestions,
        engine_used="rule",
        llm_failed=False,
        fallback_reason=None,
    )
