package com.livemap.app.ui.map

import android.view.MotionEvent
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Overlay xử lý cử chỉ xoay bản đồ 2 ngón tay siêu mượt (60fps/120fps),
 * áp dụng cơ chế lọc nhiễu và chống rung chuẩn Google Maps:
 * 1. Khử rung tay (Anti-jitter Deadband): Bỏ qua rung động vi mô của ngón tay dưới 4.5 độ khi chỉ giữ 2 ngón hoặc zoom.
 * 2. Lọc thông thấp (Low-pass EMA Filter): Chống giật cục, triệt tiêu rung giật 120Hz của cảm ứng điện dung.
 * 3. Khoảng cách an toàn ngón tay (Min finger distance): Không xoay khi 2 ngón tay quá sát nhau (< 100px).
 * 4. Zero Layout Pass: setMapOrientation(angle, doLayout = false) không lock UI thread.
 */
class SmoothRotationGestureOverlay(
    private val mapView: MapView,
    private val onRotateListener: ((Float) -> Unit)? = null
) : Overlay() {

    companion object {
        // Ngưỡng góc kích hoạt xoay: phải cố ý xoay trên 4.5 độ mới kích hoạt xoay
        // (chống rung hoàn toàn khi người dùng chỉ giữ 2 ngón tay hoặc zoom in/out)
        private const val ROTATION_ACTIVATION_SLOP_DEG = 4.5f

        // Khoảng cách tối thiểu giữa 2 ngón tay để tính toán góc chính xác
        private const val MIN_FINGER_DISTANCE_PX = 100f

        // Ngưỡng thay đổi tối thiểu giữa các frame để tránh vẽ lại không cần thiết
        private const val MIN_CHANGE_DEADBAND_DEG = 0.25f

        // Hệ số làm mượt Exponential Moving Average (alpha 0.7 = bám ngón tay tức thì và triệt tiêu rung)
        private const val SMOOTHING_FACTOR = 0.7f
    }

    private var pointerId1 = -1
    private var pointerId2 = -1
    private var initialTouchAngle = 0f
    private var initialMapOrientation = 0f
    private var lastAppliedOrientation = 0f
    private var isRotating = false

    override fun onTouchEvent(event: MotionEvent, mapView: MapView): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    val p1 = event.getPointerId(0)
                    val p2 = event.getPointerId(1)
                    val touchData = calculateTouchData(event, p1, p2)
                    if (touchData != null && touchData.distance >= MIN_FINGER_DISTANCE_PX) {
                        pointerId1 = p1
                        pointerId2 = p2
                        initialTouchAngle = touchData.angle
                        initialMapOrientation = mapView.mapOrientation
                        lastAppliedOrientation = initialMapOrientation
                        isRotating = false // Chưa kích hoạt cho đến khi vượt qua slop
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (pointerId1 != -1 && pointerId2 != -1 && event.pointerCount >= 2) {
                    val touchData = calculateTouchData(event, pointerId1, pointerId2)
                    if (touchData != null && touchData.distance >= MIN_FINGER_DISTANCE_PX) {
                        var totalDelta = touchData.angle - initialTouchAngle
                        while (totalDelta > 180f) totalDelta -= 360f
                        while (totalDelta < -180f) totalDelta += 360f

                        // 1. Kiểm tra ngưỡng kích hoạt xoay (Deadband)
                        if (!isRotating) {
                            if (abs(totalDelta) >= ROTATION_ACTIVATION_SLOP_DEG) {
                                isRotating = true
                                // Neo lại góc chạm ngay tại điểm kích hoạt để không bị giật nấc đầu tiên
                                initialTouchAngle = touchData.angle
                                initialMapOrientation = mapView.mapOrientation
                                lastAppliedOrientation = initialMapOrientation
                            } else {
                                // Người dùng chỉ đang giữ 2 ngón tay hoặc zoom -> TUYỆT ĐỐI KHÔNG XOAY MAP
                                return false
                            }
                        }

                        // 2. Khi đang trong chế độ xoay thực sự
                        if (isRotating) {
                            var delta = touchData.angle - initialTouchAngle
                            while (delta > 180f) delta -= 360f
                            while (delta < -180f) delta += 360f

                            val targetOrientation = (initialMapOrientation + delta + 360f) % 360f
                            var change = targetOrientation - lastAppliedOrientation
                            while (change > 180f) change -= 360f
                            while (change < -180f) change += 360f

                            // Khử rung vi mô giữa các frame di chuyển
                            if (abs(change) < MIN_CHANGE_DEADBAND_DEG) return false

                            // Áp dụng bộ lọc EMA làm mượt
                            val smoothedOrientation = (lastAppliedOrientation + change * SMOOTHING_FACTOR + 360f) % 360f
                            lastAppliedOrientation = smoothedOrientation

                            // doLayout = false để tránh requestLayout() làm sụt giảm FPS của Jetpack Compose
                            mapView.setMapOrientation(smoothedOrientation, false)
                            mapView.postInvalidateOnAnimation()
                            onRotateListener?.invoke(smoothedOrientation)
                        }
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val actionIndex = event.actionIndex
                val releasedId = event.getPointerId(actionIndex)
                if (releasedId == pointerId1 || releasedId == pointerId2) {
                    resetState()
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                resetState()
            }
        }
        return false
    }

    private fun resetState() {
        isRotating = false
        pointerId1 = -1
        pointerId2 = -1
    }

    private data class TouchData(val angle: Float, val distance: Float)

    private fun calculateTouchData(event: MotionEvent, id1: Int, id2: Int): TouchData? {
        val idx1 = event.findPointerIndex(id1)
        val idx2 = event.findPointerIndex(id2)
        if (idx1 < 0 || idx2 < 0) return null
        val dx = event.getX(idx1) - event.getX(idx2)
        val dy = event.getY(idx1) - event.getY(idx2)
        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        return TouchData(angle, dist)
    }
}
