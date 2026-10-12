package com.livemap.app.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

class SmoothInterpolationTest {

    // Thuật toán tính góc quay ngắn nhất giữa 2 hướng (tránh xoay 350 độ khi chỉ lệch 10 độ)
    private fun calculateShortestAngleDiff(startBearing: Float, targetBearing: Float): Float {
        var diff = (targetBearing - startBearing) % 360f
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f
        return diff
    }

    private fun interpolateCoordinate(start: Double, end: Double, fraction: Float): Double {
        return start + (end - start) * fraction
    }

    @Test
    fun testShortestAngleDifferenceWrapAround() {
        // Từ 350 độ sang 10 độ: góc quay ngắn nhất là +20 độ (quay theo chiều kim đồng hồ 20 độ)
        val diff1 = calculateShortestAngleDiff(350f, 10f)
        assertEquals(20f, diff1, 0.001f)

        // Từ 10 độ sang 350 độ: góc quay ngắn nhất là -20 độ (ngược chiều kim đồng hồ 20 độ)
        val diff2 = calculateShortestAngleDiff(10f, 350f)
        assertEquals(-20f, diff2, 0.001f)

        // Từ 0 độ sang 90 độ: +90 độ
        val diff3 = calculateShortestAngleDiff(0f, 90f)
        assertEquals(90f, diff3, 0.001f)
    }

    @Test
    fun testCoordinateInterpolationLinear() {
        val startLat = 21.000000
        val targetLat = 21.001000

        // Tại fraction = 0.5 (ở giữa quãng đường)
        val midLat = interpolateCoordinate(startLat, targetLat, 0.5f)
        assertEquals(21.000500, midLat, 0.000001)

        // Tại fraction = 1.0 (đến đích)
        val endLat = interpolateCoordinate(startLat, targetLat, 1.0f)
        assertEquals(targetLat, endLat, 0.000001)
    }
}
