// Dashboard Logic with Visual Rain Effect
// Author: Gemini for TranHaiViet

class RainfallDashboard {
  constructor() {
    this.form = document.getElementById("predictForm")
    this.predictBtn = document.getElementById("predictBtn")
    this.autoFillBtn = document.getElementById("autoFillBtn")
    this.forecastBtn = document.getElementById("forecastBtn")
    this.locationSelect = document.getElementById("locationId")

    this.resultContainer = document.getElementById("resultContainer")
    this.emptyState = document.getElementById("emptyState")

    // Output Elements
    this.rainfallValue = document.getElementById("rainfallValue")
    this.weatherStatus = document.getElementById("weatherStatus")
    this.weatherSub = document.getElementById("weatherSub")
    this.weatherWindow = document.getElementById("weatherWindow")

    // Animation Instance
    this.rainAnimation = null;

    this.init()
  }

  init() {
    if(this.form) this.form.addEventListener("submit", (e) => this.handlePredict(e))
    if(this.autoFillBtn) this.autoFillBtn.addEventListener("click", () => this.handleAutoFill())
    if(this.forecastBtn) this.forecastBtn.addEventListener("click", () => this.handleForecast())

    // Intro Animation
    anime({
        targets: '.modern-card',
        translateY: [20, 0], opacity: [0, 1],
        delay: anime.stagger(150), easing: 'easeOutQuad'
    });

    // --- KIỂM TRA URL ĐỂ TỰ ĐỘNG CHẠY ---
    this.checkUrlAndAutoRun();
  }

  async checkUrlAndAutoRun() {
      const urlParams = new URLSearchParams(window.location.search);
      const locId = urlParams.get('locId');

      const pTemp = urlParams.get('temp');
      const pLst = urlParams.get('lst');
      const pHum = urlParams.get('hum');
      const pWind = urlParams.get('wind');

      if (locId && this.locationSelect) {
          this.locationSelect.value = locId;

          if (this.locationSelect.value === locId) {
              console.log("📍 Phát hiện điều hướng. ID:", locId);

              // Nếu URL có đủ dữ liệu -> Dùng luôn
              if (pTemp && pLst && pHum && pWind) {
                  console.log("⚡ Dùng dữ liệu từ URL để đồng nhất");

                  document.getElementById('temperature').value = pTemp;
                  document.getElementById('lst').value = pLst;
                  document.getElementById('humidity').value = pHum;
                  document.getElementById('windSpeed').value = pWind;

                  ['temperature', 'lst', 'humidity', 'windSpeed'].forEach(id => {
                      anime({ targets: `#${id}`, backgroundColor: ['#fff', '#dcfce7', '#fff'], duration: 800 });
                  });

                  Swal.fire({
                      toast: true, position: 'top-end',
                      icon: 'success', title: 'Đồng bộ dữ liệu từ Bản đồ',
                      showConfirmButton: false, timer: 2000
                  });

                  setTimeout(() => this.handlePredict(null), 500);

              } else {
                  Swal.fire({
                      toast: true, position: 'top-end',
                      icon: 'info', title: 'Đang tải dữ liệu mới...',
                      showConfirmButton: false, timer: 1500
                  });
                  await this.handleAutoFill(true);
              }
          }
      }
  }

