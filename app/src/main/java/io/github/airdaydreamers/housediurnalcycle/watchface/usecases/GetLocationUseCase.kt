package io.github.airdaydreamers.housediurnalcycle.watchface.usecases

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GetLocationUseCase(
    private val context: Context,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    companion object {
        //Default location is BERLIN
        const val DEFAULT_LATITUDE = 52.520008
        const val DEFAULT_LONGITUDE = 13.404954
    }
    private val hasGps: Boolean by lazy { context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS) }
    private val hasNetworkLocation: Boolean by lazy {
        context.packageManager.hasSystemFeature(
            PackageManager.FEATURE_LOCATION_NETWORK
        )
    }
    //TODO: need to add gms
//    private val fusedLocationClient by lazy {
//        LocationServices.getFusedLocationProviderClient(this)
//    }

    private val defaultLocation by lazy {
        Location(null).apply {
            latitude = DEFAULT_LATITUDE
            longitude = DEFAULT_LONGITUDE
        }
    }

    private val locationManager by lazy { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }

    suspend operator fun invoke(): Location? = withContext(defaultDispatcher) {
        //TODO: need to add gms
        //val location = fusedLocationClient.lastLocation

        return@withContext if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            null
        } else {
            locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: defaultLocation
        }
    }
}

//region old code
/*
private fun getLocation(): Location? {
        Log.i(TAG, "hasGPS: $hasGps and hasNetworkLocation: $hasNetworkLocation")
        val locationManager = context
            .getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val listener = LocationListener { }

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.

            Log.i(TAG, "Need to request permissions")
            return null
        }

//        locationManager.requestLocationUpdates(
//            LocationManager.NETWORK_PROVIDER,
//            0,
//            1f,
//            listener
//        )


        //TODO: add support GPS location data
        val location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        locationManager.removeUpdates(listener)

        val latitude = location?.latitude
            ?: DEFAULT_LATITUDE //TODO: will be removed -> will use for development
        val longitude = location?.longitude
            ?: DEFAULT_LONGITUDE //TODO: will be removed -> will use for development

        Log.d(TAG, "latitude: $latitude longitude: $longitude")

        return location
    }
 */
//endregion