package com.example.locationbasednotifications

import android.Manifest
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.locationbasednotifications.LocationDto.LocationSample
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchNearbyRequest
import javax.inject.Inject

private val TRANSIT_TYPES = setOf(
    "airport",
    "bus_station",
    "bus_stop",
    "international_airport",
    "subway_station",
    "train_station",
    "light_rail_station",
    "transit_station"
)


object PlacesInitializer {

    fun init(context: Context): PlacesClient? {
        val apiKey = BuildConfig.PLACES_API_KEY

        if (apiKey.isEmpty() || apiKey == "DEFAULT_API_KEY") {
            Log.e("Places", "API key missing")
            return null
        }

        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(context, apiKey)
        }

        return Places.createClient(context)
    }
}

// Define a variable to hold the Places API key.
/*val apiKey = BuildConfig.PLACES_API_KEY

// Log an error if apiKey is not set.
if (apiKey.isEmpty() || apiKey == "DEFAULT_API_KEY") {
    Log.e("Places test", "No api key")
    finish()
    return
}

// Initialize the SDK
Places.initializeWithNewPlacesApiEnabled(applicationContext, apiKey)

// Create a new PlacesClient instance
val placesClient = Places.createClient(this)


// Define a list of fields to include in the response for each returned place.
final List<Place.Field> placeFields = Arrays.asList(Place.Field.ID, Place.Field.DISPLAY_NAME, Place.Field.TYPES);

// Define the search area as a 50 meter diameter circle in Banglore, IND.
LatLng center = new LatLng(13.198474, 77.708178);
CircularBounds circle = CircularBounds.newInstance(center, /* radius = */ 50);

// Define a list of types to include.
final List<String> includedTypes = Arrays.asList(TRANSIT_TYPES.toArray(new String[0]));

// Use the builder to create a SearchNearbyRequest object.
final SearchNearbyRequest searchNearbyRequest =
SearchNearbyRequest.builder(/* location restriction = */ circle, placeFields)
.setIncludedTypes(includedTypes)
.setExcludedTypes(excludedTypes)
.setMaxResultCount(10)
.build());

// Call placesClient.searchNearby() to perform the search.
// Define a response handler to process the returned List of Place objects.
placesClient.searchNearby(searchNearbyRequest)
.addOnSuccessListener(response -> {
    List<Place> places = response.getPlaces();
});*/


/*private val locationRequest = LocationRequest.Builder(
    Priority.PRIORITY_HIGH_ACCURACY,
    10 * 60 * 1000L, // 10 minutes
)
    .setMinUpdateIntervalMillis(5 * 60 * 1000L) // 5 minutes (best effort)
    .setWaitForAccurateLocation(true)
    .build()*/


class LocationSamplingManager @Inject constructor(
    private val context: Context,
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
) {
    private var hasCheckedTransitForThisStop = false
    private val placesClient: PlacesClient? =
        PlacesInitializer.init(context)

    private val locationSamples = mutableListOf<LocationSample>()

    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        1 * 60 * 1000L
    )
        .setMinUpdateIntervalMillis(5 * 60 * 1000L)
        .setWaitForAccurateLocation(true)
        .build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            Log.d("LocationDebug", "Location received: ${location.latitude}, ${location.longitude}")

            locationSamples.add(
                LocationSample(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    timestamp = System.currentTimeMillis()
                )
            )

            if (locationSamples.size > 2) {
                locationSamples.removeAt(0)
            }

            val stationary = isUserStationary(locationSamples)
            if (stationary && !hasCheckedTransitForThisStop) {
                hasCheckedTransitForThisStop = true
                searchNearbyTransit(location)
            }

            if (!stationary) {
                // User moved again → reset for next stop
                hasCheckedTransitForThisStop = false
            }

            Log.d(
                "TransitDebug",
                "stationary=$stationary, checked=$hasCheckedTransitForThisStop"
            )
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun startLocationSampling() {
        Log.d("LocationDebug", "startLocationSampling called")
        fusedClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    fun stopLocationSampling() {
        fusedClient.removeLocationUpdates(locationCallback)
    }

    private fun searchNearbyTransit(location: Location) {
        val client = placesClient ?: return

        val placeFields = listOf(
            Place.Field.ID,
            Place.Field.DISPLAY_NAME,
            Place.Field.TYPES
        )

        val center = LatLng(location.latitude, location.longitude)
        val bounds = CircularBounds.newInstance(center, 50.0)

        val request = SearchNearbyRequest.builder(bounds, placeFields)
            .setIncludedTypes(TRANSIT_TYPES.toList())
            .setMaxResultCount(10)
            .build()

        client.searchNearby(request)
            .addOnSuccessListener { response ->

                Log.d("PlacesDebug", "Total places found: ${response.places.size}")

                response.places.forEachIndexed { index, place ->
                    Log.d(
                        "PlacesDebug",
                        """
                Place #$index
                id = ${place.id}
                name = ${place.displayName}
                types = ${place.placeTypes}
                """.trimIndent()
                    )
                }

                val transitPlace = response.places.firstOrNull{ place ->
                    place.placeTypes?.any { it in TRANSIT_TYPES } == true &&
                            !place.displayName.isNullOrBlank()
                }

                if (transitPlace != null) {
                    val placeName = transitPlace.displayName
                    Log.d("TransitCheck", "You are at $placeName")
                } else {
                    Log.d("TransitCheck", "No nearby transit location found")
                }
            }
            .addOnFailureListener {
                Log.e("TransitCheck", "Places search failed", it)
            }
    }
}


/*class LocationSamplingManager @Inject constructor(
    private val context: Context,
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
) {

    private val _locationSamples = mutableListOf<LocationSample>()
    val locationSamples: List<LocationSample>
        get() = _locationSamples

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return

            _locationSamples.add(
                LocationSample(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    timestamp = System.currentTimeMillis()
                )
            )

            if (_locationSamples.size > 2) {
                _locationSamples.removeAt(0)
            }


            if (isUserStationary(_locationSamples)) {
                val isTransit = isTransitLocation(placesResponse)
            }
        }
    }*/

    /*@RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun startLocationSampling() {
        fusedClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    fun stopLocationSampling() {
        fusedClient.removeLocationUpdates(locationCallback)
    }
}*/

fun isUserStationary(
    samples: List<LocationSample>,
    maxDistanceMeters: Float = 100f,
    minDurationMillis: Long = 1 * 60 * 1000L
): Boolean {

    if (samples.size < 2) return false

    val previous = samples[samples.size - 2]
    val current = samples.last()

    val distance = FloatArray(1)
    Location.distanceBetween(
        previous.latitude,
        previous.longitude,
        current.latitude,
        current.longitude,
        distance
    )

    val timeDiff = current.timestamp - previous.timestamp

    return distance[0] <= maxDistanceMeters &&
            timeDiff >= minDurationMillis
}

/*fun isTransitLocation(placesResponse: PlacesNearbyResponse): Boolean {
    return placesResponse.results.any { place ->
        place.types.any { type ->
            type in TRANSIT_TYPES
        }
    }
}*/