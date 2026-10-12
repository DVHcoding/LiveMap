package com.livemap.app.ui.map

import android.view.MotionEvent
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.atan2

/**
 * Overlay xử lý cử chỉ xoay bản đồ 2 ngón tay siêu mượt (60fps/120fps),
 * khắc phục hoàn toàn nhược điểm của RotationGestureOverlay mặc định trong OsmDroid:
 * 1. Bug cộng dồn góc dồn dập (cumulative angle bug) làm bản đồ bị văng và giật.
 * 2. requestLayout() bị gọi trên từng frame gây re-measure toàn bộ Compose UI tree.
 * 3. Cố định chu kỳ 25ms không đồng bộ với tần số quét màn hình (Vsync).
 */
class SmoothRotationGestureOverlay(
    private val mapView: MapView,
    private val onRotateListener: ((Float) -> Unit)? = null
) : Overlay() {

    private var pointerId1 = -1
    private var pointerId2 = -1
    private var initialTouchAngle = 0f
    private var initialMapOrientation = 0f
    private var isRotating = false

    override fun onTouchEvent(event: MotionEvent, mapView: MapView): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    pointerId1 = event.getPointerId(0)
                    pointerId2 = event.getPointerId(1)
                    val angle = calculateAngle(event, pointerId1, pointerId2)
                    if (angle != null) {
                        initialTouchAngle = angle
                        initialMapOrientation = mapView.mapOrientation
                        isRotating = true
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isRotating && event.pointerCount >= 2) {
                    val currentTouchAngle = calculateAngle(event, pointerId1, pointerId2)
                    if (currentTouchAngle != null) {
                        var delta = currentTouchAngle - initialTouchAngle
                        // Chuẩn hóa góc chênh lệch trong khoảng [-180, 180]
                        while (delta > 180f) delta -= 360f
                        while (delta < -180f) delta += 360f

                        val targetOrientation = (initialMapOrientation + delta) % 360f
                        // doLayout = false để tránh requestLayout() làm sụt giảm FPS của Jetpack Compose
                        mapView.setMapOrientation(targetOrientation, false)
                        mapView.postInvalidateOnAnimation()
                        onRotateListener?.invoke(targetOrientation)
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val actionIndex = event.actionIndex
                val releasedId = event.getPointerId(actionIndex)
                if (releasedId == pointerId1 || releasedId == pointerId2) {
                    isRotating = false
                    pointerId1 = -1
                    pointerId2 = -1
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                isRotating = false
                pointerId1 = -1
                pointerId2 = -1
            }
        }
        // Trả về false để các cử chỉ khác (như pinch zoom hay pan) vẫn được xử lý mượt mà đồng thời
        return false
    }

    private fun calculateAngle(event: MotionEvent, id1: Int, id2: Int): Float? {
        val idx1 = event.findPointerIndex(id1)
        val idx2 = event.findPointerIndex(id2)
        if (idx1 < 0 || idx2 < 0) return null
        val dx = event.getX(idx1) - event.getX(idx2)
        val dy = event.getY(idx1) - event.getY(idx2)
        return Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    }
}
