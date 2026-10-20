package com.livemap.app.ui.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.livemap.app.data.model.LocationData
import com.livemap.app.utils.LocationShareUtils
import com.livemap.app.utils.OrientationSensorHelper
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

private val OSM_FRANCE = XYTileSource(
    "OsmFrance",
    0, 20, 256, ".png",
    arrayOf(
        "https://a.tile.openstreetmap.fr/osmfr/",
        "https://b.tile.openstreetmap.fr/osmfr/",
        "https://c.tile.openstreetmap.fr/osmfr/"
    ),
    "© OpenStreetMap contributors"
)



private class OsmMapHolder(
    val mapView: MapView,
    val myMarker: Marker,
    val partnerMarker: Marker,
    val mySmoothHelper: SmoothMarkerHelper,
    val partnerSmoothHelper: SmoothMarkerHelper
)

@Composable
fun OsmMapView(
    myLocation: LocationData?,
    partnerLocation: LocationData?,
    myName: String,
    partnerName: String,
    mapViewController: MapViewController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val orientationHelper = remember { OrientationSensorHelper(context) }

    var mapHolder by remember { mutableStateOf<OsmMapHolder?>(null) }
    var hasCenteredOnFirstLocation by remember { mutableStateOf(false) }

    // Xử lý vòng đời MapView và Cảm biến la bàn hướng quay
    DisposableEffect(lifecycleOwner) {
        orientationHelper.startListening()
        mapHolder?.mapView?.onResume()

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mapHolder?.mapView?.onResume()
                    orientationHelper.startListening()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    mapHolder?.mapView?.onPause()
                    orientationHelper.stopListening()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    mapHolder?.mySmoothHelper?.cancel()
                    mapHolder?.partnerSmoothHelper?.cancel()
                    mapHolder?.mapView?.onDetach()
                    orientationHelper.stopListening()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            mapHolder?.mySmoothHelper?.cancel()
            mapHolder?.partnerSmoothHelper?.cancel()
            orientationHelper.stopListening()
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewController.onCenterOnLocation = null
            mapViewController.onResetNorth = null
            mapViewController.onFitBothLocations = null
        }
    }

    // Luôn gắn các lệnh điều khiển từ nút bấm ngoài giao diện (zoom, reset bắc, bao quát) khi mapHolder sẵn sàng
    LaunchedEffect(mapViewController, mapHolder) {
        val holder = mapHolder ?: return@LaunchedEffect
        val mapView = holder.mapView
        mapViewController.onCenterOnLocation = { lat, lng, zoom ->
            mapView.controller.setZoom(zoom)
            mapView.controller.animateTo(GeoPoint(lat, lng))
        }
        mapViewController.onResetNorth = {
            mapView.mapOrientation = 0f
            mapView.invalidate()
        }
        mapViewController.onFitBothLocations = { myLoc, partnerLoc ->
            if (myLoc != null && partnerLoc != null) {
                val north = maxOf(myLoc.latitude, partnerLoc.latitude)
                val south = minOf(myLoc.latitude, partnerLoc.latitude)
                val east = maxOf(myLoc.longitude, partnerLoc.longitude)
                val west = minOf(myLoc.longitude, partnerLoc.longitude)
                val box = BoundingBox(north + 0.005, east + 0.005, south - 0.005, west - 0.005)
                mapView.zoomToBoundingBox(box, true, 120)
            } else if (partnerLoc != null) {
                mapView.controller.animateTo(GeoPoint(partnerLoc.latitude, partnerLoc.longitude))
            } else if (myLoc != null) {
                mapView.controller.animateTo(GeoPoint(myLoc.latitude, myLoc.longitude))
            }
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            // Cấu hình cache thư mục nội bộ chống lỗi Scoped Storage trên Android 9-14
            val osmBaseDir = java.io.File(ctx.cacheDir, "osmdroid")
            val osmTilesDir = java.io.File(osmBaseDir, "tiles")
            if (!osmTilesDir.exists()) osmTilesDir.mkdirs()

            org.osmdroid.config.Configuration.getInstance().apply {
                userAgentValue = "LiveMapApp/1.0.0 (Android; com.livemap.app)"
                osmdroidBasePath = osmBaseDir
                osmdroidTileCache = osmTilesDir
                cacheMapTileCount = 48
                tileDownloadThreads = 4
                tileFileSystemThreads = 4
            }

            val mapView = MapView(ctx).apply {
                setTileSource(OSM_FRANCE)
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                minZoomLevel = 4.0
                maxZoomLevel = 20.0

                // Điểm khởi đầu: ưu tiên vị trí thực tế của người dùng nếu đã có
                val initialPoint = if (myLocation != null) {
                    GeoPoint(myLocation.latitude, myLocation.longitude)
                } else {
                    GeoPoint(21.0285, 105.8542)
                }
                controller.setCenter(initialPoint)
                controller.setZoom(if (myLocation != null) 17.5 else 16.0)

                // Kích hoạt ngay thread tải tile bản đồ
                onResume()
            }

            // Marker tâm điểm ở giữa ANCHOR_CENTER để đèn pin xoay tròn chính xác quanh chấm tròn
            // isFlat = true để marker nằm phẳng trên bản đồ (chuẩn Google Maps Navigation),
            // tự động xoay cùng bản đồ mà không bị tính sai góc khi xoay 2 ngón tay.
            val myMarker = Marker(mapView).apply {
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = MarkerIconFactory.getMarker(ctx, isPartner = false)
                title = myName
                isFlat = true
                if (myLocation != null) {
                    position = GeoPoint(myLocation.latitude, myLocation.longitude)
                    if (myLocation.bearing != 0f) {
                        rotation = -myLocation.bearing
                    }
                }
                setOnMarkerClickListener { marker, _ ->
                    val pos = marker.position
                    LocationShareUtils.copyCoordinates(ctx, pos.latitude, pos.longitude, myName)
                    marker.showInfoWindow()
                    true
                }
            }

            // Kích hoạt xoay bản đồ 360 độ 2 ngón tay siêu mượt (Smooth 60fps/120fps, không lag)
            val rotationGestureOverlay = SmoothRotationGestureOverlay(mapView)
            mapView.overlays.add(rotationGestureOverlay)
            mapView.overlays.add(myMarker)

            val partnerMarker = Marker(mapView).apply {
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = MarkerIconFactory.getMarker(ctx, isPartner = true)
                title = partnerName
                isFlat = true
                setVisible(partnerLocation != null)
                if (partnerLocation != null) {
                    position = GeoPoint(partnerLocation.latitude, partnerLocation.longitude)
                    if (partnerLocation.bearing != 0f) {
                        rotation = -partnerLocation.bearing
                    }
                }
                setOnMarkerClickListener { marker, _ ->
                    val pos = marker.position
                    LocationShareUtils.copyCoordinates(ctx, pos.latitude, pos.longitude, partnerName)
                    marker.showInfoWindow()
                    true
                }
            }
            mapView.overlays.add(partnerMarker)

            val holder = OsmMapHolder(
                mapView = mapView,
                myMarker = myMarker,
                partnerMarker = partnerMarker,
                mySmoothHelper = SmoothMarkerHelper(mapView),
                partnerSmoothHelper = SmoothMarkerHelper(mapView)
            )

            mapHolder = holder
            mapView
        },
        update = { _ ->
            // Luôn đảm bảo marker có vị trí tọa độ đầy đủ khi view được tái sử dụng
            mapHolder?.let { holder ->
                if (myLocation != null && (holder.myMarker.position == null || holder.myMarker.position.latitude == 0.0)) {
                    holder.myMarker.position = GeoPoint(myLocation.latitude, myLocation.longitude)
                    holder.mapView.invalidate()
                }
                if (partnerLocation != null) {
                    holder.partnerMarker.setVisible(true)
                    if (holder.partnerMarker.position == null || holder.partnerMarker.position.latitude == 0.0) {
                        holder.partnerMarker.position = GeoPoint(partnerLocation.latitude, partnerLocation.longitude)
                    }
                    holder.mapView.invalidate()
                } else {
                    holder.partnerMarker.setVisible(false)
                    holder.mapView.invalidate()
                }
            }
        }
    )

    val lastPartnerTime = remember { longArrayOf(0L) }
    LaunchedEffect(partnerLocation, mapHolder) {
        val holder = mapHolder ?: return@LaunchedEffect
        if (partnerLocation == null) {
            holder.partnerSmoothHelper.cancel()
            holder.partnerMarker.setVisible(false)
            holder.mapView.invalidate()
            return@LaunchedEffect
        }
        holder.partnerMarker.setVisible(true)
        val loc = partnerLocation
        val now = System.currentTimeMillis()
        val duration = if (lastPartnerTime[0] > 0L) {
            (now - lastPartnerTime[0]).coerceIn(800L, 2500L)
        } else 1000L
        lastPartnerTime[0] = now

        val isPartnerMoving = loc.speed >= 0.5f && loc.bearing != 0f
        holder.partnerMarker.title = "$partnerName (${String.format("%.1f", loc.speed * 3.6f)} km/h)"
        holder.partnerSmoothHelper.animateMarkerTo(
            marker = holder.partnerMarker,
            targetGeoPoint = GeoPoint(loc.latitude, loc.longitude),
            targetBearing = loc.bearing,
            durationMs = duration,
            animateRotation = isPartnerMoving
        )
    }

    val lastMyTime = remember { longArrayOf(0L) }
    LaunchedEffect(myLocation, mapHolder) {
        val loc = myLocation ?: return@LaunchedEffect
        val holder = mapHolder ?: return@LaunchedEffect
        val now = System.currentTimeMillis()
        val duration = if (lastMyTime[0] > 0L) {
            (now - lastMyTime[0]).coerceIn(800L, 2500L)
        } else 1000L
        lastMyTime[0] = now

        val isMoving = loc.speed >= 1.0f && loc.bearing != 0f
        holder.myMarker.title = "$myName (${String.format("%.1f", loc.speed * 3.6f)} km/h)"
        holder.mySmoothHelper.animateMarkerTo(
            marker = holder.myMarker,
            targetGeoPoint = GeoPoint(loc.latitude, loc.longitude),
            targetBearing = loc.bearing,
            durationMs = duration,
            animateRotation = isMoving
        )
    }

    // Khi người dùng đứng yên hoặc xoay điện thoại: đèn pin Google Maps xoay theo la bàn mượt mà,
    // sử dụng rememberUpdatedState để coroutine chạy ổn định 1 lần duy nhất, không hủy/tạo lại coroutine liên tục.
    val currentMyLocation by rememberUpdatedState(myLocation)
    LaunchedEffect(mapHolder) {
        val holder = mapHolder ?: return@LaunchedEffect
        orientationHelper.azimuthFlow.collect { azimuth ->
            val loc = currentMyLocation
            val isMoving = loc != null && loc.speed >= 1.0f && loc.bearing != 0f
            if (!isMoving) {
                holder.myMarker.rotation = -azimuth
                holder.mapView.postInvalidateOnAnimation()
            }
        }
    }

    // Tự động focus vào vị trí của mình khi khởi tạo hoặc khi chuyển lại map
    LaunchedEffect(mapHolder, myLocation) {
        val holder = mapHolder ?: return@LaunchedEffect
        val loc = myLocation ?: return@LaunchedEffect
        if (!hasCenteredOnFirstLocation) {
            hasCenteredOnFirstLocation = true
            holder.mapView.controller.setZoom(17.5)
            holder.mapView.controller.animateTo(GeoPoint(loc.latitude, loc.longitude))
        }
    }
}
