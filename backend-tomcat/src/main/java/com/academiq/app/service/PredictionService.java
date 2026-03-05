package com.academiq.app.service;

import com.academiq.app.model.PredictionResult;
import com.academiq.app.model.PredictionSuggestion;
import com.academiq.app.model.StudentInput;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PredictionService {

    private static final String PROMPT = "You are an academic advisor AI. Given a student's data, analyze their performance and return a JSON object with: " +
            "{ 'risk_level': 'DANGER' | 'ATTENTION' | 'CLEAR', 'risk_score': <number 0-100>, 'summary': '<2-sentence overall assessment>', " +
            "'suggestions': [{ 'area': '<CGPA|Attendance|Assignments|Class Behavior|Lab Behavior>', 'issue': '<what is wrong>', " +
            "'recommendation': '<specific actionable step>' }] } Be specific. If attendance is below 75%, say exactly that and recommend attending at least X more classes. " +
            "If CGPA is below 6.0, suggest specific study strategies. Return ONLY the JSON, no markdown.";

    private final ObjectMapper objectMapper;

    @Value("${OPENAI_API_KEY:}")
    private String apiKey;

    @Value("${OPENAI_MODEL:gpt-4o-mini}")
    private String openAiModel;

    @Value("${OPENAI_BASE_URL:https://api.openai.com/v1/chat/completions}")
    private String openAiUrl;

    public PredictionService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PredictionResult generate(StudentInput student, String engine) {
        RuleOutcome rule = computeRule(student);

        if ("llm".equalsIgnoreCase(engine)) {
            try {
                PredictionResult llmResult = callLlm(student);
                llmResult.setEngineUsed("llm");
                llmResult.setLlmFailed(false);
                llmResult.setFallbackReason(null);
                return llmResult;
            } catch (Exception ex) {
                PredictionResult fallback = buildRuleResult(student, rule);
                fallback.setEngineUsed("rule");
                fallback.setLlmFailed(true);
                fallback.setFallbackReason(ex.getMessage());
                return fallback;
            }
        }

        PredictionResult result = buildRuleResult(student, rule);
        result.setEngineUsed("rule");
        result.setLlmFailed(false);
        result.setFallbackReason(null);
        return result;
    }

    private PredictionResult buildRuleResult(StudentInput student, RuleOutcome outcome) {
        PredictionResult result = new PredictionResult();
        result.setRiskLevel(outcome.riskLevel);
        result.setRiskScore(outcome.score);
        result.setSummary(defaultSummary(student.getStudentName(), outcome.riskLevel));
        result.setSuggestions(defaultSuggestions(student));
        return result;
    }

    private RuleOutcome computeRule(StudentInput student) {
        double score = ((student.getCgpa() / 10.0) * 30.0)
                + ((student.getAttendancePct() / 100.0) * 25.0)
                + ((student.getAssignmentMarks() / 100.0) * 20.0)
                + ((student.getClassBehavior() / 5.0) * 12.5)
                + ((student.getLabBehavior() / 5.0) * 12.5);

        String risk = score >= 70 ? "CLEAR" : (score >= 45 ? "ATTENTION" : "DANGER");
        return new RuleOutcome(round(score), risk);
    }

    private List<PredictionSuggestion> defaultSuggestions(StudentInput student) {
        List<PredictionSuggestion> list = new ArrayList<>();

        if (student.getCgpa() < 6.0) {
            list.add(new PredictionSuggestion("CGPA",
                    "CGPA is " + round(student.getCgpa()) + ", below the 6.0 threshold.",
                    "Adopt a weekly study calendar with daily 90-minute deep-work blocks and one faculty review session per week."));
        }

        if (student.getAttendancePct() < 75.0) {
            int classesNeeded = Math.max(1, (int) Math.round((75.0 - student.getAttendancePct()) / 2.0));
            list.add(new PredictionSuggestion("Attendance",
                    "Attendance is " + round(student.getAttendancePct()) + "%, below 75%.",
                    "Attend at least " + classesNeeded + " additional classes over the next cycle and avoid consecutive absences."));
        }

        if (student.getAssignmentMarks() < 60.0) {
            list.add(new PredictionSuggestion("Assignments",
                    "Assignment marks are " + round(student.getAssignmentMarks()) + ", indicating weak quality consistency.",
                    "Start assignments 72 hours before due date and perform rubric-mapped self-review before submission."));
        }

        if (student.getClassBehavior() <= 2) {
            list.add(new PredictionSuggestion("Class Behavior",
                    "Class behavior score is " + student.getClassBehavior() + "/5.",
                    "Participate at least once per class and track focus adherence with a simple attendance-and-participation log."));
        }

        if (student.getLabBehavior() <= 2) {
            list.add(new PredictionSuggestion("Lab Behavior",
                    "Lab behavior score is " + student.getLabBehavior() + "/5.",
                    "Prepare a pre-lab checklist and submit post-lab notes to reinforce practical learning outcomes."));
        }

        if (list.isEmpty()) {
            list.add(new PredictionSuggestion("General",
                    "No critical weaknesses detected.",
                    "Maintain consistency through weekly goal tracking and subject-wise revision checkpoints."));
        }

        return list;
    }

    private String defaultSummary(String name, String risk) {
        if ("DANGER".equals(risk)) {
            return name + " is currently in a high-risk zone and requires immediate support across multiple metrics.";
        }
        if ("ATTENTION".equals(risk)) {
            return name + " is in the attention zone with moderate concerns that can improve with targeted consistency.";
        }
        return name + " is in the clear zone with stable academic indicators and healthy engagement trends.";
    }

    private PredictionResult callLlm(StudentInput student) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("OPENAI_API_KEY is not configured.");
        }

        Map<String, Object> studentMap = new HashMap<>();
        studentMap.put("student_name", student.getStudentName());
        studentMap.put("roll_number", student.getRollNumber());
        studentMap.put("cgpa", student.getCgpa());
        studentMap.put("attendance_pct", student.getAttendancePct());
        studentMap.put("assignment_marks", student.getAssignmentMarks());
        studentMap.put("class_behavior", student.getClassBehavior());
        studentMap.put("lab_behavior", student.getLabBehavior());

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", openAiModel);
        payload.put("temperature", 0.2);
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", PROMPT);
        messages.add(systemMessage);
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", objectMapper.writeValueAsString(studentMap));
        messages.add(userMessage);
        payload.put("messages", messages);

        String payloadJson = objectMapper.writeValueAsString(payload);
        HttpURLConnection connection = (HttpURLConnection) new URL(openAiUrl).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(payloadJson.getBytes("UTF-8"));
        }

        int statusCode = connection.getResponseCode();
        String responseBody = readBody(statusCode >= 200 && statusCode < 300 ? connection.getInputStream() : connection.getErrorStream());

        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalStateException("LLM HTTP error: " + statusCode + " " + responseBody);
        }

        JsonNode root = objectMapper.readTree(responseBody);
        String content = root.path("choices").path(0).path("message").path("content").asText();
        String jsonObject = extractJson(content);
        JsonNode llmNode = objectMapper.readTree(jsonObject);

        String riskLevel = llmNode.path("risk_level").asText("").toUpperCase();
        double riskScore = llmNode.path("risk_score").asDouble(-1);
        if (!("DANGER".equals(riskLevel) || "ATTENTION".equals(riskLevel) || "CLEAR".equals(riskLevel))) {
            throw new IllegalStateException("LLM returned invalid risk_level.");
        }
        if (riskScore < 0 || riskScore > 100) {
            throw new IllegalStateException("LLM returned invalid risk_score.");
        }

        PredictionResult result = new PredictionResult();
        result.setRiskLevel(riskLevel);
        result.setRiskScore(round(riskScore));
        result.setSummary(llmNode.path("summary").asText(defaultSummary(student.getStudentName(), riskLevel)));

        List<PredictionSuggestion> suggestions = new ArrayList<>();
        if (llmNode.has("suggestions") && llmNode.get("suggestions").isArray()) {
            for (JsonNode item : llmNode.get("suggestions")) {
                suggestions.add(new PredictionSuggestion(
                        item.path("area").asText("General"),
                        item.path("issue").asText(""),
                        item.path("recommendation").asText("")
                ));
            }
        }
        if (suggestions.isEmpty()) {
            suggestions = defaultSuggestions(student);
        }
        result.setSuggestions(suggestions);
        return result;
    }

    private String readBody(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private String extractJson(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end < 0 || end <= start) {
            throw new IllegalStateException("LLM response does not contain JSON.");
        }
        return content.substring(start, end + 1);
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static class RuleOutcome {
        private final double score;
        private final String riskLevel;

        private RuleOutcome(double score, String riskLevel) {
            this.score = score;
            this.riskLevel = riskLevel;
        }
    }
}
