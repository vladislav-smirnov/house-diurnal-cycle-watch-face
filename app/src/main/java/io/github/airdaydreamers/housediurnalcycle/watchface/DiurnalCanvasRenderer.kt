package io.github.airdaydreamers.housediurnalcycle.watchface

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.Rect
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.util.Log
import android.view.*
import androidx.core.app.ActivityCompat
import androidx.wear.watchface.*
import androidx.wear.watchface.style.CurrentUserStyleRepository
import com.airbnb.lottie.LottieAnimationView
import io.github.airdaydreamers.housediurnalcycle.watchface.data.time.DailyStatus
import io.github.airdaydreamers.housediurnalcycle.watchface.data.time.DailyTime
import io.github.airdaydreamers.housediurnalcycle.watchface.utils.Constants
import io.github.airdaydreamers.housediurnalcycle.watchface.utils.changeLayersColor
import io.github.airdaydreamers.timetext.TimeText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.shredzone.commons.suncalc.SunTimes
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs

private const val FRAME_PERIOD_MS_DEFAULT: Long = 16L

class DiurnalCanvasRenderer(
    private val context: Context,
    surfaceHolder: SurfaceHolder,
    watchState: WatchState,
    private val complicationSlotsManager: ComplicationSlotsManager,
    currentUserStyleRepository: CurrentUserStyleRepository,
    canvasType: Int
): Renderer.CanvasRenderer2<DiurnalCanvasRenderer.ClockSharedAssets>(
    surfaceHolder,
    currentUserStyleRepository,
    watchState,
    canvasType,
    FRAME_PERIOD_MS_DEFAULT,
    clearWithBackgroundTintBeforeRenderingHighlightLayer = false
) {
    inner class ClockSharedAssets : SharedAssets {
        override fun onDestroy() {
        }
    }
    companion object {
        const val TAG = "DiurnalCanvasRenderer"

        //Default location is BERLIN
        private const val DEFAULT_LATITUDE = 52.520008
        private const val DEFAULT_LONGITUDE = 13.404954

        private const val LOTTIE_NAME_PATH_CONTENT = "content"
        private const val LOTTIE_NAME_PATH_BATTERY_GROUP = "battery_changes"
        private const val LOTTIE_NAME_PATH_BATTERY_CELL_4 = "battery_item_0_4"
        private const val LOTTIE_NAME_PATH_BATTERY_CELL_3 = "battery_item_0_3"
        private const val LOTTIE_NAME_PATH_BATTERY_CELL_2 = "battery_item_0_2"
        private const val LOTTIE_NAME_PATH_BATTERY_CELL_1 = "battery_item_0_1"
    }

    private lateinit var rootView: View
    private lateinit var houseView: LottieAnimationView
    //private lateinit var clockView: CurvedTextView

    private var listOfTimes: Array<DailyTime> = arrayOf(DailyTime(), DailyTime())
    private var currentDailyStatus: DailyStatus = DailyStatus.DAY
    private var currentProgress = 0.0f  //0f to 1f

    private val currentScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)


    private val hasGps: Boolean by lazy { context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS) }
    private val hasNetworkLocation: Boolean by lazy {
        context.packageManager.hasSystemFeature(
            PackageManager.FEATURE_LOCATION_NETWORK
        )
    }

    private val lottiePathBattery4 = arrayListOf(
        LOTTIE_NAME_PATH_CONTENT,
        LOTTIE_NAME_PATH_BATTERY_GROUP,
        LOTTIE_NAME_PATH_BATTERY_CELL_4
    )
    private val lottiePathBattery3 = arrayListOf(
        LOTTIE_NAME_PATH_CONTENT,
        LOTTIE_NAME_PATH_BATTERY_GROUP,
        LOTTIE_NAME_PATH_BATTERY_CELL_3
    )
    private val lottiePathBattery2 = arrayListOf(
        LOTTIE_NAME_PATH_CONTENT,
        LOTTIE_NAME_PATH_BATTERY_GROUP,
        LOTTIE_NAME_PATH_BATTERY_CELL_2
    )
    private val lottiePathBattery1 = arrayListOf(
        LOTTIE_NAME_PATH_CONTENT,
        LOTTIE_NAME_PATH_BATTERY_GROUP,
        LOTTIE_NAME_PATH_BATTERY_CELL_1
    )

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
        (rootView.findViewById(R.id.timeText) as TimeText).also {
            it.onAttachedToWindow()
            it.measure(specW, specH)
        }
        //clockView = rootView.findViewById(R.id.digital_clock_view)
    }

    override suspend fun createSharedAssets(): ClockSharedAssets {
        return ClockSharedAssets()
    }

    override fun onDestroy() {
        super.onDestroy()
        (rootView.findViewById(R.id.timeText) as TimeText).onDetachedFromWindow()
        Log.d(TAG, "onDestroy")
    }

    override fun onRenderParametersChanged(renderParameters: RenderParameters) {
        super.onRenderParametersChanged(renderParameters)

        Log.v(TAG, "drawMode: ${renderParameters.drawMode}")
    }

    override fun render(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        sharedAssets: ClockSharedAssets
    ) {
        Log.v(TAG, "$zonedDateTime")

        Log.v(TAG, "render => drawMode: ${renderParameters.drawMode}")
        //Will use
        when (renderParameters.drawMode) {
            DrawMode.AMBIENT -> {}
            DrawMode.INTERACTIVE -> {}
            DrawMode.LOW_BATTERY_INTERACTIVE -> {}
            DrawMode.MUTE -> {}
        }

        //testTime = testTime.plusSeconds(20)

        val status = computeDailyStatus(zonedDateTime)
        Log.d(TAG, "status: $status")

        rootView.draw(canvas)
        animate(status, zonedDateTime)

        //TODO: make awesome digital clock
        //clockView.text = "${zonedDateTime.hour}:${zonedDateTime.minute}"

        //TODO: draw on edge. at this moment we can see circle
        drawComplications(canvas, zonedDateTime)


        //region test lottie will be changed
//        if (countDown >= 0.5056f) {
//            countDown = 0.2466f
//        }

        //countDown += 0.001f

        //houseView.progress = countDown
        //Log.d(TAG, "countDown = $countDown")
        //canvas.drawColor(Color)
        //endregion

        drawBatteryLevel()
    }

    //TODO: need to complete
    override fun renderHighlightLayer(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        sharedAssets: ClockSharedAssets
    ) {
        Log.d(TAG, "renderHighlightLayer")

        canvas.drawColor(renderParameters.highlightLayer!!.backgroundTint)

        for ((_, complication) in complicationSlotsManager.complicationSlots) {
            if (complication.enabled) {
                complication.renderHighlightLayer(canvas, zonedDateTime, renderParameters)
            }
        }
    }

    private fun drawComplications(canvas: Canvas, zonedDateTime: ZonedDateTime) {
        for ((_, complication) in complicationSlotsManager.complicationSlots) {
            if (complication.enabled) {
                complication.render(canvas, zonedDateTime, renderParameters)
            }
        }
    }

    //region logic TODO: decide to make in separate file
    private fun animate(
        dailyStatus: DailyStatus,
        currentTime: ZonedDateTime
    ) {
        when (dailyStatus) {
            DailyStatus.SUNRISE -> {
                if (currentDailyStatus != dailyStatus) {
                    currentDailyStatus = dailyStatus
                    currentProgress = 0.7400f //max 1
                }

                var sunrise: ZonedDateTime?
                currentTime.dayOfYear.rem(2).also {
                    sunrise = listOfTimes[it].sunrise
                }

                //0.0037 per minute
                val progressOfEvent = abs(ChronoUnit.MINUTES.between(currentTime, sunrise))
                houseView.progress = 0.0037f * progressOfEvent + 0.7400f
            }

            DailyStatus.DAY -> {
                //clockView.textColor = Color.BLACK
                //clockView.invalidate()

                if (currentDailyStatus != dailyStatus) {
                    currentDailyStatus = dailyStatus
                    currentProgress = 0.0f  //max 0.2466f
                }

                if (currentProgress >= 0.2466f) {
                    currentProgress = 0.0f
                }

                currentProgress += 0.001f
                houseView.progress = currentProgress
            }

            DailyStatus.SUNSET -> {
                if (currentDailyStatus != dailyStatus) {
                    currentDailyStatus = dailyStatus
                    currentProgress = 0.2466f // max 0.5056f
                }

                //0.0037 per minute
                val sunset: ZonedDateTime?
                currentTime.dayOfYear.rem(2).also {
                    sunset = listOfTimes[it].sunset
                }

                val progressOfEvent = abs(ChronoUnit.MINUTES.between(currentTime, sunset))

                val estimatedProgress = 0.0037f * progressOfEvent + 0.2466f
                houseView.progress = estimatedProgress
            }

            DailyStatus.NIGHT -> {
                //clockView.textColor = Color.WHITE
                //clockView.invalidate()

                if (currentDailyStatus != dailyStatus) {
                    currentDailyStatus = dailyStatus
                    currentProgress = 0.5056f  // max =0.7400f
                }

                if (currentProgress >= 0.7400f) {
                    currentProgress = 0.5056f
                }

                currentProgress += 0.001f
                houseView.progress = currentProgress
            }
        }
    }

    private fun computeDailyStatus(zonedDateTime: ZonedDateTime): DailyStatus {
        //Get current location //TODO: need to request permissions
        val location = getLocation()

        val times = if (location != null) {
            SunTimes.compute()
                .on(zonedDateTime)
                .at(location.latitude, location.longitude)
                .execute()
        } else {
            SunTimes.compute().execute()
        }
        Log.v(TAG, "SunTimes: $times")

        times.rise?.dayOfYear?.rem(2)?.also {
            listOfTimes[it].sunrise = times.rise
        }

        times.set?.dayOfYear?.rem(2)?.also {
            listOfTimes[it].sunset = times.set
        }

        var sunrise: ZonedDateTime?
        var sunset: ZonedDateTime?

        zonedDateTime.dayOfYear.rem(2).also {
            sunrise = listOfTimes[it].sunrise
            sunset = listOfTimes[it].sunset
        }

        Log.v(TAG, "sunrise: $sunrise sunset: $sunset")

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
            isSunriseOrSunset(
                currentTime,
                eventTime = sunrise ?: virtualTimes.rise
            ) -> DailyStatus.SUNRISE

            isDay(sunrise, sunset, currentTime, computedTimes) -> DailyStatus.DAY
            isSunriseOrSunset(
                currentTime,
                eventTime = sunset ?: virtualTimes.set
            ) -> DailyStatus.SUNSET

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

    private fun isSunriseOrSunset(currentTime: ZonedDateTime, eventTime: ZonedDateTime?): Boolean {
        return (currentTime.isEqual(eventTime)
                || (currentTime.isAfter(eventTime)
                && currentTime.isBefore(eventTime?.plusMinutes(Constants.DEFAULT_DURATION_RISE_SET_MIN)))
                )
    }

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
    //endregion

    private fun getBatteryLevel(): Int? {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }

        val batteryPct: Float? = batteryStatus?.let { intent ->
            val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            level * 100 / scale.toFloat()
        }

        Log.v(TAG, "Battery Percent = $batteryPct")
        return batteryPct?.toInt()
    }

    private fun drawBatteryLevel() {
        when (getBatteryLevel() ?: 0) {
            in 0..25 -> {
                houseView.changeLayersColor(
                    android.R.color.white,
                    lottiePathBattery4
                )
                houseView.changeLayersColor(
                    android.R.color.white,
                    lottiePathBattery3
                )
                houseView.changeLayersColor(
                    android.R.color.white,
                    lottiePathBattery2
                )
                houseView.changeLayersColor(
                    android.R.color.holo_red_dark,
                    lottiePathBattery1
                )
            }

            in 26..50 -> {
                houseView.changeLayersColor(
                    android.R.color.white,
                    lottiePathBattery4
                )
                houseView.changeLayersColor(
                    android.R.color.white,
                    lottiePathBattery3
                )
                houseView.changeLayersColor(
                    android.R.color.holo_orange_dark,
                    lottiePathBattery2
                )
                houseView.changeLayersColor(
                    android.R.color.holo_orange_dark,
                    lottiePathBattery1
                )
            }

            in 50..75 -> {
                houseView.changeLayersColor(
                    android.R.color.white,
                    lottiePathBattery4
                )
                houseView.changeLayersColor(
                    android.R.color.holo_green_dark,
                    lottiePathBattery3
                )
                houseView.changeLayersColor(
                    android.R.color.holo_green_dark,
                    lottiePathBattery2
                )
                houseView.changeLayersColor(
                    android.R.color.holo_green_dark,
                    lottiePathBattery1
                )
            }

            in 75..100 -> {
                houseView.changeLayersColor(
                    android.R.color.holo_green_dark,
                    lottiePathBattery4
                )
                houseView.changeLayersColor(
                    android.R.color.holo_green_dark,
                    lottiePathBattery3
                )
                houseView.changeLayersColor(
                    android.R.color.holo_green_dark,
                    lottiePathBattery2
                )
                houseView.changeLayersColor(
                    android.R.color.holo_green_dark,
                    lottiePathBattery1
                )
            }
        }
    }
}