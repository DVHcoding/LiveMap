package com.livemap.app.ui.map

import android.animation.ValueAnimator
import android.location.Location
import android.view.animation.LinearInterpolator
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class SmoothMarkerHelper(private val mapView: MapView) {

    private var currentAnimator: ValueAnimator? = null
    private var isInitialized: Boolean = false
    private var lastTargetLat: Double = 0.0
    private var lastTargetLng: Double = 0.0
    private var lastTargetBearing: Float = 0f

    fun animateMarkerTo(
        marker: Marker,
        targetGeoPoint: GeoPoint,
        targetBearing: Float,
        durationMs: Long = 1000L,
        animateRotation: Boolean = true
    ) {
        val currentPos = marker.position
        val isZeroCoord = currentPos == null || (currentPos.latitude == 0.0 && currentPos.longitude == 0.0)

        // Lần đầu tiên: đặt thẳng vào vị trí, không nội suy
        if (!isInitialized || isZeroCoord) {
            isInitialized = true
            currentAnimator?.cancel()
            currentAnimator = null
            lastTargetLat = targetGeoPoint.latitude
            lastTargetLng = targetGeoPoint.longitude
            lastTargetBearing = targetBearing
            marker.position = targetGeoPoint
            if (animateRotation) {
                marker.rotation = -targetBearing
            }
            mapView.postInvalidateOnAnimation()
            return
        }

        // Chống ngắt quãng animator nếu nhận tọa độ trùng lặp khi màn hình recompose
        if (currentAnimator?.isRunning == true &&
            kotlin.math.abs(targetGeoPoint.latitude - lastTargetLat) < 0.000001 &&
            kotlin.math.abs(targetGeoPoint.longitude - lastTargetLng) < 0.000001 &&
            (!animateRotation || kotlin.math.abs(targetBearing - lastTargetBearing) < 0.2f)
        ) {
            return
        }

        // Điểm xuất phát là chính xác tọa độ hiện tại của marker trên màn hình
        val startLat = marker.position.latitude
        val startLng = marker.position.longitude

        // Nếu khoảng cách nhảy vọt quá xa (> 70m): đặt thẳng vị trí (snap) thay vì phóng vụt qua màn hình
        val distanceResults = FloatArray(1)
        Location.distanceBetween(
            startLat, startLng,
            targetGeoPoint.latitude, targetGeoPoint.longitude,
            distanceResults
        )
        val distanceMeters = distanceResults[0]

        if (distanceMeters > 70f) {
            currentAnimator?.cancel()
            currentAnimator = null
            lastTargetLat = targetGeoPoint.latitude
            lastTargetLng = targetGeoPoint.longitude
            lastTargetBearing = targetBearing
            marker.position = targetGeoPoint
            if (animateRotation) {
                marker.rotation = -targetBearing
            }
            mapView.postInvalidateOnAnimation()
            return
        }

        lastTargetLat = targetGeoPoint.latitude
        lastTargetLng = targetGeoPoint.longitude
        lastTargetBearing = targetBearing

        // Góc quay hiện tại của marker
        val startBearing = -marker.rotation
        var bearingDiff = (targetBearing - startBearing) % 360f
        if (bearingDiff > 180f) bearingDiff -= 360f
        if (bearingDiff < -180f) bearingDiff += 360f

        currentAnimator?.cancel()

        // Thêm buffer 350ms để triệt tiêu hiện tượng khựng trước khi gói tin tiếp theo tới;
        // Marker luôn duy trì chuyển động liên tục, không bao giờ dừng sớm chờ mạng
        val effectiveDuration = (durationMs + 350L).coerceIn(1000L, 3000L)

        currentAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = effectiveDuration
            interpolator = LinearInterpolator()

            addUpdateListener { anim ->
                val fraction = anim.animatedValue as Float
                val lat = startLat + (targetGeoPoint.latitude - startLat) * fraction
                val lng = startLng + (targetGeoPoint.longitude - startLng) * fraction

                marker.position = GeoPoint(lat, lng)
                if (animateRotation) {
                    val currentRot = startBearing + bearingDiff * fraction
                    marker.rotation = -currentRot // OsmDroid quay theo chiều kim đồng hồ
                }
                mapView.postInvalidateOnAnimation()
            }

            start()
        }
    }

    fun cancel() {
        currentAnimator?.cancel()
        currentAnimator = null
    }
}
