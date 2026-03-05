INSERT INTO STUDENTS (ROLL_NUMBER, STUDENT_NAME, CGPA, ATTENDANCE_PCT, ASSIGNMENT_MARKS, CLASS_BEHAVIOR, LAB_BEHAVIOR)
VALUES ('A001', 'Arjun Mehta', 8.50, 91.00, 88.00, 5, 5);

INSERT INTO STUDENTS (ROLL_NUMBER, STUDENT_NAME, CGPA, ATTENDANCE_PCT, ASSIGNMENT_MARKS, CLASS_BEHAVIOR, LAB_BEHAVIOR)
VALUES ('A002', 'Sneha Rao', 6.20, 74.00, 65.00, 3, 3);

INSERT INTO STUDENTS (ROLL_NUMBER, STUDENT_NAME, CGPA, ATTENDANCE_PCT, ASSIGNMENT_MARKS, CLASS_BEHAVIOR, LAB_BEHAVIOR)
VALUES ('A003', 'Rahul Das', 4.10, 52.00, 40.00, 2, 2);

INSERT INTO STUDENTS (ROLL_NUMBER, STUDENT_NAME, CGPA, ATTENDANCE_PCT, ASSIGNMENT_MARKS, CLASS_BEHAVIOR, LAB_BEHAVIOR)
VALUES ('A004', 'Priya Nair', 7.80, 82.00, 79.00, 4, 4);

INSERT INTO STUDENTS (ROLL_NUMBER, STUDENT_NAME, CGPA, ATTENDANCE_PCT, ASSIGNMENT_MARKS, CLASS_BEHAVIOR, LAB_BEHAVIOR)
VALUES ('A005', 'Kiran Shah', 5.00, 61.00, 55.00, 2, 2);

INSERT INTO PREDICTIONS (STUDENT_ID, RISK_LEVEL, RISK_SCORE, AI_SUGGESTIONS)
SELECT s.STUDENT_ID,
       'CLEAR',
       90.85,
       '{"summary":"Strong overall performance with excellent consistency.","suggestions":[{"area":"General","issue":"No critical issues detected.","recommendation":"Maintain current study rhythm and engagement."}]}'
FROM STUDENTS s WHERE s.ROLL_NUMBER = 'A001';

INSERT INTO PREDICTIONS (STUDENT_ID, RISK_LEVEL, RISK_SCORE, AI_SUGGESTIONS)
SELECT s.STUDENT_ID,
       'ATTENTION',
       57.60,
       '{"summary":"Moderate risk with attendance and assignment consistency concerns.","suggestions":[{"area":"Attendance","issue":"Attendance is below optimal range.","recommendation":"Attend at least 4 more classes this month."},{"area":"Assignments","issue":"Assignment quality is mid-range.","recommendation":"Start submissions earlier and complete rubric checks."}]}'
FROM STUDENTS s WHERE s.ROLL_NUMBER = 'A002';

INSERT INTO PREDICTIONS (STUDENT_ID, RISK_LEVEL, RISK_SCORE, AI_SUGGESTIONS)
SELECT s.STUDENT_ID,
       'DANGER',
       43.30,
       '{"summary":"High risk profile with multiple low-performing indicators.","suggestions":[{"area":"CGPA","issue":"CGPA is critically low.","recommendation":"Use a daily revision schedule and weekly mentor review."},{"area":"Attendance","issue":"Attendance is far below 75%.","recommendation":"Prioritize attending every class for the next 6 weeks."}]}'
FROM STUDENTS s WHERE s.ROLL_NUMBER = 'A003';

INSERT INTO PREDICTIONS (STUDENT_ID, RISK_LEVEL, RISK_SCORE, AI_SUGGESTIONS)
SELECT s.STUDENT_ID,
       'CLEAR',
       79.70,
       '{"summary":"Stable and healthy performance with good engagement.","suggestions":[{"area":"General","issue":"No major academic issues observed.","recommendation":"Continue current consistency and weekly progress tracking."}]}'
FROM STUDENTS s WHERE s.ROLL_NUMBER = 'A004';

INSERT INTO PREDICTIONS (STUDENT_ID, RISK_LEVEL, RISK_SCORE, AI_SUGGESTIONS)
SELECT s.STUDENT_ID,
       'DANGER',
       39.80,
       '{"summary":"Risk remains high due to weak academic and engagement signals.","suggestions":[{"area":"Attendance","issue":"Attendance is significantly below target.","recommendation":"Attend at least 7 additional classes in the next cycle."},{"area":"Assignments","issue":"Assignment score trend is low.","recommendation":"Break each task into milestones with instructor check-ins."}]}'
FROM STUDENTS s WHERE s.ROLL_NUMBER = 'A005';

COMMIT;
