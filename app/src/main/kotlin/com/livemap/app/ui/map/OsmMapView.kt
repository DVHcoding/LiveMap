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
import com.livemap.app.utils.OrientationSensorHelper
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MapViewController(
    private var mapView: MapView? = null
) {
    fun attach(view: MapView) {
        this.mapView = view
    }

    fun detach() {
        this.mapView = null
    }

    fun centerOnLocation(lat: Double, lng: Double, zoom: Double = 17.5) {
        mapView?.controller?.apply {
            setZoom(zoom)
            animateTo(GeoPoint(lat, lng))
        }
    }

    fun resetNorth() {
        mapView?.let { v ->
            v.mapOrientation = 0f
            v.invalidate()
        }
    }

    fun fitBothLocations(myLoc: LocationData?, partnerLoc: LocationData?) {
        val view = mapView ?: return
        if (myLoc != null && partnerLoc != null) {
            val north = maxOf(myLoc.latitude, partnerLoc.latitude)
            val south = minOf(myLoc.latitude, partnerLoc.latitude)
            val east = maxOf(myLoc.longitude, partnerLoc.longitude)
            val west = minOf(myLoc.longitude, partnerLoc.longitude)

            val box = BoundingBox(
                north + 0.005,
                east + 0.005,
                south - 0.005,
                west - 0.005
            )
            view.zoomToBoundingBox(box, true, 120)
        } else if (partnerLoc != null) {
            centerOnLocation(partnerLoc.latitude, partnerLoc.longitude)
        } else if (myLoc != null) {
            centerOnLocation(myLoc.latitude, myLoc.longitude)
        }
    }
}

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
    DisposableEffect(lifecycleOwner, mapHolder) {
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
                    mapViewController.detach()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            orientationHelper.stopListening()
            lifecycleOwner.lifecycle.removeObserver(observer)
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
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                minZoomLevel = 4.0
                maxZoomLevel = 20.0
                controller.setZoom(16.0)

                // Điểm khởi đầu mặc định
                val defaultPoint = GeoPoint(21.0285, 105.8542)
                controller.setCenter(defaultPoint)

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
            mapViewController.attach(mapView)
            mapView
        },
        update = { _ -> }
    )

    val lastPartnerTime = remember { longArrayOf(0L) }
    LaunchedEffect(partnerLocation, mapHolder) {
        val loc = partnerLocation ?: return@LaunchedEffect
        val holder = mapHolder ?: return@LaunchedEffect
        val now = System.currentTimeMillis()
        val duration = if (lastPartnerTime[0] > 0L) {
            (now - lastPartnerTime[0]).coerceIn(200L, 2500L)
        } else 1000L
        lastPartnerTime[0] = now

        holder.partnerMarker.title = "$partnerName (${String.format("%.1f", loc.speed * 3.6f)} km/h)"
        holder.partnerSmoothHelper.animateMarkerTo(
            holder.partnerMarker,
            GeoPoint(loc.latitude, loc.longitude),
            loc.bearing,
            durationMs = duration
        )
    }

    val lastMyTime = remember { longArrayOf(0L) }
    LaunchedEffect(myLocation, mapHolder) {
        val loc = myLocation ?: return@LaunchedEffect
        val holder = mapHolder ?: return@LaunchedEffect
        val now = System.currentTimeMillis()
        val duration = if (lastMyTime[0] > 0L) {
            (now - lastMyTime[0]).coerceIn(200L, 2500L)
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

    // Tự động focus vào vị trí của mình khi vừa nhận được tọa độ lần đầu
    LaunchedEffect(myLocation) {
        if (myLocation != null && !hasCenteredOnFirstLocation) {
            hasCenteredOnFirstLocation = true
            mapViewController.centerOnLocation(myLocation.latitude, myLocation.longitude, zoom = 17.5)
        }
    }
}
