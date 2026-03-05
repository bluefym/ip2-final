(function () {
  const chartStore = new Map();

  const COLORS = {
    white: "#FFFFFF",
    grid: "#1E1E1E",
    red: "#FF2D2D",
    yellow: "#FFD600",
    green: "#00FF85",
  };

  function getRiskColor(level) {
    if (level === "DANGER") return COLORS.red;
    if (level === "ATTENTION") return COLORS.yellow;
    return COLORS.green;
  }

  function destroyChart(canvasId) {
    if (chartStore.has(canvasId)) {
      chartStore.get(canvasId).destroy();
      chartStore.delete(canvasId);
    }
  }

  function createChart(canvasId, config) {
    const canvas = document.getElementById(canvasId);
    if (!canvas) return;

    destroyChart(canvasId);

    const chart = new Chart(canvas.getContext("2d"), config);
    chartStore.set(canvasId, chart);
  }

  function commonScales(yMin = 0, yMax = 100) {
    return {
      x: {
        grid: { color: COLORS.grid },
        ticks: { color: COLORS.white },
      },
      y: {
        min: yMin,
        max: yMax,
        grid: { color: COLORS.grid },
        ticks: { color: COLORS.white },
      },
    };
  }

  function normalizeMetrics(student) {
    return [
      Number(student.cgpa || 0) * 10,
      Number(student.attendance_pct || 0),
      Number(student.assignment_marks || 0),
      Number(student.class_behavior || 0) * 20,
      Number(student.lab_behavior || 0) * 20,
    ];
  }

  function renderStudentRadar(canvasId, student) {
    const labels = ["CGPA", "ATTENDANCE", "ASSIGNMENTS", "CLASS BEHAVIOR", "LAB BEHAVIOR"];
    const data = normalizeMetrics(student);

    createChart(canvasId, {
      type: "radar",
      data: {
        labels,
        datasets: [
          {
            data,
            borderColor: COLORS.white,
            backgroundColor: "rgba(255,255,255,0.15)",
            borderWidth: 1,
            pointBackgroundColor: COLORS.white,
            pointRadius: 2,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
        },
        scales: {
          r: {
            min: 0,
            max: 100,
            grid: { color: COLORS.grid },
            angleLines: { color: COLORS.grid },
            pointLabels: { color: COLORS.white, font: { family: "Helvetica Neue" } },
            ticks: { color: COLORS.white, backdropColor: "transparent" },
          },
        },
      },
    });
  }

  function renderStudentBar(canvasId, student) {
    const labels = ["CGPA", "ATTENDANCE", "ASSIGNMENTS", "CLASS", "LAB"];
    const data = normalizeMetrics(student);

    createChart(canvasId, {
      type: "bar",
      data: {
        labels,
        datasets: [
          {
            data,
            backgroundColor: COLORS.white,
            borderColor: COLORS.white,
            borderWidth: 1,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
        },
        scales: commonScales(0, 100),
      },
    });
  }

  function renderDashboardDonut(canvasId, students) {
    const counts = { DANGER: 0, ATTENTION: 0, CLEAR: 0 };
    students.forEach((student) => {
      if (counts[student.risk_level] !== undefined) counts[student.risk_level] += 1;
    });

    createChart(canvasId, {
      type: "doughnut",
      data: {
        labels: ["DANGER", "ATTENTION", "CLEAR"],
        datasets: [
          {
            data: [counts.DANGER, counts.ATTENTION, counts.CLEAR],
            backgroundColor: [COLORS.red, COLORS.yellow, COLORS.green],
            borderColor: "#000000",
            borderWidth: 1,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            labels: { color: COLORS.white },
          },
        },
      },
    });
  }

  function renderDashboardScatter(canvasId, students) {
    const danger = [];
    const attention = [];
    const clear = [];

    students.forEach((student) => {
      const point = {
        x: Number(student.attendance_pct || 0),
        y: Number(student.cgpa || 0),
        name: student.student_name,
      };

      if (student.risk_level === "DANGER") danger.push(point);
      else if (student.risk_level === "ATTENTION") attention.push(point);
      else clear.push(point);
    });

    createChart(canvasId, {
      type: "scatter",
      data: {
        datasets: [
          { label: "DANGER", data: danger, backgroundColor: COLORS.red },
          { label: "ATTENTION", data: attention, backgroundColor: COLORS.yellow },
          { label: "CLEAR", data: clear, backgroundColor: COLORS.green },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { labels: { color: COLORS.white } },
          tooltip: {
            callbacks: {
              label(ctx) {
                const label = ctx.raw && ctx.raw.name ? ctx.raw.name : "Student";
                return `${label}: Attendance ${ctx.raw.x}%, CGPA ${ctx.raw.y}`;
              },
            },
          },
        },
        scales: {
          x: {
            min: 0,
            max: 100,
            title: { display: true, text: "Attendance %", color: COLORS.white },
            grid: { color: COLORS.grid },
            ticks: { color: COLORS.white },
          },
          y: {
            min: 0,
            max: 10,
            title: { display: true, text: "CGPA", color: COLORS.white },
            grid: { color: COLORS.grid },
            ticks: { color: COLORS.white },
          },
        },
      },
    });
  }

  function renderDashboardRiskBar(canvasId, students) {
    const sorted = [...students]
      .filter((student) => typeof student.risk_score === "number")
      .sort((a, b) => a.risk_score - b.risk_score)
      .slice(0, 10);

    createChart(canvasId, {
      type: "bar",
      data: {
        labels: sorted.map((student) => `${student.roll_number} - ${student.student_name}`),
        datasets: [
          {
            data: sorted.map((student) => Number(student.risk_score || 0)),
            backgroundColor: sorted.map((student) => getRiskColor(student.risk_level)),
            borderColor: sorted.map((student) => getRiskColor(student.risk_level)),
            borderWidth: 1,
          },
        ],
      },
      options: {
        indexAxis: "y",
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
        },
        scales: commonScales(0, 100),
      },
    });
  }

  window.ACADEMIQCharts = {
    renderStudentRadar,
    renderStudentBar,
    renderDashboardDonut,
    renderDashboardScatter,
    renderDashboardRiskBar,
  };
})();
