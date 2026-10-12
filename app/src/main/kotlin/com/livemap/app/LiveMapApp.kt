package com.livemap.app

import android.app.Application
import org.osmdroid.config.Configuration

class LiveMapApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Cấu hình OsmDroid để tải tile bản đồ mượt mà và lưu cache
        val prefs = getSharedPreferences("${packageName}_osm", MODE_PRIVATE)
        Configuration.getInstance().load(this, prefs)
        Configuration.getInstance().userAgentValue = packageName
    }
}
