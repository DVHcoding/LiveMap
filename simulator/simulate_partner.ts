import mqtt from "mqtt";

const BROKER_URL = "mqtt://broker.emqx.io:1883";
const PAIR_CODE = process.argv[2] || "OK";
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

// 6. Kết nối MQTT và phát sóng định vị thời gian thực với vật lý và mạng chập chờn y như thật
const client = mqtt.connect(BROKER_URL, {
    clientId: `Simulated_Partner_HN_${Math.random().toString(36).substring(7)}`,
    clean: true,
    connectTimeout: 10000,
});

function sleep(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
}

let currentIndex = 0;
let battery = 92;
let movingForward = true;
let currentSpeed = 10.0; // m/s (36 km/h)
let cycleCount = 0;

client.on("connect", async () => {
    console.log("✅ Đã kết nối EMQX MQTT!");
    console.log(`🚗 BẮT ĐẦU MÔ PHỎNG LƯỚT SIÊU MƯỢT (1.0s/lần, vận tốc biến thiên tự nhiên)...\n`);

    while (true) {
        cycleCount++;
        const wp = waypoints[currentIndex];
        const nextWp = waypoints[Math.min(currentIndex + 1, waypoints.length - 1)];

        // 1. Tính toán góc cua để điều chỉnh vận tốc tự nhiên
        let angleDiff = Math.abs(nextWp.bearing - wp.bearing);
        if (angleDiff > 180) angleDiff = 360 - angleDiff;

        // Vận tốc biến thiên mềm mại:
        if (angleDiff > 25) {
            // Khúc cua gắt -> hạ tốc độ nhẹ xuống 18 - 22 km/h
            currentSpeed = 5.5;
        } else if (cycleCount % 20 < 10) {
            // Tăng tốc trên đường thẳng: 40 - 48 km/h
            currentSpeed = 12.0;
        } else {
            // Tốc độ bình thường: 32 - 36 km/h
            currentSpeed = 9.5;
        }

        // Hao pin nhẹ
        if (cycleCount % 60 === 0 && battery > 10) {
            battery -= 1;
        }

        const payload = {
            senderId: "USER_B",
            senderName: "Người ấy (Mô Phỏng)",
            latitude: wp.lat,
            longitude: wp.lng,
            bearing: Math.round(wp.bearing * 10) / 10,
            speed: Math.round(currentSpeed * 10) / 10,
            accuracy: 2.0,
            batteryLevel: battery,
            isCharging: false,
            timestamp: Date.now(),
            status: "ACTIVE",
            deviceId: "DEV_SIMULATOR_B"
        };

        client.publish(TOPIC, JSON.stringify(payload), { qos: 0 });

        const speedKmh = (currentSpeed * 3.6).toFixed(1);
        console.log(
            `[🚗 LƯỚT MƯỢT] Bước ${currentIndex + 1}/${waypoints.length} | Tốc độ: ${speedKmh} km/h | Góc lái: ${wp.bearing.toFixed(0)}° | Tọa độ: ${wp.lat.toFixed(6)}, ${wp.lng.toFixed(6)}`
        );

        // Tiến về phía trước theo vận tốc thực tế (STEP_METERS = 5m)
        const stepAdvance = Math.max(1, Math.round((currentSpeed * 1.0) / STEP_METERS));
        if (movingForward) {
            currentIndex += stepAdvance;
            if (currentIndex >= waypoints.length - 1) {
                currentIndex = waypoints.length - 1;
                movingForward = false;
                console.log("🏁 ĐÃ ĐẾN NHÀ HÁT LỚN HÀ NỘI! Bắt đầu quay đầu về lại...");
            }
        } else {
            currentIndex -= stepAdvance;
            if (currentIndex <= 0) {
                currentIndex = 0;
                movingForward = true;
                console.log("🏁 ĐÃ VỀ LẠI HOÀNG TĂNG BÍ! Bắt đầu hành trình mới...");
            }
        }

        // Nhịp phát sóng chuẩn xác 1000ms đều đặn
        await sleep(1000);
    }
});

client.on("error", (err) => {
    console.error("❌ Lỗi MQTT:", err.message);
});
