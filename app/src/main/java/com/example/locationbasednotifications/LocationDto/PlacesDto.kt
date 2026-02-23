package com.example.locationbasednotifications.LocationDto

data class PlacesNearbyResponse(
    val results: List<PlaceResult>
)

data class PlaceResult(
    val types: List<String>
)