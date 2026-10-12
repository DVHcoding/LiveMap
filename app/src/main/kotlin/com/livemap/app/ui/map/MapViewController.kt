package com.livemap.app.ui.map

import com.livemap.app.data.model.LocationData

class MapViewController {
    var onCenterOnLocation: ((lat: Double, lng: Double, zoom: Double) -> Unit)? = null
    var onResetNorth: (() -> Unit)? = null
    var onFitBothLocations: ((myLoc: LocationData?, partnerLoc: LocationData?) -> Unit)? = null

    fun centerOnLocation(lat: Double, lng: Double, zoom: Double = 17.5) {
        onCenterOnLocation?.invoke(lat, lng, zoom)
    }

    fun resetNorth() {
        onResetNorth?.invoke()
    }

    fun fitBothLocations(myLoc: LocationData?, partnerLoc: LocationData?) {
        onFitBothLocations?.invoke(myLoc, partnerLoc)
    }
}
