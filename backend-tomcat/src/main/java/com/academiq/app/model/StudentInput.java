package com.academiq.app.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class StudentInput {

    @JsonProperty("student_name")
    @NotBlank(message = "Student Name is required.")
    private String studentName;

    @JsonProperty("roll_number")
    @NotBlank(message = "Roll Number is required.")
    private String rollNumber;

    @NotNull(message = "CGPA is required.")
    @DecimalMin(value = "0.0", message = "CGPA must be between 0 and 10.")
    @DecimalMax(value = "10.0", message = "CGPA must be between 0 and 10.")
    private Double cgpa;

    @JsonProperty("attendance_pct")
    @NotNull(message = "Attendance % is required.")
    @DecimalMin(value = "0.0", message = "Attendance must be between 0 and 100.")
    @DecimalMax(value = "100.0", message = "Attendance must be between 0 and 100.")
    private Double attendancePct;

    @JsonProperty("assignment_marks")
    @NotNull(message = "Assignment Marks are required.")
    @DecimalMin(value = "0.0", message = "Assignment Marks must be between 0 and 100.")
    @DecimalMax(value = "100.0", message = "Assignment Marks must be between 0 and 100.")
    private Double assignmentMarks;

    @JsonProperty("class_behavior")
    @NotNull(message = "Class Behavior is required.")
    @Min(value = 1, message = "Class Behavior must be between 1 and 5.")
    @Max(value = 5, message = "Class Behavior must be between 1 and 5.")
    private Integer classBehavior;

    @JsonProperty("lab_behavior")
    @NotNull(message = "Lab Behavior is required.")
    @Min(value = 1, message = "Lab Behavior must be between 1 and 5.")
    @Max(value = 5, message = "Lab Behavior must be between 1 and 5.")
    private Integer labBehavior;

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getRollNumber() {
        return rollNumber;
    }

    public void setRollNumber(String rollNumber) {
        this.rollNumber = rollNumber;
    }

    public Double getCgpa() {
        return cgpa;
    }

    public void setCgpa(Double cgpa) {
        this.cgpa = cgpa;
    }

    public Double getAttendancePct() {
        return attendancePct;
    }

    public void setAttendancePct(Double attendancePct) {
        this.attendancePct = attendancePct;
    }

    public Double getAssignmentMarks() {
        return assignmentMarks;
    }

    public void setAssignmentMarks(Double assignmentMarks) {
        this.assignmentMarks = assignmentMarks;
    }

    public Integer getClassBehavior() {
        return classBehavior;
    }

    public void setClassBehavior(Integer classBehavior) {
        this.classBehavior = classBehavior;
    }

    public Integer getLabBehavior() {
        return labBehavior;
    }

    public void setLabBehavior(Integer labBehavior) {
        this.labBehavior = labBehavior;
    }
}
