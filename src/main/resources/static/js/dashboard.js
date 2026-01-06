// Dashboard Logic with Visual Rain Effect
// Author: Gemini for TranHaiViet

class RainfallDashboard {
  constructor() {
    this.form = document.getElementById("predictForm")
    this.predictBtn = document.getElementById("predictBtn")
    this.autoFillBtn = document.getElementById("autoFillBtn")
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
}

document.addEventListener("DOMContentLoaded", () => { new RainfallDashboard() });