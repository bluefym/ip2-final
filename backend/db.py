import json
import importlib
import os
from typing import Any, Dict, List, Optional, Tuple

try:
    oracle_driver = importlib.import_module("cx_Oracle")
except ImportError:
    oracle_driver = importlib.import_module("oracledb")


class OracleDBManager:
    def __init__(self) -> None:
        self.pool: Optional[Any] = None
        self.last_error: Optional[str] = None

    def _required_env(self) -> Dict[str, str]:
        return {
            "ORACLE_HOST": os.getenv("ORACLE_HOST", "").strip(),
            "ORACLE_PORT": os.getenv("ORACLE_PORT", "").strip(),
            "ORACLE_SID": os.getenv("ORACLE_SID", "").strip(),
            "ORACLE_USER": os.getenv("ORACLE_USER", "").strip(),
            "ORACLE_PASSWORD": os.getenv("ORACLE_PASSWORD", "").strip(),
        }

    def is_configured(self) -> bool:
        return all(bool(value) for value in self._required_env().values())

    def initialize_pool(self) -> None:
        if not self.is_configured():
            self.last_error = "Oracle environment variables are missing."
            self.pool = None
            return

        env = self._required_env()
        service_name = os.getenv("ORACLE_SERVICE_NAME", "").strip()

        try:
            if service_name:
                dsn = oracle_driver.makedsn(
                    env["ORACLE_HOST"],
                    int(env["ORACLE_PORT"]),
                    service_name=service_name,
                )
            else:
                dsn = oracle_driver.makedsn(
                    env["ORACLE_HOST"],
                    int(env["ORACLE_PORT"]),
                    sid=env["ORACLE_SID"],
                )
            self.pool = oracle_driver.SessionPool(
                user=env["ORACLE_USER"],
                password=env["ORACLE_PASSWORD"],
                dsn=dsn,
                min=1,
                max=5,
                increment=1,
                threaded=True,
                encoding="UTF-8",
            )
            self.last_error = None
        except Exception as exc:
            self.pool = None
            self.last_error = str(exc)

    def is_available(self) -> bool:
        if self.pool is None:
            return False
        try:
            connection = self.pool.acquire()
            connection.ping()
            self.pool.release(connection)
            return True
        except Exception as exc:
            self.last_error = str(exc)
            return False

    def _acquire(self) -> Any:
        if self.pool is None:
            raise RuntimeError("Oracle session pool is not initialized.")
        return self.pool.acquire()

    def save_student_and_prediction(self, student: Dict[str, Any], prediction: Dict[str, Any]) -> Tuple[int, int]:
        connection = self._acquire()
        cursor = connection.cursor()

        try:
            cursor.execute(
                "SELECT STUDENT_ID FROM STUDENTS WHERE ROLL_NUMBER = :roll_number",
                {"roll_number": student["roll_number"]},
            )
            existing = cursor.fetchone()

            if existing:
                student_id = int(existing[0])
                cursor.execute(
                    """
                    UPDATE STUDENTS
                    SET
                        STUDENT_NAME = :student_name,
                        CGPA = :cgpa,
                        ATTENDANCE_PCT = :attendance_pct,
                        ASSIGNMENT_MARKS = :assignment_marks,
                        CLASS_BEHAVIOR = :class_behavior,
                        LAB_BEHAVIOR = :lab_behavior
                    WHERE STUDENT_ID = :student_id
                    """,
                    {
                        "student_id": student_id,
                        "student_name": student["student_name"],
                        "cgpa": student["cgpa"],
                        "attendance_pct": student["attendance_pct"],
                        "assignment_marks": student["assignment_marks"],
                        "class_behavior": student["class_behavior"],
                        "lab_behavior": student["lab_behavior"],
                    },
                )
            else:
                student_id_var = cursor.var(oracle_driver.NUMBER)
                cursor.execute(
                    """
                    INSERT INTO STUDENTS (
                        ROLL_NUMBER,
                        STUDENT_NAME,
                        CGPA,
                        ATTENDANCE_PCT,
                        ASSIGNMENT_MARKS,
                        CLASS_BEHAVIOR,
                        LAB_BEHAVIOR
                    ) VALUES (
                        :roll_number,
                        :student_name,
                        :cgpa,
                        :attendance_pct,
                        :assignment_marks,
                        :class_behavior,
                        :lab_behavior
                    )
                    RETURNING STUDENT_ID INTO :student_id
                    """,
                    {
                        "roll_number": student["roll_number"],
                        "student_name": student["student_name"],
                        "cgpa": student["cgpa"],
                        "attendance_pct": student["attendance_pct"],
                        "assignment_marks": student["assignment_marks"],
                        "class_behavior": student["class_behavior"],
                        "lab_behavior": student["lab_behavior"],
                        "student_id": student_id_var,
                    },
                )
                student_id = int(student_id_var.getvalue()[0])

            prediction_id_var = cursor.var(oracle_driver.NUMBER)
            ai_blob = json.dumps(
                {
                    "summary": prediction.get("summary", ""),
                    "suggestions": prediction.get("suggestions", []),
                    "engine_used": prediction.get("engine_used", "rule"),
                    "llm_failed": prediction.get("llm_failed", False),
                    "fallback_reason": prediction.get("fallback_reason"),
                }
            )

            cursor.execute(
                """
                INSERT INTO PREDICTIONS (
                    STUDENT_ID,
                    RISK_LEVEL,
                    RISK_SCORE,
                    AI_SUGGESTIONS
                ) VALUES (
                    :student_id,
                    :risk_level,
                    :risk_score,
                    :ai_suggestions
                )
                RETURNING PREDICTION_ID INTO :prediction_id
                """,
                {
                    "student_id": student_id,
                    "risk_level": prediction["risk_level"],
                    "risk_score": prediction["risk_score"],
                    "ai_suggestions": ai_blob,
                    "prediction_id": prediction_id_var,
                },
            )
            prediction_id = int(prediction_id_var.getvalue()[0])

            connection.commit()
            return student_id, prediction_id
        except Exception:
            connection.rollback()
            raise
        finally:
            cursor.close()
            self.pool.release(connection)

    def save_prediction_for_existing_student(self, student_id: int, prediction: Dict[str, Any]) -> int:
        connection = self._acquire()
        cursor = connection.cursor()

        try:
            prediction_id_var = cursor.var(oracle_driver.NUMBER)
            ai_blob = json.dumps(
                {
                    "summary": prediction.get("summary", ""),
                    "suggestions": prediction.get("suggestions", []),
                    "engine_used": prediction.get("engine_used", "rule"),
                    "llm_failed": prediction.get("llm_failed", False),
                    "fallback_reason": prediction.get("fallback_reason"),
                }
            )

            cursor.execute(
                """
                INSERT INTO PREDICTIONS (
                    STUDENT_ID,
                    RISK_LEVEL,
                    RISK_SCORE,
                    AI_SUGGESTIONS
                ) VALUES (
                    :student_id,
                    :risk_level,
                    :risk_score,
                    :ai_suggestions
                )
                RETURNING PREDICTION_ID INTO :prediction_id
                """,
                {
                    "student_id": student_id,
                    "risk_level": prediction["risk_level"],
                    "risk_score": prediction["risk_score"],
                    "ai_suggestions": ai_blob,
                    "prediction_id": prediction_id_var,
                },
            )

            prediction_id = int(prediction_id_var.getvalue()[0])
            connection.commit()
            return prediction_id
        except Exception:
            connection.rollback()
            raise
        finally:
            cursor.close()
            self.pool.release(connection)

    def fetch_all_students_with_latest_prediction(self, search: Optional[str] = None) -> List[Dict[str, Any]]:
        connection = self._acquire()
        cursor = connection.cursor()

        try:
            query = """
                SELECT
                    s.STUDENT_ID,
                    s.ROLL_NUMBER,
                    s.STUDENT_NAME,
                    s.CGPA,
                    s.ATTENDANCE_PCT,
                    s.ASSIGNMENT_MARKS,
                    s.CLASS_BEHAVIOR,
                    s.LAB_BEHAVIOR,
                    p.PREDICTION_ID,
                    p.RISK_LEVEL,
                    p.RISK_SCORE,
                    p.AI_SUGGESTIONS,
                    p.PREDICTED_AT
                FROM STUDENTS s
                LEFT JOIN (
                    SELECT p1.*
                    FROM (
                        SELECT
                            p.*,
                            ROW_NUMBER() OVER (
                                PARTITION BY p.STUDENT_ID
                                ORDER BY p.PREDICTED_AT DESC, p.PREDICTION_ID DESC
                            ) AS rn
                        FROM PREDICTIONS p
                    ) p1
                    WHERE p1.rn = 1
                ) p ON s.STUDENT_ID = p.STUDENT_ID
            """

            params: Dict[str, Any] = {}
            if search:
                query += " WHERE LOWER(s.STUDENT_NAME) LIKE :search OR LOWER(s.ROLL_NUMBER) LIKE :search "
                params["search"] = f"%{search.lower()}%"

            query += " ORDER BY s.CREATED_AT DESC "

            cursor.execute(query, params)
            rows = cursor.fetchall()

            students: List[Dict[str, Any]] = []
            for row in rows:
                ai_suggestions = {"summary": "", "suggestions": []}
                if row[11]:
                    try:
                        ai_suggestions = json.loads(row[11].read() if hasattr(row[11], "read") else row[11])
                    except Exception:
                        ai_suggestions = {"summary": "", "suggestions": []}

                students.append(
                    {
                        "student_id": int(row[0]),
                        "roll_number": row[1],
                        "student_name": row[2],
                        "cgpa": float(row[3]) if row[3] is not None else None,
                        "attendance_pct": float(row[4]) if row[4] is not None else None,
                        "assignment_marks": float(row[5]) if row[5] is not None else None,
                        "class_behavior": int(row[6]) if row[6] is not None else None,
                        "lab_behavior": int(row[7]) if row[7] is not None else None,
                        "prediction_id": int(row[8]) if row[8] is not None else None,
                        "risk_level": row[9],
                        "risk_score": float(row[10]) if row[10] is not None else None,
                        "summary": ai_suggestions.get("summary", ""),
                        "suggestions": ai_suggestions.get("suggestions", []),
                        "predicted_at": str(row[12]) if row[12] is not None else None,
                    }
                )

            return students
        finally:
            cursor.close()
            self.pool.release(connection)
