package com.academiq.app.repository;

import com.academiq.app.model.PredictionResult;
import com.academiq.app.model.PredictionSuggestion;
import com.academiq.app.model.SaveResult;
import com.academiq.app.model.StudentDashboardRow;
import com.academiq.app.model.StudentInput;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class OracleRepository {

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private volatile String lastError;

    public OracleRepository(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    public String getLastError() {
        return lastError;
    }

    public boolean isAvailable() {
        try (Connection connection = dataSource.getConnection()) {
            connection.isValid(3);
            lastError = null;
            return true;
        } catch (Exception ex) {
            lastError = ex.getMessage();
            return false;
        }
    }

    public SaveResult saveStudentAndPrediction(StudentInput student, PredictionResult prediction) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Long studentId = findStudentIdByRoll(connection, student.getRollNumber());
                if (studentId == null) {
                    insertStudent(connection, student);
                    studentId = findStudentIdByRoll(connection, student.getRollNumber());
                    if (studentId == null) {
                        throw new IllegalStateException("Failed to resolve STUDENT_ID after insert.");
                    }
                } else {
                    updateStudent(connection, studentId, student);
                }

                Long predictionId = insertPrediction(connection, studentId, prediction);
                connection.commit();
                return new SaveResult(studentId, predictionId);
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private Long findStudentIdByRoll(Connection connection, String rollNumber) throws Exception {
        String sql = "SELECT STUDENT_ID FROM STUDENTS WHERE ROLL_NUMBER = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, rollNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return null;
    }

    private void insertStudent(Connection connection, StudentInput student) throws Exception {
        String sql = "INSERT INTO STUDENTS ("
                + "ROLL_NUMBER, "
                + "STUDENT_NAME, "
                + "CGPA, "
                + "ATTENDANCE_PCT, "
                + "ASSIGNMENT_MARKS, "
                + "CLASS_BEHAVIOR, "
                + "LAB_BEHAVIOR"
                + ") VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, student.getRollNumber());
            ps.setString(2, student.getStudentName());
            ps.setDouble(3, student.getCgpa());
            ps.setDouble(4, student.getAttendancePct());
            ps.setDouble(5, student.getAssignmentMarks());
            ps.setInt(6, student.getClassBehavior());
            ps.setInt(7, student.getLabBehavior());
            ps.executeUpdate();
        }
    }

    private void updateStudent(Connection connection, Long studentId, StudentInput student) throws Exception {
        String sql = "UPDATE STUDENTS "
                + "SET STUDENT_NAME = ?, "
                + "CGPA = ?, "
                + "ATTENDANCE_PCT = ?, "
                + "ASSIGNMENT_MARKS = ?, "
                + "CLASS_BEHAVIOR = ?, "
                + "LAB_BEHAVIOR = ? "
                + "WHERE STUDENT_ID = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, student.getStudentName());
            ps.setDouble(2, student.getCgpa());
            ps.setDouble(3, student.getAttendancePct());
            ps.setDouble(4, student.getAssignmentMarks());
            ps.setInt(5, student.getClassBehavior());
            ps.setInt(6, student.getLabBehavior());
            ps.setLong(7, studentId);
            ps.executeUpdate();
        }
    }

    private Long insertPrediction(Connection connection, Long studentId, PredictionResult prediction) throws Exception {
        String sql = "INSERT INTO PREDICTIONS ("
                + "STUDENT_ID, "
                + "RISK_LEVEL, "
                + "RISK_SCORE, "
                + "AI_SUGGESTIONS"
                + ") VALUES (?, ?, ?, ?)";

        Map<String, Object> aiBlob = new HashMap<>();
        aiBlob.put("summary", prediction.getSummary());
        aiBlob.put("suggestions", prediction.getSuggestions());
        aiBlob.put("engine_used", prediction.getEngineUsed());
        aiBlob.put("llm_failed", prediction.isLlmFailed());
        aiBlob.put("fallback_reason", prediction.getFallbackReason());

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            ps.setString(2, prediction.getRiskLevel());
            ps.setDouble(3, prediction.getRiskScore());
            ps.setString(4, objectMapper.writeValueAsString(aiBlob));
            ps.executeUpdate();
        }

        String idSql = "SELECT PREDICTION_ID "
            + "FROM PREDICTIONS "
            + "WHERE STUDENT_ID = ? "
            + "ORDER BY PREDICTED_AT DESC, PREDICTION_ID DESC "
            + "FETCH FIRST 1 ROWS ONLY";

        try (PreparedStatement ps = connection.prepareStatement(idSql)) {
            ps.setLong(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }

        throw new IllegalStateException("Failed to resolve PREDICTION_ID after insert.");
    }

    public List<StudentDashboardRow> fetchAllStudentsWithLatestPrediction(String search) throws Exception {
        StringBuilder sql = new StringBuilder(
                "SELECT "
                        + "s.STUDENT_ID, "
                        + "s.ROLL_NUMBER, "
                        + "s.STUDENT_NAME, "
                        + "s.CGPA, "
                        + "s.ATTENDANCE_PCT, "
                        + "s.ASSIGNMENT_MARKS, "
                        + "s.CLASS_BEHAVIOR, "
                        + "s.LAB_BEHAVIOR, "
                        + "p.PREDICTION_ID, "
                        + "p.RISK_LEVEL, "
                        + "p.RISK_SCORE, "
                        + "p.AI_SUGGESTIONS, "
                        + "p.PREDICTED_AT "
                        + "FROM STUDENTS s "
                        + "LEFT JOIN ( "
                        + "    SELECT * FROM ( "
                        + "        SELECT p.*, ROW_NUMBER() OVER (PARTITION BY p.STUDENT_ID ORDER BY p.PREDICTED_AT DESC, p.PREDICTION_ID DESC) rn "
                        + "        FROM PREDICTIONS p "
                        + "    ) WHERE rn = 1 "
                        + ") p ON s.STUDENT_ID = p.STUDENT_ID "
        );

        boolean hasSearch = search != null && !search.trim().isEmpty();
        if (hasSearch) {
            sql.append(" WHERE LOWER(s.STUDENT_NAME) LIKE ? OR LOWER(s.ROLL_NUMBER) LIKE ? ");
        }
        sql.append(" ORDER BY s.CREATED_AT DESC ");

        List<StudentDashboardRow> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql.toString())) {

            if (hasSearch) {
                String normalizedSearch = search == null ? "" : search.toLowerCase();
                String token = "%" + normalizedSearch + "%";
                ps.setString(1, token);
                ps.setString(2, token);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    StudentDashboardRow row = new StudentDashboardRow();
                    row.setStudentId(rs.getLong("STUDENT_ID"));
                    row.setRollNumber(rs.getString("ROLL_NUMBER"));
                    row.setStudentName(rs.getString("STUDENT_NAME"));
                    row.setCgpa(getNullableDouble(rs, "CGPA"));
                    row.setAttendancePct(getNullableDouble(rs, "ATTENDANCE_PCT"));
                    row.setAssignmentMarks(getNullableDouble(rs, "ASSIGNMENT_MARKS"));
                    row.setClassBehavior(getNullableInt(rs, "CLASS_BEHAVIOR"));
                    row.setLabBehavior(getNullableInt(rs, "LAB_BEHAVIOR"));
                    row.setPredictionId(getNullableLong(rs, "PREDICTION_ID"));
                    row.setRiskLevel(rs.getString("RISK_LEVEL"));
                    row.setRiskScore(getNullableDouble(rs, "RISK_SCORE"));
                    row.setPredictedAt(readTimestamp(rs.getTimestamp("PREDICTED_AT")));

                    String ai = rs.getString("AI_SUGGESTIONS");
                    if (ai != null && !ai.trim().isEmpty()) {
                        try {
                            JsonNode node = objectMapper.readTree(ai);
                            row.setSummary(node.path("summary").asText(""));
                            List<PredictionSuggestion> suggestions = new ArrayList<>();
                            if (node.has("suggestions") && node.get("suggestions").isArray()) {
                                for (JsonNode item : node.get("suggestions")) {
                                    suggestions.add(new PredictionSuggestion(
                                            item.path("area").asText(""),
                                            item.path("issue").asText(""),
                                            item.path("recommendation").asText("")
                                    ));
                                }
                            }
                            row.setSuggestions(suggestions);
                        } catch (Exception ignored) {
                            row.setSummary("");
                            row.setSuggestions(new ArrayList<>());
                        }
                    }

                    rows.add(row);
                }
            }
        }

        return rows;
    }

    private Double getNullableDouble(ResultSet rs, String column) throws Exception {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private Integer getNullableInt(ResultSet rs, String column) throws Exception {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Long getNullableLong(ResultSet rs, String column) throws Exception {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private String readTimestamp(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toString();
    }
}
