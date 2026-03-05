import csv
import io
import os
from typing import Any, Dict, List

from flask import Flask, Response, jsonify, request, send_from_directory

try:
    from dotenv import load_dotenv

    load_dotenv()
except Exception:
    pass

from db import OracleDBManager
from models import validate_student_payload
from predictor import VALID_RISK_LEVELS, generate_prediction


app = Flask(__name__, static_folder="../frontend", static_url_path="")
db = OracleDBManager()
db.initialize_pool()


def json_error(status: int, code: str, message: str, details: Any = None) -> Response:
    payload: Dict[str, Any] = {
        "success": False,
        "error": {
            "code": code,
            "message": message,
        },
    }
    if details is not None:
        payload["error"]["details"] = details
    return jsonify(payload), status


def _serialize_prediction(prediction_obj: Any) -> Dict[str, Any]:
    return {
        "risk_level": prediction_obj.risk_level,
        "risk_score": prediction_obj.risk_score,
        "summary": prediction_obj.summary,
        "suggestions": prediction_obj.suggestions,
        "engine_used": prediction_obj.engine_used,
        "llm_failed": prediction_obj.llm_failed,
        "fallback_reason": prediction_obj.fallback_reason,
    }


def _db_warning() -> str:
    return "DATABASE UNAVAILABLE — PREDICTIONS WILL NOT BE SAVED"


@app.route("/")
def root() -> Response:
    return send_from_directory(app.static_folder, "index.html")


@app.route("/api/health", methods=["GET"])
def health() -> Response:
    available = db.is_available()
    return jsonify(
        {
            "success": True,
            "service": "ACADEMIQ",
            "database_available": available,
            "database_error": None if available else db.last_error,
            "warning": None if available else _db_warning(),
        }
    )


@app.route("/api/predict", methods=["POST"])
def predict_single() -> Response:
    if not request.is_json:
        return json_error(400, "INVALID_CONTENT_TYPE", "Request must be JSON.")

    payload = request.get_json(silent=True) or {}
    student, validation_errors = validate_student_payload(payload)
    if validation_errors:
        return json_error(400, "VALIDATION_FAILED", "Invalid student input.", validation_errors)

    engine = str(payload.get("engine", "rule")).strip().lower()
    if engine not in {"rule", "llm"}:
        return json_error(400, "INVALID_ENGINE", "Engine must be 'rule' or 'llm'.")

    prediction = generate_prediction(student, engine=engine)
    prediction_dict = _serialize_prediction(prediction)

    database_available = db.is_available()
    saved = False
    student_id = None
    prediction_id = None
    save_error = None

    if database_available:
        try:
            student_id, prediction_id = db.save_student_and_prediction(student.to_dict(), prediction_dict)
            saved = True
        except Exception as exc:
            save_error = str(exc)
    else:
        save_error = db.last_error

    return jsonify(
        {
            "success": True,
            "student": student.to_dict(),
            "prediction": prediction_dict,
            "saved": saved,
            "student_id": student_id,
            "prediction_id": prediction_id,
            "database_available": database_available,
            "warning": None if saved else (_db_warning() if not database_available else "DATABASE SAVE FAILED — PREDICTION NOT SAVED"),
            "database_error": save_error,
        }
    )


@app.route("/api/predict/bulk", methods=["POST"])
def predict_bulk() -> Response:
    uploaded_file = request.files.get("file")
    engine = str(request.form.get("engine", "rule")).strip().lower()

    if engine not in {"rule", "llm"}:
        return json_error(400, "INVALID_ENGINE", "Engine must be 'rule' or 'llm'.")

    if uploaded_file is None:
        return json_error(400, "FILE_REQUIRED", "CSV file is required in form field 'file'.")

    if not uploaded_file.filename.lower().endswith(".csv"):
        return json_error(400, "INVALID_FILE", "Only CSV files are supported.")

    raw_content = uploaded_file.read()
    try:
        decoded = raw_content.decode("utf-8-sig")
    except UnicodeDecodeError:
        return json_error(400, "INVALID_ENCODING", "CSV must be UTF-8 encoded.")

    reader = csv.DictReader(io.StringIO(decoded))
    if reader.fieldnames is None:
        return json_error(400, "INVALID_CSV", "CSV file has no header row.")

    database_available = db.is_available()

    results: List[Dict[str, Any]] = []
    errors: List[Dict[str, Any]] = []
    saved_count = 0
    processed_count = 0
    total_rows = 0

    for index, row in enumerate(reader, start=2):
        total_rows += 1
        student, validation_errors = validate_student_payload(row)
        if validation_errors:
            errors.append(
                {
                    "row": index,
                    "type": "validation",
                    "details": validation_errors,
                    "raw": row,
                }
            )
            continue

        processed_count += 1
        prediction = generate_prediction(student, engine=engine)
        prediction_dict = _serialize_prediction(prediction)

        saved = False
        row_error = None
        student_id = None
        prediction_id = None

        if database_available:
            try:
                student_id, prediction_id = db.save_student_and_prediction(student.to_dict(), prediction_dict)
                saved = True
                saved_count += 1
            except Exception as exc:
                row_error = str(exc)
                errors.append(
                    {
                        "row": index,
                        "type": "database",
                        "message": "Failed to persist row.",
                        "details": row_error,
                        "roll_number": student.roll_number,
                    }
                )

        results.append(
            {
                "row": index,
                "student": student.to_dict(),
                "prediction": prediction_dict,
                "saved": saved,
                "student_id": student_id,
                "prediction_id": prediction_id,
                "database_error": row_error,
            }
        )

    return jsonify(
        {
            "success": True,
            "total_rows": total_rows,
            "processed_count": processed_count,
            "saved_count": saved_count,
            "error_count": len(errors),
            "errors": errors,
            "results": results,
            "database_available": database_available,
            "warning": None if database_available else _db_warning(),
            "database_error": None if database_available else db.last_error,
        }
    )


