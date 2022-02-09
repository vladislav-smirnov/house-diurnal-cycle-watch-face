package io.github.airdaydreamers.housediurnalcycle.watchface

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.Rect
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.util.Log
import android.view.*
import androidx.core.app.ActivityCompat
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.style.CurrentUserStyleRepository
import com.airbnb.lottie.LottieAnimationView
import io.github.airdaydreamers.housediurnalcycle.watchface.data.time.DailyStatus
import io.github.airdaydreamers.housediurnalcycle.watchface.data.time.DailyTime
import io.github.airdaydreamers.housediurnalcycle.watchface.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.shredzone.commons.suncalc.SunTimes
import java.time.ZonedDateTime


class DiurnalCanvasRenderer(
    private val context: Context,
    surfaceHolder: SurfaceHolder,
    watchState: WatchState,
    private val complicationSlotsManager: ComplicationSlotsManager,
    currentUserStyleRepository: CurrentUserStyleRepository,
    canvasType: Int
) : Renderer.CanvasRenderer(
    surfaceHolder = surfaceHolder,
    currentUserStyleRepository = currentUserStyleRepository,
    watchState = watchState,
    canvasType = canvasType,
    interactiveDrawModeUpdateDelayMillis = 16L
) {
    companion object {
        const val TAG = "DiurnalCanvasRenderer"
    }

    private lateinit var rootView: View
    private lateinit var houseView: LottieAnimationView

    private var listOfTimes: Array<DailyTime?> = arrayOfNulls(2)

    private var countDown: Float = 0f

    private val currentScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    //region timecodes
    /*
    0f -00
    1=> 10
    2 => 20.500
    3=> 30
    4 => 40.540


    2=> from 10 to 20.500 = 10.500
    2-3 => 9.500

    0-1 = 0.2466f
    1-2 = 0.5056f
    2-3 = 0.7400f
    3-4 = 1f
     */
    //endregion
    init {
        initViews()

        currentScope.launch {
            currentUserStyleRepository.userStyle.collect { userStyle ->
                //updateWatchFaceData(userStyle)
            }
        }
    }

    @SuppressLint("InflateParams")
    private fun initViews() {
        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).also {
            it as LayoutInflater
            rootView = it.inflate(R.layout.watch_face_layout, null)
        }

        val displaySize =
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .currentWindowMetrics.let {
                    val insets = it.windowInsets.getInsets(WindowInsets.Type.systemBars())
                    val width = it.bounds.width() - insets.left - insets.right
                    val height = it.bounds.height() - insets.bottom - insets.top
                    Point(width, height)
                }

        val specW = View.MeasureSpec.makeMeasureSpec(displaySize.x, View.MeasureSpec.EXACTLY)  //x
        val specH = View.MeasureSpec.makeMeasureSpec(displaySize.y, View.MeasureSpec.EXACTLY)  //y

        // Update the layout
        rootView.measure(specW, specH)
        rootView.layout(0, 0, rootView.measuredWidth, rootView.measuredHeight)

        houseView = rootView.findViewById(R.id.animation_view)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
    }

    override fun render(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime) {
        Log.v(TAG, "$zonedDateTime")

        //val test = zonedDateTime.plusMinutes(61)

        val status = computeDailyStatus(zonedDateTime)

        Log.d(TAG, "status: $status")
        //region test lottie will be changed
        rootView.draw(canvas)

        if (countDown >= 0.2466f) {
            countDown = 0f
        }

        countDown += 0.001f
        houseView.progress = countDown
        Log.d(TAG, "countDown = $countDown")
        //canvas.drawColor(Color)
        //endregion
    }

    private fun computeDailyStatus(zonedDateTime: ZonedDateTime): DailyStatus {
        //Get current location //TODO: need to request permissions
        val location = getLocation()

        val times = if (location != null) {
            val times = SunTimes.compute()
                .on(zonedDateTime)
                .at(location.latitude, location.longitude)
                .execute()
            times
        } else {
            SunTimes.compute().execute()
        }

        times.rise?.dayOfYear?.rem(2)?.also {
            listOfTimes[it]?.sunrise = times.rise
        }

        times.set?.dayOfYear?.rem(2)?.also {
            listOfTimes[it]?.sunset = times.set
        }

        var sunrise: ZonedDateTime?
        var sunset: ZonedDateTime?

        zonedDateTime.dayOfYear.rem(2).also {
            sunrise = listOfTimes[it]?.sunrise
            sunset = listOfTimes[it]?.sunset
        }

        val virtualTimes = if (sunrise == null || sunset == null) {
            //if all values for current day is null
            val virtualZonedDateTime = ZonedDateTime.of(
                zonedDateTime.year,
                zonedDateTime.monthValue,
                zonedDateTime.dayOfMonth,
                1,
                0,
                0,
                0,
                zonedDateTime.zone
            )

            location?.let {
                SunTimes.compute()
                    .on(virtualZonedDateTime)
                    .at(it.latitude, it.longitude)
                    .execute()
            }
        } else null

        return getDailyStatus(
            sunrise = sunrise,
            sunset = sunset,
            currentTime = zonedDateTime,
            virtualTimes = virtualTimes ?: SunTimes.compute().execute(),
            computedTimes = times
        )
    }

    private fun getDailyStatus(
        sunrise: ZonedDateTime?,
        sunset: ZonedDateTime?,
        currentTime: ZonedDateTime,
        computedTimes: SunTimes,
        virtualTimes: SunTimes
    ): DailyStatus {
        return when {
            isEventTime(sunrise, currentTime, virtualTimes) -> DailyStatus.SUNRISE
            isDay(sunrise, sunset, currentTime, computedTimes) -> DailyStatus.DAY
            isEventTime(sunset, currentTime, virtualTimes) -> DailyStatus.SUNSET
            else -> DailyStatus.NIGHT
        }
    }

    private fun isDay(
        sunrise: ZonedDateTime?,
        sunset: ZonedDateTime?,
        currentTime: ZonedDateTime,
        computedTimes: SunTimes
    ): Boolean {
        return (sunrise != null
                && (currentTime.isAfter(sunrise.plusMinutes(Constants.DEFAULT_DURATION_RISE_SET_MIN))
                && currentTime.isBefore(sunset)))
                || ((sunrise == null && sunset != null)
                && currentTime.isBefore(sunset)
                && (computedTimes.rise?.isAfter(computedTimes.set) == true))
    }

    private fun isEventTime(
        eventTime: ZonedDateTime?,
        currentTime: ZonedDateTime,
        virtualTimes: SunTimes
    ): Boolean {
        return isSunriseOrSunset(currentTime, eventTime = eventTime ?: virtualTimes.set)
    }

    private fun isSunriseOrSunset(currentTime: ZonedDateTime, eventTime: ZonedDateTime?): Boolean {
        return (currentTime.isEqual(eventTime)
                || (currentTime.isAfter(eventTime)
                && currentTime.isBefore(eventTime?.plusMinutes(Constants.DEFAULT_DURATION_RISE_SET_MIN)))
                )
    }

    private fun getLocation(): Location? {
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
        locationManager.requestLocationUpdates(
            LocationManager.NETWORK_PROVIDER,
            1,
            1f,
            listener
        )


        //TODO: add support GPS location data
        val location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        locationManager.removeUpdates(listener)

        val latitude = location?.latitude
        val longitude = location?.longitude

        Log.d(TAG, "latitude: $latitude longitude: $longitude")

        return location
    }

    //TODO: need to complete
    override fun renderHighlightLayer(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime) {
        Log.d(TAG, "renderHighlightLayer")

        canvas.drawColor(renderParameters.highlightLayer!!.backgroundTint)

        for ((_, complication) in complicationSlotsManager.complicationSlots) {
            if (complication.enabled) {
                complication.renderHighlightLayer(canvas, zonedDateTime, renderParameters)
            }
        }
    }
}