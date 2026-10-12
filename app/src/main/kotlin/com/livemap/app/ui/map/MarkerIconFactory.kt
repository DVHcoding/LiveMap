package com.livemap.app.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

object MarkerIconFactory {

    private var myMarkerDrawable: Drawable? = null
    private var partnerMarkerDrawable: Drawable? = null

    fun getMarker(
        context: Context,
        isPartner: Boolean
    ): Drawable {
        if (isPartner && partnerMarkerDrawable != null) {
            return partnerMarkerDrawable!!
        }
        if (!isPartner && myMarkerDrawable != null) {
            return myMarkerDrawable!!
        }

        val density = context.resources.displayMetrics.density
        // Kích thước canvas vuông để tâm điểm nằm chính xác ở giữa (cx, cy)
        val size = (120 * density).toInt()
        val cx = size / 2f
        val cy = size / 2f

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val mainColor = if (isPartner) Color.parseColor("#EC4899") else Color.parseColor("#2563EB")
        val r = Color.red(mainColor)
        val g = Color.green(mainColor)
        val b = Color.blue(mainColor)

        // 1. DIRECTION CONE (Tia rẽ quạt 60 độ tỏa sáng như đèn pin về phía trước / 12 giờ)
        val beamRadius = 52 * density
        val conePath = Path().apply {
            moveTo(cx, cy)
            arcTo(
                RectF(cx - beamRadius, cy - beamRadius, cx + beamRadius, cy + beamRadius),
                -120f,
                60f,
                false
            )
            close()
        }

        val conePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = RadialGradient(
                cx, cy, beamRadius,
                intArrayOf(
                    Color.argb(180, r, g, b),
                    Color.argb(70, r, g, b),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0.1f, 0.75f, 1.0f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(conePath, conePaint)

        // 2. VÒNG SÁNG NGOÀI (PULSE GLOW RING) bao quanh chấm tròn
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(45, r, g, b)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, 18 * density, glowPaint)

        // 3. VIỀN TRẮNG NỔI BẬT CHUẨN GOOGLE MAPS
        val whiteBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            setShadowLayer(4 * density, 0f, 2 * density, Color.argb(120, 0, 0, 0))
        }
        canvas.drawCircle(cx, cy, 13 * density, whiteBorderPaint)

        // 4. CHẤM TRÒN CHÍNH (CORE DOT - Chuẩn Google Maps, hoàn hảo mọi góc quay)
        val coreDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mainColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, 10 * density, coreDotPaint)

        val drawable = BitmapDrawable(context.resources, bitmap)
        if (isPartner) {
            partnerMarkerDrawable = drawable
        } else {
            myMarkerDrawable = drawable
        }
        return drawable
    }
}
