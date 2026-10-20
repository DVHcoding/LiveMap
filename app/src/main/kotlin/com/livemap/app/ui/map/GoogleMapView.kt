package com.livemap.app.ui.map

import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.livemap.app.data.model.LocationData
import com.livemap.app.utils.LocationShareUtils
import com.livemap.app.utils.OrientationSensorHelper
import kotlinx.coroutines.launch

@Composable
fun GoogleMapView(
    myLocation: LocationData?,
    partnerLocation: LocationData?,
    myName: String,
    partnerName: String,
    mapViewController: MapViewController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val orientationHelper = remember { OrientationSensorHelper(context) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(myLocation?.latitude ?: 21.0285, myLocation?.longitude ?: 105.8542),
            if (myLocation != null) 17.5f else 16.5f
        )
    }

    var hasCenteredOnFirstLocation by remember { mutableStateOf(false) }

    // Quản lý vị trí & góc quay của Tôi
    var myCurrentLatLng by remember {
        mutableStateOf(LatLng(myLocation?.latitude ?: 21.0285, myLocation?.longitude ?: 105.8542))
    }
    var myMarkerRotation by remember { mutableFloatStateOf(myLocation?.bearing ?: 0f) }
    val myMarkerState = rememberMarkerState(position = myCurrentLatLng)

    // Quản lý vị trí & góc quay của Đối phương
    var partnerCurrentLatLng by remember {
        mutableStateOf<LatLng?>(partnerLocation?.let { LatLng(it.latitude, it.longitude) })
    }
    var partnerMarkerRotation by remember { mutableFloatStateOf(partnerLocation?.bearing ?: 0f) }
    val partnerMarkerState = rememberMarkerState(position = partnerCurrentLatLng ?: LatLng(0.0, 0.0))

    var isMapLoaded by remember { mutableStateOf(false) }

    // Icon đèn pin chuẩn Google Maps (lấy an toàn khi map đã tải)
    val myIconDescriptor = remember(isMapLoaded) { MarkerIconFactory.getBitmapDescriptor(context, isPartner = false) }
    val partnerIconDescriptor = remember(isMapLoaded) { MarkerIconFactory.getBitmapDescriptor(context, isPartner = true) }

    // Đăng ký vòng đời cảm biến la bàn
    DisposableEffect(lifecycleOwner) {
        orientationHelper.startListening()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> orientationHelper.startListening()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_DESTROY -> orientationHelper.stopListening()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            orientationHelper.stopListening()
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewController.onCenterOnLocation = null
            mapViewController.onResetNorth = null
            mapViewController.onFitBothLocations = null
        }
    }

    // Gắn các lệnh điều khiển từ nút bấm ngoài giao diện
    LaunchedEffect(mapViewController) {
        mapViewController.onCenterOnLocation = { lat, lng, zoom ->
            coroutineScope.launch {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), zoom.toFloat()))
            }
        }
        mapViewController.onResetNorth = {
            coroutineScope.launch {
                val current = cameraPositionState.position
                cameraPositionState.animate(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.builder(current).bearing(0f).build()
                    )
                )
            }
        }
        mapViewController.onFitBothLocations = { myLoc, partnerLoc ->
            if (myLoc != null && partnerLoc != null) {
                val builder = LatLngBounds.builder()
                builder.include(LatLng(myLoc.latitude, myLoc.longitude))
                builder.include(LatLng(partnerLoc.latitude, partnerLoc.longitude))
                coroutineScope.launch {
                    try {
                        cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(builder.build(), 120))
                    } catch (_: Exception) {
                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(myLoc.latitude, myLoc.longitude), 16f))
                    }
                }
            } else if (partnerLoc != null) {
                coroutineScope.launch {
                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(partnerLoc.latitude, partnerLoc.longitude), 16.5f))
                }
            } else if (myLoc != null) {
                coroutineScope.launch {
                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(myLoc.latitude, myLoc.longitude), 16.5f))
                }
            }
        }
    }

    // Tự động focus lần đầu khi có tọa độ
    LaunchedEffect(myLocation) {
        if (myLocation != null && !hasCenteredOnFirstLocation) {
            hasCenteredOnFirstLocation = true
            cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(LatLng(myLocation.latitude, myLocation.longitude), 17f))
        }
    }

    // Cập nhật mượt mà tọa độ của Tôi (Smooth interpolation)
    val lastMyTime = remember { longArrayOf(0L) }
    var myAnimator by remember { mutableStateOf<ValueAnimator?>(null) }
    LaunchedEffect(myLocation) {
        val loc = myLocation ?: return@LaunchedEffect
        val target = LatLng(loc.latitude, loc.longitude)
        val start = myCurrentLatLng
        val now = System.currentTimeMillis()
        val duration = if (lastMyTime[0] > 0L) (now - lastMyTime[0]).coerceIn(200L, 2500L) else 1000L
        lastMyTime[0] = now

        val isMoving = loc.speed >= 1.0f && loc.bearing != 0f
        if (isMoving) {
            myMarkerRotation = loc.bearing
        }

        val deltaLat = kotlin.math.abs(target.latitude - start.latitude)
        val deltaLng = kotlin.math.abs(target.longitude - start.longitude)
        if (deltaLat > 0.005 || deltaLng > 0.005) {
            myAnimator?.cancel()
            myCurrentLatLng = target
            myMarkerState.position = target
            return@LaunchedEffect
        }

        myAnimator?.cancel()
        myAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val fraction = anim.animatedValue as Float
                val lat = start.latitude + (target.latitude - start.latitude) * fraction
                val lng = start.longitude + (target.longitude - start.longitude) * fraction
                val interpolated = LatLng(lat, lng)
                myCurrentLatLng = interpolated
                myMarkerState.position = interpolated
            }
            start()
        }
    }

    // La bàn xoay đèn pin của Tôi khi đứng yên
    val currentMyLocation by rememberUpdatedState(myLocation)
    LaunchedEffect(Unit) {
        orientationHelper.azimuthFlow.collect { azimuth ->
            val loc = currentMyLocation
            val isMoving = loc != null && loc.speed >= 1.0f && loc.bearing != 0f
            if (!isMoving) {
                myMarkerRotation = azimuth
            }
        }
    }

    // Cập nhật mượt mà tọa độ Đối phương (Smooth interpolation)
    val lastPartnerTime = remember { longArrayOf(0L) }
    var partnerAnimator by remember { mutableStateOf<ValueAnimator?>(null) }
    LaunchedEffect(partnerLocation) {
        if (partnerLocation == null) {
            partnerAnimator?.cancel()
            partnerCurrentLatLng = null
            return@LaunchedEffect
        }
        val loc = partnerLocation
        val target = LatLng(loc.latitude, loc.longitude)
        val start = partnerCurrentLatLng ?: target
        val now = System.currentTimeMillis()
        val duration = if (lastPartnerTime[0] > 0L) (now - lastPartnerTime[0]).coerceIn(200L, 2500L) else 1000L
        lastPartnerTime[0] = now

        val isMoving = loc.speed >= 0.5f && loc.bearing != 0f
        if (isMoving) {
            partnerMarkerRotation = loc.bearing
        }

        val partnerDeltaLat = kotlin.math.abs(target.latitude - start.latitude)
        val partnerDeltaLng = kotlin.math.abs(target.longitude - start.longitude)
        if (partnerDeltaLat > 0.005 || partnerDeltaLng > 0.005) {
            partnerAnimator?.cancel()
            partnerCurrentLatLng = target
            partnerMarkerState.position = target
            return@LaunchedEffect
        }

        partnerAnimator?.cancel()
        partnerAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val fraction = anim.animatedValue as Float
                val lat = start.latitude + (target.latitude - start.latitude) * fraction
                val lng = start.longitude + (target.longitude - start.longitude) * fraction
                val interpolated = LatLng(lat, lng)
                partnerCurrentLatLng = interpolated
                partnerMarkerState.position = interpolated
            }
            start()
        }
    }

    val uiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            compassEnabled = false,
            myLocationButtonEnabled = false,
            rotationGesturesEnabled = true,
            scrollGesturesEnabled = true,
            tiltGesturesEnabled = true,
            zoomGesturesEnabled = true
        )
    }
    val properties = remember {
        MapProperties(
            isBuildingEnabled = true,
            isIndoorEnabled = true
        )
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = properties,
        uiSettings = uiSettings,
        onMapLoaded = {
            isMapLoaded = true
        }
    ) {
        // Marker của Tôi
        Marker(
            state = myMarkerState,
            title = myName,
            icon = myIconDescriptor,
            anchor = Offset(0.5f, 0.5f),
            flat = true,
            rotation = myMarkerRotation,
            onClick = {
                val pos = myMarkerState.position
                LocationShareUtils.copyCoordinates(context, pos.latitude, pos.longitude, myName)
                false
            }
        )

        // Marker của Đối phương (nếu có vị trí)
        if (partnerCurrentLatLng != null) {
            Marker(
                state = partnerMarkerState,
                title = "$partnerName (${String.format("%.1f", (partnerLocation?.speed ?: 0f) * 3.6f)} km/h)",
                icon = partnerIconDescriptor,
                anchor = Offset(0.5f, 0.5f),
                flat = true,
                rotation = partnerMarkerRotation,
                onClick = {
                    val pos = partnerMarkerState.position
                    LocationShareUtils.copyCoordinates(context, pos.latitude, pos.longitude, partnerName)
                    false
                }
            )
        }
    }
}