  async handlePredict(e) {
    if(e) e.preventDefault()

    const locationSelect = document.getElementById("locationId");
    const locId = locationSelect.value;
    const locName = locationSelect.options[locationSelect.selectedIndex].text;

    const inputs = ['lst', 'humidity', 'temperature', 'windSpeed'];
    const values = {};
    for (const id of inputs) values[id] = parseFloat(document.getElementById(id).value);

    if(!locId || Object.values(values).some(isNaN)) {
        Swal.fire({icon: 'warning', title: 'Thiếu thông tin', text: 'Vui lòng điền đủ dữ liệu'});
        return;
    }

    this.predictBtn.disabled = true;
    this.predictBtn.innerHTML = 'Đang phân tích mây...';

    try {
      const response = await fetch(`/api/predict?locationId=${locId}`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
             lst: values.lst, humidity: values.humidity,
             temperature: values.temperature, wind_speed: values.windSpeed,
             wind_unit: "kmh", location_name: locName
          }),
      });

      if (!response.ok) throw new Error("API Error");
      const data = await response.json();

      this.displayResults(data);

      // --- MỚI: LƯU KẾT QUẢ VÀO LOCAL STORAGE CHO TRANG SAFETY ---
      // Giúp trang "Kỹ Năng" biết vừa dự báo mưa to hay nhỏ để cảnh báo
      localStorage.setItem('lastPrediction', JSON.stringify({
          location: locName,
          rain: data.predicted_rainfall,
          timestamp: new Date().toISOString()
      }));

    } catch (error) {
      console.error(error);
      Swal.fire({icon: 'error', title: 'Lỗi', text: 'Không thể kết nối Server'});
    } finally {
      this.predictBtn.disabled = false;
      this.predictBtn.innerHTML = '🔮 Dự báo ngay';
    }
  }

  displayResults(data) {
    this.emptyState.style.display = 'none';
    this.resultContainer.style.display = 'block';

    if(window.innerWidth < 768) {
        this.resultContainer.scrollIntoView({ behavior: 'smooth' });
    }

    const rain = data.predicted_rainfall || 0;

    anime({
        targets: { val: 0 },
        val: rain,
        easing: 'easeOutExpo',
        duration: 2500,
        round: 10,
        update: (anim) => {
            this.rainfallValue.textContent = anim.animations[0].currentValue.toFixed(1);
        }
    });

    let config = this.getWeatherConfig(rain);

    this.weatherStatus.textContent = config.title;
    this.weatherSub.textContent = config.desc;
    this.weatherStatus.style.color = config.color;

    this.weatherWindow.className = `weather-window ${config.bgClass}`;

    this.createRainEffect(config.rainIntensity, config.windAngle);
  }

  getWeatherConfig(rain) {
      if (rain < 1) return {
          title: "Trời Tạnh Ráo", desc: "Không có dấu hiệu mưa",
          bgClass: "weather-clear", color: "#fef08a", rainIntensity: 0
      };
      if (rain < 10) return {
          title: "Mưa Nhỏ", desc: "Mưa lất phất rải rác",
          bgClass: "weather-rain-light", color: "#e0f2fe", rainIntensity: 20, windAngle: 0
      };
      if (rain < 50) return {
          title: "Mưa Vừa", desc: "Cần mang áo mưa",
          bgClass: "weather-rain-heavy", color: "#bae6fd", rainIntensity: 80, windAngle: 10
      };
      return {
          title: "Bão / Mưa Rất To", desc: "Cảnh báo ngập lụt nguy hiểm",
          bgClass: "weather-storm", color: "#fca5a5", rainIntensity: 200, windAngle: 30
      };
  }

  createRainEffect(count, angle = 0) {
      const oldDrops = document.querySelectorAll('.rain-particle');
      oldDrops.forEach(el => el.remove());
      if(this.rainAnimation) this.rainAnimation.pause();

      if (count === 0) return;

      const fragment = document.createDocumentFragment();
      for (let i = 0; i < count; i++) {
          const drop = document.createElement('div');
          drop.classList.add('rain-particle');
          drop.style.left = Math.random() * 100 + '%';
          drop.style.opacity = Math.random() * 0.5 + 0.1;
          drop.style.height = (Math.random() * 20 + 10) + 'px';
          fragment.appendChild(drop);
      }
      this.weatherWindow.appendChild(fragment);

      this.rainAnimation = anime({
          targets: '.rain-particle',
          translateY: [0, 600],
          translateX: [0, angle * 5],
          easing: 'linear',
          duration: () => anime.random(800, 1500),
          delay: anime.stagger(10),
          loop: true
      });
  }

  async handleAutoFill(isAutoRun = false) {
    const locId = document.getElementById("locationId").value;
    if (!locId) {
        if(!isAutoRun) Swal.fire({icon: 'info', title: 'Chọn tỉnh', text: 'Vui lòng chọn địa điểm trước'});
        return;
    }

    if(!isAutoRun) anime({ targets: '#autoFillBtn', scale: [1, 0.9, 1], duration: 300 });

    try {
        const res = await fetch(`/api/current-weather?locationId=${locId}`);
        const data = await res.json();

        ['temperature', 'humidity', 'lst', 'windSpeed'].forEach(key => {
            const el = document.getElementById(key);
            if(el) {
                let val = data[key] || data[key === 'windSpeed' ? 'wind_speed' : key];
                el.value = val;
                anime({ targets: el, backgroundColor: ['#fff', '#dbeafe', '#fff'], duration: 600 });
            }
        });

        if(!isAutoRun) {
            Swal.fire({icon: 'success', toast: true, position: 'top-end', title: 'Đã lấy dữ liệu', timer: 2000, showConfirmButton: false});
        } else {
            console.log("⚡ AutoFill xong -> Gọi Predict ngay lập tức...");
            setTimeout(() => this.handlePredict(null), 500);
        }

    } catch (e) {
        console.error(e);
    }
  }
