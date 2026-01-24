// Dashboard Logic Final v12.0 (Clean & Zero Motion)
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

    this.init()
  }

  init() {
    if(this.form) this.form.addEventListener("submit", (e) => this.handlePredict(e))
    if(this.autoFillBtn) this.autoFillBtn.addEventListener("click", () => this.handleAutoFill())
    if(this.forecastBtn) this.forecastBtn.addEventListener("click", () => this.handleForecast())

    anime({
        targets: '.modern-card',
        translateY: [20, 0], opacity: [0, 1],
        delay: anime.stagger(150), easing: 'easeOutQuad'
    });

    this.checkUrlAndAutoRun();
  }

  // --- LOGIC NHẬN DỮ LIỆU TỪ MAP & TRIGGER BACKGROUND ---
  async checkUrlAndAutoRun() {
      const urlParams = new URLSearchParams(window.location.search);
      const locId = urlParams.get('locId');

      const pTemp = urlParams.get('temp');
      const pLst = urlParams.get('lst');
      const pHum = urlParams.get('hum');
      const pWind = urlParams.get('wind');

      if (locId && this.locationSelect) {
          this.locationSelect.value = locId;

          // [Fix] Kích hoạt sự kiện đổi ảnh nền (background logic bên prediction.html)
          this.locationSelect.dispatchEvent(new Event('change'));

          if (pTemp && pLst && pHum && pWind) {
              document.getElementById('temperature').value = pTemp;
              document.getElementById('lst').value = pLst;
              document.getElementById('humidity').value = pHum;
              document.getElementById('windSpeed').value = pWind;

              ['temperature', 'lst', 'humidity', 'windSpeed'].forEach(id => {
                  anime({ targets: `#${id}`, backgroundColor: ['#fff', '#dcfce7', '#fff'], duration: 800 });
              });

              Swal.fire({ toast: true, position: 'top-end', icon: 'success', title: 'Đã đồng bộ dữ liệu', showConfirmButton: false, timer: 1500 });
              setTimeout(() => this.handlePredict(null), 500);
          } else {
              await this.handleAutoFill(true);
          }
      }
  }

  async handleAutoFill(isAutoRun = false) {
    const locId = this.locationSelect.value;
    if (!locId) {
        if(!isAutoRun) Swal.fire({icon: 'info', title: 'Chọn tỉnh', text: 'Vui lòng chọn địa điểm trước'});
        return;
    }

    if(!isAutoRun) {
        this.autoFillBtn.innerHTML = '⏳ Đang lấy...';
        this.autoFillBtn.disabled = true;
    }

    try {
        const res = await fetch(`/api/current-weather?locationId=${locId}`);
        const data = await res.json();

        // Trigger đổi ảnh nền
        this.locationSelect.dispatchEvent(new Event('change'));

        const mapKeys = {
            'temperature': data.temperature,
            'humidity': data.humidity,
            'lst': data.lst,
            'windSpeed': (data.inputWindSpeed !== undefined) ? data.inputWindSpeed : (data.wind_speed || 0)
        };

        for (const [id, val] of Object.entries(mapKeys)) {
            const el = document.getElementById(id);
            if(el) {
                el.value = val;
                anime({ targets: el, backgroundColor: ['#fff', '#dbeafe', '#fff'], duration: 600 });
            }
        }

        if(!isAutoRun) Swal.fire({icon: 'success', toast: true, position: 'top-end', title: 'Đã lấy dữ liệu mới nhất', timer: 2000, showConfirmButton: false});
        else setTimeout(() => this.handlePredict(null), 500);

    } catch (e) {
        console.error(e);
        if(!isAutoRun) Swal.fire('Lỗi', 'Không lấy được dữ liệu thời tiết', 'error');
    } finally {
        if(!isAutoRun) {
            this.autoFillBtn.innerHTML = '📡 Lấy dữ liệu';
            this.autoFillBtn.disabled = false;
        }
    }
  }

  async handlePredict(e) {
    if(e) e.preventDefault()
    const locId = this.locationSelect.value;
    const locName = this.locationSelect.options[this.locationSelect.selectedIndex].text;
    const v = {
        lst: parseFloat(document.getElementById('lst').value),
        humidity: parseFloat(document.getElementById('humidity').value),
        temperature: parseFloat(document.getElementById('temperature').value),
        windSpeed: parseFloat(document.getElementById('windSpeed').value)
    };

    if(!locId || Object.values(v).some(isNaN)) {
        Swal.fire({icon: 'warning', title: 'Thiếu thông tin', text: 'Vui lòng điền đủ dữ liệu'});
        return;
    }

    this.predictBtn.disabled = true; this.predictBtn.innerHTML = 'Đang phân tích...';

    try {
      const res = await fetch(`/api/predict?locationId=${locId}`, {
          method: "POST", headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ lst: v.lst, humidity: v.humidity, temperature: v.temperature, wind_speed: v.windSpeed, wind_unit: "kmh", location_name: locName })
      });
      const data = await res.json();
      this.displayResults(data);
    } catch (error) { Swal.fire({icon: 'error', title: 'Lỗi', text: 'Không thể kết nối Server'}); }
    finally { this.predictBtn.disabled = false; this.predictBtn.innerHTML = '🔮 Dự báo ngay'; }
  }

  displayResults(data) {
    this.emptyState.style.display = 'none';
    this.resultContainer.style.display = 'block';
    if(window.innerWidth < 768) this.resultContainer.scrollIntoView({ behavior: 'smooth' });

    const rain = data.predicted_rainfall || 0;
    anime({ targets: { val: 0 }, val: rain, easing: 'easeOutExpo', duration: 2500, round: 10, update: (a) => this.rainfallValue.textContent = a.animations[0].currentValue.toFixed(1) });

    let cfg = this.getWeatherConfig(rain);
    this.weatherStatus.textContent = cfg.title;
    this.weatherSub.textContent = cfg.desc;
    this.weatherStatus.style.color = cfg.textColor;
    this.weatherWindow.className = `weather-window ${cfg.stateClass}`;
    this.createRainEffect(cfg.rainCount, cfg.windAngle);
  }

  getWeatherConfig(rain) {
      if (rain < 1) return { title: "Trời Tạnh Ráo", desc: "Tầm nhìn tốt.", stateClass: "state-safe", textColor: "#fef08a", rainCount: 0 };
      if (rain < 10) return { title: "Mưa Nhỏ", desc: "Mưa rải rác.", stateClass: "state-rain", textColor: "#e0f2fe", rainCount: 30, windAngle: 0 };
      if (rain < 50) return { title: "Mưa Vừa", desc: "Cần áo mưa.", stateClass: "state-rain", textColor: "#bae6fd", rainCount: 100, windAngle: 10 };
      return { title: "BÃO / MƯA TO", desc: "CẢNH BÁO!", stateClass: "state-storm", textColor: "#fca5a5", rainCount: 300, windAngle: 40 };
  }

  createRainEffect(count, angle = 0) {
      const container = document.getElementById('rainContainer');
      container.innerHTML = '';
      if (count === 0) return;
      const frag = document.createDocumentFragment();
      for (let i = 0; i < count; i++) {
          const d = document.createElement('div');
          d.classList.add('rain-drop');
          d.style.left = Math.random() * 100 + '%';
          d.style.animationDuration = (0.5 + Math.random() * 0.5) + 's';
          d.style.animationDelay = (Math.random() * 2) + 's';
          d.style.transform = `rotate(${angle}deg)`;
          frag.appendChild(d);
      }
      container.appendChild(frag);
  }

  // --- DỰ BÁO 5 NGÀY (CLEAN HTML - DỰA HOÀN TOÀN VÀO STYLE.CSS) ---
  async handleForecast() {
        const locId = this.locationSelect.value;
        const locName = this.locationSelect.options[this.locationSelect.selectedIndex].text;
        if (!locId) { Swal.fire({icon: 'info', title: 'Chọn tỉnh', text: 'Vui lòng chọn địa điểm'}); return; }

        Swal.fire({ title: `Đang phân tích dữ liệu 5 ngày...`, didOpen: () => Swal.showLoading() });

        try {
            const res = await fetch(`/api/forecast?locationId=${locId}`);
            if (!res.ok) throw new Error("API Error");
            const data = await res.json();
            Swal.close();

            // Không còn CSS nhúng ở đây nữa -> Style.css lo hết
            let html = `
                <div class="table-responsive" style="max-height: 400px; margin-top: 10px; overflow-y: auto;">
                    <table class="fancy-table" style="width: 100%; border-collapse: collapse;">
                        <thead style="position: sticky; top: 0; z-index: 20;">
                            <tr>
                                <th style="padding: 15px; text-align: center; background: #f8fafc; color: #475569;">Ngày</th>
                                <th style="text-align: center; background: #f8fafc; color: #475569;">🌡️ Nhiệt</th>
                                <th style="text-align: center; background: #f8fafc; color: #475569;">💧 Ẩm</th>
                                <th style="text-align: center; background: #f8fafc; color: #475569;">💨 Gió</th>
                                <th style="text-align: center; background: #f8fafc; color: #475569;">☔ Mưa (mm)</th>
                                <th style="text-align: left; padding-left: 20px; background: #f8fafc; color: #475569;">Trạng thái</th>
                            </tr>
                        </thead>
                        <tbody>`;

            if (data.length === 0) html += `<tr><td colspan="6" style="text-align:center;">Không có dữ liệu.</td></tr>`;
            else {
                data.forEach(d => {
                    let p = d.message.split('|');
                    let date = p[0].split('-').slice(1).reverse().join('/');
                    let temp = p[1] || '--'; let hum = p[2] || '--'; let wind = p[3] || '--';
                    let r = d.predicted_rainfall;
                    let st = r > 50 ? 'Bão / Mưa to' : (r > 10 ? 'Mưa vừa' : (r > 0.5 ? 'Mưa nhỏ' : 'Tạnh ráo'));
                    let icon = r > 10 ? (r > 50 ? '⛈️' : '🌧️') : (r > 0.5 ? '🌦️' : '☀️');
                    let col = r > 10 ? (r > 50 ? '#ef4444' : '#f59e0b') : '#10b981';
                    let bgBadge = r > 10 ? (r > 50 ? '#fef2f2' : '#fffbeb') : '#f0fdf4';

                    html += `<tr class="forecast-row">
                        <td style="font-weight: 700; color: #64748b;">${date}</td>
                        <td style="color: #334155;">${parseFloat(temp).toFixed(1)}°C</td>
                        <td style="color: #334155;">${parseFloat(hum).toFixed(0)}%</td>
                        <td style="color: #334155;">${parseFloat(wind).toFixed(1)} m/s</td>
                        <td style="font-weight: 800; color: #2563eb;">${r.toFixed(1)}</td>
                        <td style="text-align: left; padding-left: 20px;">
                            <span style="background: ${bgBadge}; color: ${col}; padding: 5px 10px; border-radius: 20px; font-weight: 600; font-size: 0.85rem; border: 1px solid ${col}40;">${icon} ${st}</span>
                        </td>
                    </tr>`;
                });
            }
            html += `</tbody></table></div>`;

            Swal.fire({ title: `🔮 Dự Báo 5 Ngày Tới - ${locName}`, html: html, width: '850px', showConfirmButton: true, confirmButtonText: 'Đóng', confirmButtonColor: '#0f172a' });
        } catch (e) { console.error(e); Swal.fire('Lỗi', 'Không thể lấy dữ liệu.', 'error'); }
    }
}
document.addEventListener("DOMContentLoaded", () => { new RainfallDashboard() });