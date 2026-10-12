import mqtt from "mqtt";

const BROKER_URL = "mqtt://broker.emqx.io:1883";
const PAIR_CODE = "live_couple_8888";
const TOPIC = `livemap/pair/${PAIR_CODE}/location`;

// Xuất phát từ vị trí thực tế của bạn (Hoàng Tăng Bí) -> Đích đến: Nhà hát Lớn Hà Nội (Tràng Tiền)
const START_LNG = 105.771442;
const START_LAT = 21.081912;
const END_LNG = 105.8576;
const END_LAT = 21.0244;

console.log("🚀 Đang khởi tạo lộ trình đi trên đường thực tế đến Nhà hát Lớn Hà Nội...");

// 1. Công thức tính khoảng cách Haversine (mét)
function calculateDistance(lat1: number, lon1: number, lat2: number, lon2: number): number {
    const R = 6371e3;
    const toRad = (d: number) => (d * Math.PI) / 180;
    const phi1 = toRad(lat1);
    const phi2 = toRad(lat2);
    const deltaPhi = toRad(lat2 - lat1);
    const deltaLambda = toRad(lon2 - lon1);

    const a =
        Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2) +
        Math.cos(phi1) * Math.cos(phi2) * Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
}

// 2. Tính góc hướng (bearing) giữa 2 tọa độ (0° - 360°)
function calculateBearing(lat1: number, lon1: number, lat2: number, lon2: number): number {
    const toRad = (d: number) => (d * Math.PI) / 180;
    const toDeg = (r: number) => (r * 180) / Math.PI;

    const y = Math.sin(toRad(lon2 - lon1)) * Math.cos(toRad(lat2));
    const x =
        Math.cos(toRad(lat1)) * Math.sin(toRad(lat2)) -
        Math.sin(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.cos(toRad(lon2 - lon1));
    const brng = toDeg(Math.atan2(y, x));
    return (brng + 360) % 360;
}

// 3. Tải lộ trình đường ô tô/xe máy từ OSRM OpenStreetMap
const routeUrl = `https://router.project-osrm.org/route/v1/driving/${START_LNG},${START_LAT};${END_LNG},${END_LAT}?overview=full&geometries=geojson`;
console.log("📍 Đang kết nối OSRM để lấy tọa độ mặt đường chính xác...");

const res = await fetch(routeUrl);
const data = await res.json();

if (!data.routes || data.routes.length === 0) {
    console.error("❌ Không tìm thấy đường đi OSRM!");
    process.exit(1);
}

const rawCoords: [number, number][] = data.routes[0].geometry.coordinates; // [lng, lat]
const totalRouteDist = data.routes[0].distance;
console.log(`🛣️ Lộ trình: ${(totalRouteDist / 1000).toFixed(2)} km từ Hoàng Tăng Bí đến Nhà hát Lớn Hà Nội (${rawCoords.length} đỉnh đường).`);

// 4. Tính toán khoảng cách tích lũy (Cumulative Distance) dọc theo Polyline đường đi
const cumDists: number[] = [0];
for (let i = 0; i < rawCoords.length - 1; i++) {
    const [lng1, lat1] = rawCoords[i];
    const [lng2, lat2] = rawCoords[i + 1];
    const segDist = calculateDistance(lat1, lng1, lat2, lng2);
    cumDists.push(cumDists[cumDists.length - 1] + segDist);
}

// 5. Nội suy mẫu đều đặn (Resampling) từng 5 mét chính xác 100% nằm trên mặt đường
// Gửi mỗi 500ms một lần -> 5 mét / 0.5s = 10 m/s = 36 km/h (tốc độ xe máy/ô tô chuẩn trong phố)
interface Waypoint {
    lat: number;
    lng: number;
    bearing: number;
    speed: number;
}

const STEP_METERS = 5.0; // 5 mét mỗi bước
const INTERVAL_MS = 500; // Cập nhật mỗi 0.5 giây cho độ mượt tối đa
const totalPoints = Math.floor(totalRouteDist / STEP_METERS);
const waypoints: Waypoint[] = [];

let segIdx = 0;
for (let step = 0; step < totalPoints; step++) {
    const targetDist = step * STEP_METERS;

    // Tìm đoạn đường chứa khoảng cách targetDist
    while (segIdx < cumDists.length - 2 && cumDists[segIdx + 1] < targetDist) {
        segIdx++;
    }

    const dStart = cumDists[segIdx];
    const dEnd = cumDists[segIdx + 1];
    const segLen = dEnd - dStart;

    const [lng1, lat1] = rawCoords[segIdx];
    const [lng2, lat2] = rawCoords[segIdx + 1];

    const frac = segLen > 0.001 ? (targetDist - dStart) / segLen : 0;
    const lat = lat1 + (lat2 - lat1) * frac;
    const lng = lng1 + (lng2 - lng1) * frac;
    const bearing = calculateBearing(lat1, lng1, lat2, lng2);

    waypoints.push({
        lat,
        lng,
        bearing,
        speed: 10.0 // 10 m/s = 36 km/h
    });
}

console.log(`✨ Đã tạo ${waypoints.length} điểm mốc di chuyển liên tục, bám sát tâm đường 100%!`);

// 6. Kết nối MQTT và phát sóng định vị thời gian thực
const client = mqtt.connect(BROKER_URL, {
    clientId: `Simulated_Partner_HN_${Math.random().toString(36).substring(7)}`,
    clean: true,
    connectTimeout: 10000,
});

let currentIndex = 0;
let battery = 92;
let movingForward = true;

client.on("connect", () => {
    console.log("✅ Đã kết nối EMQX MQTT!");
    console.log(`🚗 Bắt đầu phát sóng Người ấy di chuyển dọc đường phố đến Nhà hát Lớn Hà Nội...\n`);

    setInterval(() => {
        if (waypoints.length === 0) return;

        const wp = waypoints[currentIndex];

        // Hao pin thực tế theo thời gian
        if (currentIndex % 60 === 0 && battery > 10) {
            battery -= 1;
        }

        const payload = {
            senderId: "USER_B",
            senderName: "Người ấy",
            latitude: wp.lat,
            longitude: wp.lng,
            bearing: Math.round(wp.bearing * 10) / 10,
            speed: wp.speed,
            accuracy: 1.5,
            batteryLevel: battery,
            isCharging: false,
            timestamp: Date.now()
        };

        client.publish(TOPIC, JSON.stringify(payload), { qos: 0 });

        if (currentIndex % 4 === 0) {
            const speedKmh = (wp.speed * 3.6).toFixed(1);
            console.log(
                `[🎭 ĐANG TRÊN ĐƯỜNG ĐẾN NHÀ HÁT LỚN] Bước ${currentIndex + 1}/${waypoints.length} | Tọa độ: ${wp.lat.toFixed(6)}, ${wp.lng.toFixed(6)} | Hướng: ${wp.bearing.toFixed(0)}° | Tốc độ: ${speedKmh} km/h`
            );
        }

        // Tự động quay đầu nếu đi hết lộ trình (15km)
        if (movingForward) {
            currentIndex++;
            if (currentIndex >= waypoints.length - 1) {
                movingForward = false;
            }
        } else {
            currentIndex--;
            if (currentIndex <= 0) {
                movingForward = true;
            }
        }
    }, INTERVAL_MS);
});

client.on("error", (err) => {
    console.error("❌ Lỗi MQTT:", err.message);
});
