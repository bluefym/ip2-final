package com.academiq.app.controller;

import com.academiq.app.model.PredictRequest;
import com.academiq.app.model.PredictionResult;
import com.academiq.app.model.SaveRequest;
import com.academiq.app.model.SaveResult;
import com.academiq.app.model.StudentDashboardRow;
import com.academiq.app.model.StudentInput;
import com.academiq.app.repository.OracleRepository;
import com.academiq.app.service.PredictionService;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.lang.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final OracleRepository repository;
    private final PredictionService predictionService;
    private final Validator validator;

    public ApiController(OracleRepository repository, PredictionService predictionService, Validator validator) {
        this.repository = repository;
        this.predictionService = predictionService;
        this.validator = validator;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        boolean available = repository.isAvailable();
        Map<String, Object> payload = new HashMap<>();
        payload.put("success", true);
        payload.put("service", "ACADEMIQ");
        payload.put("database_available", available);
        payload.put("database_error", available ? null : repository.getLastError());
        payload.put("warning", available ? null : dbWarning());
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/predict")
    public ResponseEntity<Map<String, Object>> predict(@Valid @RequestBody PredictRequest request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Invalid student input.", bindingErrors(bindingResult));
        }

        String engine = safeEngine(request.getEngine());
        if (engine == null) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_ENGINE", "Engine must be 'rule' or 'llm'.", null);
        }

        PredictionResult prediction = predictionService.generate(request, engine);
        boolean databaseAvailable = repository.isAvailable();

        boolean saved = false;
        Long studentId = null;
        Long predictionId = null;
        String dbError = null;

        if (databaseAvailable) {
            try {
                SaveResult result = repository.saveStudentAndPrediction(request, prediction);
                saved = true;
                studentId = result.getStudentId();
                predictionId = result.getPredictionId();
            } catch (Exception ex) {
                dbError = ex.getMessage();
            }
        } else {
            dbError = repository.getLastError();
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("success", true);
        payload.put("student", studentMap(request));
        payload.put("prediction", prediction);
        payload.put("saved", saved);
        payload.put("student_id", studentId);
        payload.put("prediction_id", predictionId);
        payload.put("database_available", databaseAvailable);
        payload.put("warning", saved ? null : (databaseAvailable ? "DATABASE SAVE FAILED — PREDICTION NOT SAVED" : dbWarning()));
        payload.put("database_error", dbError);

        return ResponseEntity.ok(payload);
    }

    @PostMapping(value = "/predict/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> predictBulk(@RequestParam("file") MultipartFile file,
                                                           @RequestParam(value = "engine", defaultValue = "rule") String engineInput) {
        String engine = safeEngine(engineInput);
        if (engine == null) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_ENGINE", "Engine must be 'rule' or 'llm'.", null);
        }

        if (file == null || file.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "FILE_REQUIRED", "CSV file is required in form field 'file'.", null);
        }

        String originalFilename = file.getOriginalFilename();
        String name = originalFilename == null ? "" : originalFilename.toLowerCase(Locale.ROOT);
        if (!name.endsWith(".csv")) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_FILE", "Only CSV files are supported.", null);
        }

        boolean databaseAvailable = repository.isAvailable();

        List<Map<String, Object>> results = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();

        int totalRows = 0;
        int processedCount = 0;
        int savedCount = 0;

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {

            for (CSVRecord record : parser) {
                totalRows++;
                long rowNo = record.getRecordNumber() + 1;

                ParseResult parse = parseStudentFromCsv(record);
                if (!parse.errors.isEmpty()) {
                    errors.add(rowError(rowNo, "validation", parse.errors, record.toMap()));
                    continue;
                }

                Set<ConstraintViolation<StudentInput>> violations = validator.validate(parse.student);
                if (!violations.isEmpty()) {
                    errors.add(rowError(rowNo, "validation", violationsToList(violations), record.toMap()));
                    continue;
                }

                processedCount++;
                PredictionResult prediction = predictionService.generate(parse.student, engine);

                boolean saved = false;
                Long studentId = null;
                Long predictionId = null;
                String dbError = null;

                if (databaseAvailable) {
                    try {
                        SaveResult result = repository.saveStudentAndPrediction(parse.student, prediction);
                        saved = true;
                        savedCount++;
                        studentId = result.getStudentId();
                        predictionId = result.getPredictionId();
                    } catch (Exception ex) {
                        dbError = ex.getMessage();
                        Map<String, Object> dbErr = new HashMap<>();
                        dbErr.put("row", rowNo);
                        dbErr.put("type", "database");
                        dbErr.put("message", "Failed to persist row.");
                        dbErr.put("details", dbError);
                        dbErr.put("roll_number", parse.student.getRollNumber());
                        errors.add(dbErr);
                    }
                }

                Map<String, Object> rowResult = new HashMap<>();
                rowResult.put("row", rowNo);
                rowResult.put("student", studentMap(parse.student));
                rowResult.put("prediction", prediction);
                rowResult.put("saved", saved);
                rowResult.put("student_id", studentId);
                rowResult.put("prediction_id", predictionId);
                rowResult.put("database_error", dbError);
                results.add(rowResult);
            }
        } catch (Exception ex) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_CSV", "CSV parsing failed.", ex.getMessage());
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("success", true);
        payload.put("total_rows", totalRows);
        payload.put("processed_count", processedCount);
        payload.put("saved_count", savedCount);
        payload.put("error_count", errors.size());
        payload.put("errors", errors);
        payload.put("results", results);
        payload.put("database_available", databaseAvailable);
        payload.put("warning", databaseAvailable ? null : dbWarning());
        payload.put("database_error", databaseAvailable ? null : repository.getLastError());

        return ResponseEntity.ok(payload);
    }

    @PostMapping("/save")
    public ResponseEntity<Map<String, Object>> save(@RequestBody SaveRequest request) {
        if (request.getStudent() == null || request.getPrediction() == null) {
            return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request must include student and prediction.", null);
        }

        Set<ConstraintViolation<StudentInput>> violations = validator.validate(request.getStudent());
        if (!violations.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Invalid student input.", violationsToList(violations));
        }

        PredictionResult prediction = request.getPrediction();
        if (prediction.getRiskLevel() == null) {
            return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Prediction risk_level is required.", null);
        }

        String riskLevel = prediction.getRiskLevel().toUpperCase(Locale.ROOT).trim();
        if (!("DANGER".equals(riskLevel) || "ATTENTION".equals(riskLevel) || "CLEAR".equals(riskLevel))) {
            return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Prediction risk_level is invalid.", null);
        }

        if (prediction.getRiskScore() == null || prediction.getRiskScore() < 0 || prediction.getRiskScore() > 100) {
            return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Prediction risk_score must be between 0 and 100.", null);
        }

        prediction.setRiskLevel(riskLevel);

        if (!repository.isAvailable()) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE", dbWarning(), repository.getLastError());
        }

        try {
            SaveResult result = repository.saveStudentAndPrediction(request.getStudent(), prediction);
            Map<String, Object> payload = new HashMap<>();
            payload.put("success", true);
            payload.put("saved", true);
            payload.put("student_id", result.getStudentId());
            payload.put("prediction_id", result.getPredictionId());
            return ResponseEntity.ok(payload);
        } catch (Exception ex) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "DATABASE_SAVE_FAILED", "Could not save record.", ex.getMessage());
        }
    }

    @GetMapping("/students")
    public ResponseEntity<Map<String, Object>> students(@RequestParam(value = "search", required = false) String search) {
        if (!repository.isAvailable()) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("success", true);
            payload.put("database_available", false);
            payload.put("warning", dbWarning());
            payload.put("database_error", repository.getLastError());
            payload.put("students", new ArrayList<>());
            return ResponseEntity.ok(payload);
        }

        try {
            List<StudentDashboardRow> students = repository.fetchAllStudentsWithLatestPrediction(search);
            Map<String, Object> payload = new HashMap<>();
            payload.put("success", true);
            payload.put("database_available", true);
            payload.put("students", students);
            return ResponseEntity.ok(payload);
        } catch (Exception ex) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "DATABASE_QUERY_FAILED", "Failed to load students.", ex.getMessage());
        }
    }

    @GetMapping("/students/export")
    public ResponseEntity<?> exportStudents() {
        if (!repository.isAvailable()) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE", dbWarning(), repository.getLastError());
        }

        try {
            List<StudentDashboardRow> students = repository.fetchAllStudentsWithLatestPrediction(null);
            StringBuilder csv = new StringBuilder();
            csv.append("Roll Number,Student Name,CGPA,Attendance %,Assignment Marks,Class Behavior,Lab Behavior,Risk Score,Risk Level,Summary\n");
            for (StudentDashboardRow s : students) {
                csv.append(escape(s.getRollNumber())).append(',')
                        .append(escape(s.getStudentName())).append(',')
                        .append(escape(number(s.getCgpa()))).append(',')
                        .append(escape(number(s.getAttendancePct()))).append(',')
                        .append(escape(number(s.getAssignmentMarks()))).append(',')
                        .append(escape(number(s.getClassBehavior()))).append(',')
                        .append(escape(number(s.getLabBehavior()))).append(',')
                        .append(escape(number(s.getRiskScore()))).append(',')
                        .append(escape(s.getRiskLevel())).append(',')
                        .append(escape(s.getSummary()))
                        .append('\n');
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=academiq_students_export.csv")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(csv.toString());
        } catch (Exception ex) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "DATABASE_QUERY_FAILED", "Failed to load students.", ex.getMessage());
        }
    }

    private String safeEngine(String engine) {
        String value = engine == null ? "rule" : engine.trim().toLowerCase(Locale.ROOT);
        if ("rule".equals(value) || "llm".equals(value)) {
            return value;
        }
        return null;
    }

    private Map<String, Object> studentMap(StudentInput student) {
        Map<String, Object> map = new HashMap<>();
        map.put("student_name", student.getStudentName());
        map.put("roll_number", student.getRollNumber());
        map.put("cgpa", student.getCgpa());
        map.put("attendance_pct", student.getAttendancePct());
        map.put("assignment_marks", student.getAssignmentMarks());
        map.put("class_behavior", student.getClassBehavior());
        map.put("lab_behavior", student.getLabBehavior());
        return map;
    }

    private ResponseEntity<Map<String, Object>> error(@NonNull HttpStatus status, String code, String message, Object details) {
        Map<String, Object> error = new HashMap<>();
        error.put("code", code);
        error.put("message", message);
        if (details != null) {
            error.put("details", details);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("success", false);
        payload.put("error", error);
        return ResponseEntity.status(status).body(payload);
    }

    private List<Map<String, String>> bindingErrors(BindingResult bindingResult) {
        List<Map<String, String>> list = new ArrayList<>();
        for (FieldError fieldError : bindingResult.getFieldErrors()) {
            Map<String, String> item = new HashMap<>();
            item.put("field", toSnakeField(fieldError.getField()));
            item.put("message", fieldError.getDefaultMessage());
            list.add(item);
        }
        return list;
    }

    private List<Map<String, String>> violationsToList(Set<ConstraintViolation<StudentInput>> violations) {
        List<Map<String, String>> list = new ArrayList<>();
        for (ConstraintViolation<StudentInput> violation : violations) {
            Map<String, String> item = new HashMap<>();
            item.put("field", toSnakeField(violation.getPropertyPath().toString()));
            item.put("message", violation.getMessage());
            list.add(item);
        }
        return list;
    }

    private String toSnakeField(String field) {
        if ("studentName".equals(field)) {
            return "student_name";
        }
        if ("rollNumber".equals(field)) {
            return "roll_number";
        }
        if ("attendancePct".equals(field)) {
            return "attendance_pct";
        }
        if ("assignmentMarks".equals(field)) {
            return "assignment_marks";
        }
        if ("classBehavior".equals(field)) {
            return "class_behavior";
        }
        if ("labBehavior".equals(field)) {
            return "lab_behavior";
        }
        return field;
    }

    private ParseResult parseStudentFromCsv(CSVRecord record) {
        Map<String, String> normalized = normalizeRecord(record.toMap());

        StudentInput student = new StudentInput();
        List<Map<String, String>> errors = new ArrayList<>();

        student.setStudentName(trimmed(normalized.get("student_name")));
        student.setRollNumber(trimmed(normalized.get("roll_number")));

        if (student.getStudentName().trim().isEmpty()) {
            errors.add(fieldError("student_name", "Student Name is required."));
        }
        if (student.getRollNumber().trim().isEmpty()) {
            errors.add(fieldError("roll_number", "Roll Number is required."));
        }

        student.setCgpa(parseDouble(normalized.get("cgpa"), "cgpa", errors));
        student.setAttendancePct(parseDouble(normalized.get("attendance_pct"), "attendance_pct", errors));
        student.setAssignmentMarks(parseDouble(normalized.get("assignment_marks"), "assignment_marks", errors));
        student.setClassBehavior(parseInteger(normalized.get("class_behavior"), "class_behavior", errors));
        student.setLabBehavior(parseInteger(normalized.get("lab_behavior"), "lab_behavior", errors));

        return new ParseResult(student, errors);
    }

    private Map<String, String> normalizeRecord(Map<String, String> raw) {
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().trim().toLowerCase(Locale.ROOT);
            String value = entry.getValue();

            if ("student name".equals(key) || "student_name".equals(key) || "name".equals(key)) {
                out.put("student_name", value);
            } else if ("roll number".equals(key) || "roll_number".equals(key) || "roll".equals(key)) {
                out.put("roll_number", value);
            } else if ("cgpa".equals(key)) {
                out.put("cgpa", value);
            } else if ("attendance %".equals(key) || "attendance_pct".equals(key) || "attendance".equals(key)) {
                out.put("attendance_pct", value);
            } else if ("assignment marks".equals(key) || "assignment_marks".equals(key) || "assignments".equals(key)) {
                out.put("assignment_marks", value);
            } else if ("class behavior".equals(key) || "class_behavior".equals(key)) {
                out.put("class_behavior", value);
            } else if ("lab behavior".equals(key) || "lab_behavior".equals(key)) {
                out.put("lab_behavior", value);
            }
        }
        return out;
    }

    private Double parseDouble(String value, String field, List<Map<String, String>> errors) {
        String v = trimmed(value);
        if (v.isEmpty()) {
            errors.add(fieldError(field, "Field is required."));
            return null;
        }
        try {
            return Double.parseDouble(v);
        } catch (Exception ex) {
            errors.add(fieldError(field, "Must be a numeric value."));
            return null;
        }
    }

    private Integer parseInteger(String value, String field, List<Map<String, String>> errors) {
        String v = trimmed(value);
        if (v.isEmpty()) {
            errors.add(fieldError(field, "Field is required."));
            return null;
        }
        try {
            return (int) Math.round(Double.parseDouble(v));
        } catch (Exception ex) {
            errors.add(fieldError(field, "Must be an integer value."));
            return null;
        }
    }

    private Map<String, Object> rowError(long rowNo, String type, Object details, Object raw) {
        Map<String, Object> item = new HashMap<>();
        item.put("row", rowNo);
        item.put("type", type);
        item.put("details", details);
        item.put("raw", raw);
        return item;
    }

    private Map<String, String> fieldError(String field, String message) {
        Map<String, String> map = new HashMap<>();
        map.put("field", field);
        map.put("message", message);
        return map;
    }

    private String trimmed(String value) {
        return value == null ? "" : value.trim();
    }

    private String dbWarning() {
        return "DATABASE UNAVAILABLE — PREDICTIONS WILL NOT BE SAVED";
    }

    private String escape(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        String escaped = text.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private String number(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static class ParseResult {
        private final StudentInput student;
        private final List<Map<String, String>> errors;

        private ParseResult(StudentInput student, List<Map<String, String>> errors) {
            this.student = student;
            this.errors = errors;
        }
    }
}
