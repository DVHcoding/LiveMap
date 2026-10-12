# LiveMap — Ứng Dụng Chia Sẻ Vị Trí Thời Gian Thực (Chạy Ngầm Siêu Mượt Cho 2 Người)

Ứng dụng Android viết bằng **Kotlin + Jetpack Compose**, chuyên biệt để **2 người** chia sẻ vị trí trực tiếp cho nhau giống như Zalo, nhưng khắc phục triệt để nhược điểm giật lag/delay nhờ cơ chế **True Realtime & Nội suy chuyển động mượt mà (Smooth Interpolation)**.

---

## 🌟 Tính Năng Nổi Bật

1. **Chạy ngầm liên tục (Foreground Service + WakeLock)**:
   - Dù bạn đóng app, vuốt bỏ khỏi đa nhiệm (Recents), hay khóa màn hình đút túi quần, GPS vẫn được stream ngầm liên tục theo thời gian thực (1 giây/lần).
   - Có thông báo trạng thái cố định trên thanh Notification giúp hệ điều hành Android (kể cả Android 14+) không bị hệ thống tự ý tắt (Doze mode).
2. **Chuyển động lướt mượt mà 60fps (Không bị dịch chuyển giật cục như Zalo)**:
   - Áp dụng thuật toán **Smooth Marker Interpolation**: Khi nhận tọa độ mới, Marker sẽ lướt nhẹ nhàng từ điểm cũ sang điểm mới theo đường ngắn nhất trong 1000ms.
   - Xoay hướng xe/người mượt mà theo góc di chuyển (Bearing).
3. **Độ trễ siêu thấp (<100ms) qua MQTT Realtime**:
   - Truyền nhận gói tin siêu nhẹ (JSON ~100 bytes) qua giao thức MQTT bảo mật, không cần cài server riêng, không tốn pin/băng thông.
4. **Bản đồ OsmDroid (OpenStreetMap)**:
   - Hoàn toàn miễn phí, hiển thị đường phố chi tiết tại Việt Nam và toàn cầu.
   - Không yêu cầu API Key hay liên kết thẻ tín dụng như Google Maps SDK.
5. **Hiển thị thông tin đối phương chi tiết**:
   - Tốc độ di chuyển theo km/h.
   - % Pin và trạng thái đang sạc của đối phương.
   - Nút căn chỉnh nhanh: **Tâm điểm Tôi**, **Tâm điểm Người ấy**, **Xem cả 2 người cùng lúc**.

---

## 🚀 Hướng Dẫn Sử Dụng Cho 2 Người

1. **Cài đặt APK lên 2 điện thoại**:
   - File APK debug đã được build sẵn tại:
     `app/build/outputs/apk/debug/app-debug.apk`
2. **Khởi động & Cấp quyền**:
   - Mở ứng dụng, cấp quyền **Vị trí (Khi dùng ứng dụng / Luôn cho phép)** và quyền **Thông báo**.
   - Bỏ giới hạn tối ưu pin khi hệ điều hành hỏi để ứng dụng chạy ngầm không bị tắt.
3. **Ghép đôi 2 máy**:
   - Bấm vào biểu tượng **Cài đặt (Bánh răng)** ở góc trên bên phải:
     - **Mã phòng / Mã bí mật**: Cả 2 máy nhập cùng 1 mã (mặc định: `live_couple_8888` hoặc bạn có thể đổi thành mã riêng của 2 bạn).
     - **Máy 1**: Chọn `Người A (Thiết bị 1)` và đặt tên (ví dụ: `Anh`).
     - **Máy 2**: Chọn `Người B (Thiết bị 2)` và đặt tên (ví dụ: `Em`).
     - Bấm **Lưu & Kết nối**.
4. **Bật chia sẻ**:
   - Bấm nút **"BẬT CHIA SẺ CHẠY NGẦM"** ở bảng điều khiển phía dưới.
   - Bây giờ 2 bạn có thể tắt màn hình hoặc đóng app, vị trí của 2 người sẽ luôn được đồng bộ mượt mà!