@app.route("/api/save", methods=["POST"])
def save_manual() -> Response:
    if not request.is_json:
        return json_error(400, "INVALID_CONTENT_TYPE", "Request must be JSON.")

    payload = request.get_json(silent=True) or {}

    student, validation_errors = validate_student_payload(payload.get("student", {}))
    if validation_errors:
        return json_error(400, "VALIDATION_FAILED", "Invalid student input.", validation_errors)

    prediction_payload = payload.get("prediction") or {}
    risk_level = str(prediction_payload.get("risk_level", "")).upper().strip()

    try:
        risk_score = float(prediction_payload.get("risk_score"))
    except (TypeError, ValueError):
        return json_error(400, "VALIDATION_FAILED", "Prediction risk_score must be numeric.")

    if risk_level not in VALID_RISK_LEVELS:
        return json_error(400, "VALIDATION_FAILED", "Prediction risk_level is invalid.")

    if not db.is_available():
        return json_error(503, "DATABASE_UNAVAILABLE", _db_warning(), db.last_error)

    prediction = {
        "risk_level": risk_level,
        "risk_score": round(risk_score, 2),
        "summary": str(prediction_payload.get("summary", "")),
        "suggestions": prediction_payload.get("suggestions", []),
        "engine_used": str(prediction_payload.get("engine_used", "rule")),
        "llm_failed": bool(prediction_payload.get("llm_failed", False)),
        "fallback_reason": prediction_payload.get("fallback_reason"),
    }

    try:
        student_id, prediction_id = db.save_student_and_prediction(student.to_dict(), prediction)
    except Exception as exc:
        return json_error(500, "DATABASE_SAVE_FAILED", "Could not save record.", str(exc))

    return jsonify(
        {
            "success": True,
            "saved": True,
            "student_id": student_id,
            "prediction_id": prediction_id,
        }
    )


@app.route("/api/students", methods=["GET"])
def all_students() -> Response:
    search = str(request.args.get("search", "")).strip()

    if not db.is_available():
        return jsonify(
            {
                "success": True,
                "database_available": False,
                "warning": _db_warning(),
                "database_error": db.last_error,
                "students": [],
            }
        )

    try:
        students = db.fetch_all_students_with_latest_prediction(search=search or None)
    except Exception as exc:
        return json_error(500, "DATABASE_QUERY_FAILED", "Failed to load students.", str(exc))

    return jsonify(
        {
            "success": True,
            "database_available": True,
            "students": students,
        }
    )


@app.route("/api/students/export", methods=["GET"])
def export_students() -> Response:
    if not db.is_available():
        return json_error(503, "DATABASE_UNAVAILABLE", _db_warning(), db.last_error)

    try:
        students = db.fetch_all_students_with_latest_prediction()
    except Exception as exc:
        return json_error(500, "DATABASE_QUERY_FAILED", "Failed to load students.", str(exc))

    output = io.StringIO()
    writer = csv.writer(output)
    writer.writerow(
        [
            "Roll Number",
            "Student Name",
            "CGPA",
            "Attendance %",
            "Assignment Marks",
            "Class Behavior",
            "Lab Behavior",
            "Risk Score",
            "Risk Level",
            "Summary",
        ]
    )

    for student in students:
        writer.writerow(
            [
                student.get("roll_number", ""),
                student.get("student_name", ""),
                student.get("cgpa", ""),
                student.get("attendance_pct", ""),
                student.get("assignment_marks", ""),
                student.get("class_behavior", ""),
                student.get("lab_behavior", ""),
                student.get("risk_score", ""),
                student.get("risk_level", ""),
                student.get("summary", ""),
            ]
        )

    response = Response(output.getvalue(), mimetype="text/csv")
    response.headers["Content-Disposition"] = "attachment; filename=academiq_students_export.csv"
    return response


@app.route("/<path:path>")
def static_proxy(path: str) -> Response:
    return send_from_directory(app.static_folder, path)


if __name__ == "__main__":
    port = int(os.getenv("FLASK_PORT", "5000"))
    app.run(host="0.0.0.0", port=port, debug=True)
