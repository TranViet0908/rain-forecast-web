// Map Monitor Logic (Clean Version - No Radar)
// Author: Gemini for TranHaiViet

class MapMonitor {
    constructor() {
        this.map = null;
        this.initMap();
    }

    initMap() {
        // 1. Khởi tạo Map, trung tâm là Miền Trung (Huế/Đà Nẵng)
        this.map = L.map('map').setView([16.4637, 107.5909], 7);

        // 2. Load Tiles (Giao diện Tối - Dark Matter) - Chỉ dùng lớp này cho sạch
        L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
            attribution: '&copy; OpenStreetMap &copy; CartoDB',
            subdomains: 'abcd',
            maxZoom: 19
        }).addTo(this.map);

        // 3. Load Data từ API của mình
        this.loadLocations();
    }

    async loadLocations() {
        try {
            const response = await fetch('/api/map/locations');
            const data = await response.json();

            data.forEach(loc => this.addLocationMarker(loc));

        } catch (error) {
            console.error("Lỗi load map data:", error);
        }
    }

    addLocationMarker(loc) {
        // Cấu hình màu sắc theo trạng thái
        let color = '#10b981'; // Safe (Green)
        let radius = 20000;    // 20km radius highlight

        if (loc.status === 'WARNING') {
            color = '#f59e0b'; // Yellow
            radius = 30000;
        } else if (loc.status === 'DANGER') {
            color = '#ef4444'; // Red
            radius = 40000;
        }

        // A. TẠO VÙNG HIGHLIGHT (CIRCLE)
        L.circle([loc.lat, loc.lon], {
            color: color,
            fillColor: color,
            fillOpacity: 0.2,
            radius: radius,
            weight: 1
        }).addTo(this.map);

        // B. TẠO PIN (MARKER)
        const customIcon = L.divIcon({
            className: 'custom-pin',
            html: `<div style="background-color: ${color}; width: 16px; height: 16px; border-radius: 50%; border: 2px solid white; box-shadow: 0 0 10px ${color};"></div>`,
            iconSize: [16, 16],
            iconAnchor: [8, 8]
        });

        const marker = L.marker([loc.lat, loc.lon], {icon: customIcon}).addTo(this.map);

        // C. TẠO POPUP (Nội dung ban đầu là Loading)
        marker.bindPopup(`<div style="color: #cbd5e1; padding: 10px; text-align: center;">⏳ Đang tải dữ liệu thực tế...</div>`, {
            maxWidth: 320,
            minWidth: 280
        });

        // D. SỰ KIỆN CLICK (Gọi API Detail & Update Popup)
        marker.on('click', async () => {
            // Zoom nhẹ vào
            this.map.flyTo([loc.lat, loc.lon], 10, { animate: true, duration: 1.0 });

            try {
                // Gọi API lấy chi tiết
                const res = await fetch(`/api/map/detail/${loc.id}`);
                const detail = await res.json();

                // Xác định text trạng thái
                let rainText = 'An toàn';
                if (detail.status === 'WARNING') rainText = 'Mưa vừa';
                else if (detail.status === 'DANGER') rainText = 'CẢNH BÁO MƯA LỚN';

                // Tạo nội dung Popup chi tiết
                const popupContent = `
                    <div style="font-family: 'Segoe UI', sans-serif;">
                        <!-- Header: Tên tỉnh + Địa chỉ cụ thể -->
                        <div style="text-align: center; margin-bottom: 10px;">
                            <h3 style="margin: 0; color: ${color}; font-size: 1.2rem; text-transform: uppercase; font-weight: 800;">${detail.name}</h3>
                            <div style="font-size: 0.85rem; color: #94a3b8; margin-top: 4px;">
                                <span style="display:inline-block; vertical-align: middle;">📍</span> ${detail.addressDetail}
                            </div>
                        </div>

                        <!-- Dự báo lượng mưa (Nổi bật nhất) -->
                        <div style="background: rgba(255,255,255,0.05); border-radius: 8px; padding: 10px; text-align: center; margin-bottom: 12px; border: 1px solid ${color}40;">
                            <div style="font-size: 0.8rem; text-transform: uppercase; color: #cbd5e1; letter-spacing: 1px;">Dự báo mưa</div>
                            <div style="font-size: 2.2rem; font-weight: 800; color: #fff; line-height: 1.1;">
                                ${detail.predictedRain.toFixed(1)} <span style="font-size: 0.9rem; font-weight: normal; color: #94a3b8;">mm</span>
                            </div>
                            <div style="color: ${color}; font-size: 0.9rem; font-weight: 600; margin-top: 4px;">${rainText}</div>
                        </div>

                        <!-- Grid thông số chi tiết (LST, Temp, Hum, Wind) -->
                        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 8px; font-size: 0.85rem; color: #e2e8f0;">
                            <div style="background: rgba(0,0,0,0.3); padding: 6px; border-radius: 4px; display: flex; align-items: center; justify-content: space-between;">
                                <span style="color: #94a3b8;">🌡 KK</span>
                                <span style="font-weight: bold;">${detail.currentTemp} °C</span>
                            </div>
                            <div style="background: rgba(0,0,0,0.3); padding: 6px; border-radius: 4px; display: flex; align-items: center; justify-content: space-between;">
                                <span style="color: #94a3b8;">🔥 LST</span>
                                <span style="font-weight: bold;">${detail.currentLst} °C</span>
                            </div>
                            <div style="background: rgba(0,0,0,0.3); padding: 6px; border-radius: 4px; display: flex; align-items: center; justify-content: space-between;">
                                <span style="color: #94a3b8;">💧 Ẩm</span>
                                <span style="font-weight: bold;">${detail.currentHumidity} %</span>
                            </div>
                            <div style="background: rgba(0,0,0,0.3); padding: 6px; border-radius: 4px; display: flex; align-items: center; justify-content: space-between;">
                                <span style="color: #94a3b8;">💨 Gió</span>
                                <span style="font-weight: bold;">${detail.currentWind} m/s</span>
                            </div>
                        </div>

                        <hr style="border-color: rgba(255,255,255,0.1); margin: 12px 0;">

                        <!-- Nút chi tiết: QUAN TRỌNG - Gửi kèm dữ liệu qua URL để Dashboard đồng nhất -->
                        <button onclick="window.location.href='/?locId=${detail.id}&temp=${detail.currentTemp}&lst=${detail.currentLst}&hum=${detail.currentHumidity}&wind=${detail.currentWind}'"
                            style="background: ${color}; color: #0f172a; border: none; padding: 8px 15px; border-radius: 6px; font-weight: 700; cursor: pointer; width: 100%; transition: all 0.2s;">
                            Xem chi tiết & Mô phỏng
                        </button>
                    </div>
                `;

                // Update nội dung popup
                marker.getPopup().setContent(popupContent);
                marker.getPopup().update();

            } catch (err) {
                console.error("Lỗi lấy chi tiết:", err);
                marker.getPopup().setContent(`<div style="color: #ef4444; padding:10px; text-align:center;">Lỗi tải dữ liệu!</div>`);
            }
        });
    }
}

document.addEventListener('DOMContentLoaded', () => {
    new MapMonitor();
});