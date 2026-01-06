// History Chart Logic - Real Data

class HistoryChartManager {
  constructor() {
    this.chartInstance = null
    // Đọc biến global từ Thymeleaf script tag
    this.data = (typeof serverHistoryData !== 'undefined') ? serverHistoryData : [];
    this.init()
  }

  init() {
    // Sắp xếp dữ liệu theo ngày tăng dần để vẽ biểu đồ cho đúng chiều thời gian
    // (Java đang trả về giảm dần để hiện Table đẹp, nên JS cần reverse hoặc sort lại)
    const sortedData = [...this.data].sort((a, b) => new Date(a.predictedForDate) - new Date(b.predictedForDate));
    
    this.renderChart(sortedData);
    this.updateStatistics(this.data);
  }

  renderChart(data) {
    const ctx = document.getElementById("comparisonChart")
    if (!ctx) return

    if (data.length === 0) return; // Không vẽ nếu không có data

    const labels = data.map((d) => d.predictedForDate)
    const forecastData = data.map((d) => d.predictedRainfall)
    // Map null thành null để Chart.js ngắt nét hoặc bỏ qua
    const actualData = data.map((d) => d.actualRainfall) 

    if (this.chartInstance) {
      this.chartInstance.destroy()
    }

    this.chartInstance = new Chart(ctx, {
      type: "line",
      data: {
        labels: labels,
        datasets: [
          {
            label: "Dự báo (mm)",
            data: forecastData,
            borderColor: "#0369a1",
            backgroundColor: "rgba(3, 105, 161, 0.1)",
            tension: 0.4,
            fill: true,
            borderWidth: 2,
            pointRadius: 4,
          },
          {
            label: "Thực tế (mm)",
            data: actualData, // ChartJS tự động xử lý null (ngắt đoạn)
            borderColor: "#dc2626",
            backgroundColor: "rgba(220, 38, 38, 0.1)",
            tension: 0.4,
            fill: true,
            borderWidth: 2,
            pointRadius: 4,
            spanGaps: true // Nối các điểm lại nếu muốn, hoặc để false để ngắt quãng
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
            legend: { position: 'top' },
            tooltip: { mode: 'index', intersect: false }
        },
        scales: {
            y: { beginAtZero: true }
        }
      },
    })
  }

  updateStatistics(data) {
    // Tính toán thống kê từ dữ liệu thật
    const totalRecords = data.length
    const maxRainfall = data.reduce((max, d) => Math.max(max, d.predictedRainfall || 0, d.actualRainfall || 0), 0)
    const pendingCount = data.filter((d) => d.actualRainfall == null).length
    
    // Tính độ chính xác (Chỉ tính những record đã có thực tế)
    let totalAccuracy = 0;
    let countAcc = 0;
    data.forEach(d => {
        if(d.actualRainfall != null && d.actualRainfall > 0) {
            // Công thức ví dụ: 1 - |pred - real| / real
            let error = Math.abs(d.predictedRainfall - d.actualRainfall) / d.actualRainfall;
            if(d.actualRainfall < 1) error = Math.abs(d.predictedRainfall - d.actualRainfall); // Xử lý số nhỏ
            let acc = Math.max(0, (1 - error) * 100);
            totalAccuracy += acc;
            countAcc++;
        }
    });
    const avgAccuracy = countAcc > 0 ? (totalAccuracy / countAcc) : 0;

    // Update DOM nếu element tồn tại
    const elTotal = document.getElementById("historyTotal");
    const elAcc = document.getElementById("historyAccuracy");
    const elMax = document.getElementById("historyMaxRain");
    const elPending = document.getElementById("historyPending");

    if (elTotal) elTotal.textContent = totalRecords;
    if (elAcc) elAcc.textContent = avgAccuracy.toFixed(1);
    if (elMax) elMax.textContent = maxRainfall.toFixed(1);
    if (elPending) elPending.textContent = pendingCount;
  }
}

document.addEventListener("DOMContentLoaded", () => {
  new HistoryChartManager()
})