async handleForecast() {
        const locId = document.getElementById("locationId").value;
        const locName = document.getElementById("locationId").options[document.getElementById("locationId").selectedIndex].text;

        if (!locId) {
            Swal.fire({
                icon: 'info',
                title: 'Chưa chọn địa điểm',
                text: 'Vui lòng chọn Tỉnh/Thành phố trước khi xem dự báo 5 ngày.',
                confirmButtonColor: '#0f172a' // var(--dark-bg)
            });
            return;
        }

        Swal.fire({
            title: `Đang phân tích dữ liệu vệ tinh cho ${locName}...`,
            html: 'Hệ thống đang chạy mô hình đệ quy để dự đoán xu hướng mưa.',
            allowOutsideClick: false,
            didOpen: () => Swal.showLoading()
        });

        try {
            const res = await fetch(`/api/forecast?locationId=${locId}`);
            if (!res.ok) throw new Error("Lỗi API");
            const data = await res.json();

            Swal.close();

            // --- XÂY DỰNG HTML THEO STYLE.CSS ---
            // Sử dụng class 'table-responsive' và 'fancy-table' từ style.css
            let htmlContent = `
                <div class="table-responsive" style="max-height: 400px; margin-top: 10px;">
                    <table class="fancy-table">
                        <thead>
                            <tr>
                                <th style="text-align: center;">Ngày Dự Báo</th>
                                <th style="text-align: center;">Lượng Mưa (mm)</th>
                                <th style="text-align: left;">Trạng Thái & Cảnh Báo</th>
                            </tr>
                        </thead>
                        <tbody>
            `;

            if (data.length === 0) {
                htmlContent += `<tr><td colspan="3" style="text-align:center;">Không có dữ liệu dự báo.</td></tr>`;
            } else {
                data.forEach(d => {
                    const r = d.predicted_rainfall;

                    // 1. Logic Text & Icon
                    let statusText = 'Trời nắng / Không mưa';
                    let icon = '☀️';

                    if (r > 100) { statusText = 'Mưa đặc biệt lớn (Nguy hiểm)'; icon = '⛈️'; }
                    else if (r > 50) { statusText = 'Mưa rất to / Giông bão'; icon = '⛈️'; }
                    else if (r > 25) { statusText = 'Mưa to'; icon = '🌧️'; }
                    else if (r > 10) { statusText = 'Mưa vừa'; icon = '🌦️'; }
                    else if (r > 0.5) { statusText = 'Mưa nhỏ / Rải rác'; icon = '☁️'; }

                    // 2. Logic Màu Sắc (Dùng biến CSS var)
                    // --success: #10b981; --warning: #f59e0b; --danger: #ef4444;
                    let colorVar = 'var(--success)'; // Mặc định xanh
                    let fontWeight = '500';

                    if (r > 50) { colorVar = 'var(--danger)'; fontWeight = '800'; } // Đỏ
                    else if (r > 25) { colorVar = '#ea580c'; fontWeight = '700'; } // Cam đậm
                    else if (r > 10) { colorVar = 'var(--warning)'; fontWeight = '600'; } // Vàng

                    // 3. Format Ngày (YYYY-MM-DD -> DD/MM)
                    let dateParts = d.message.split('-');
                    let dateStr = `${dateParts[2]}/${dateParts[1]}`;

                    htmlContent += `
                        <tr>
                            <td style="text-align: center; color: var(--text-secondary); font-weight: 600;">
                                ${dateStr}
                            </td>
                            <td style="text-align: center; font-size: 1.1rem; font-weight: 700; color: var(--dark-bg);">
                                ${r.toFixed(1)}
                            </td>
                            <td style="text-align: left; color: ${colorVar}; font-weight: ${fontWeight};">
                                <span style="margin-right: 8px;">${icon}</span> ${statusText}
                            </td>
                        </tr>
                    `;
                });
            }

            htmlContent += `
                        </tbody>
                    </table>
                </div>
                <div style="text-align: right; margin-top: 10px; font-size: 0.8rem; color: #94a3b8; font-style: italic;">
                    *Dự báo dựa trên mô hình Hybrid AI & Dữ liệu vệ tinh
                </div>
            `;

            // Hiển thị Popup với giao diện rộng hơn
            Swal.fire({
                title: `🔮 Dự Báo 5 Ngày Tới - ${locName}`,
                html: htmlContent,
                width: '800px', // Mở rộng chiều ngang để bảng đẹp hơn
                showConfirmButton: true,
                confirmButtonText: 'Đóng',
                confirmButtonColor: '#0f172a', // var(--dark-bg)
                background: '#ffffff',
                customClass: {
                    title: 'hero-title', // Tận dụng class font to đậm của header
                    popup: 'modern-card' // Tận dụng class bo góc, đổ bóng của card
                }
            });

        } catch (e) {
            console.error(e);
            Swal.fire('Lỗi', 'Không thể lấy dữ liệu dự báo từ Server.', 'error');
        }
    }
}

document.addEventListener("DOMContentLoaded", () => { new RainfallDashboard() });