package com.livemap.app

import android.app.Application
import org.osmdroid.config.Configuration
import java.io.File

class LiveMapApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 1. Chỉ định thư mục cache nội bộ trong app (Internal App Storage)
        // Bắt buộc trên Android 10+ (Android 11, 12, 13, 14) để vượt qua Scoped Storage
        // Nếu không set, OsmDroid sẽ cố ghi vào thẻ nhớ ngoài /sdcard/osmdroid và bị hệ thống chặn gây xám/trắng map
        val osmBaseDir = File(cacheDir, "osmdroid")
        val osmTilesDir = File(osmBaseDir, "tiles")
        if (!osmTilesDir.exists()) {
            osmTilesDir.mkdirs()
        }

        val prefs = getSharedPreferences("${packageName}_osm", MODE_PRIVATE)
        Configuration.getInstance().apply {
            load(this@LiveMapApp, prefs)
            // User-Agent chuẩn chỉ định danh để máy chủ OpenStreetMap không chặn HTTP 403
            userAgentValue = "LiveMapApp/1.0.0 (Android; com.livemap.app; OpenStreetMap Tile Client)"
            osmdroidBasePath = osmBaseDir
            osmdroidTileCache = osmTilesDir
        }
    }
}
