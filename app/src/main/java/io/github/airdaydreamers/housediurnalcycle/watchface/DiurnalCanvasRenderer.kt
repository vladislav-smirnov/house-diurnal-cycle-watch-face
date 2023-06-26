package io.github.airdaydreamers.housediurnalcycle.watchface

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.Rect
import android.location.Location
import android.os.BatteryManager
import android.util.Log
import android.view.*
import androidx.lifecycle.Observer
import androidx.wear.watchface.*
import androidx.wear.watchface.style.CurrentUserStyleRepository
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.airbnb.lottie.LottieAnimationView
import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator
import io.github.airdaydreamers.housediurnalcycle.watchface.data.time.DailyStatus
import io.github.airdaydreamers.housediurnalcycle.watchface.data.time.DailyTime
import io.github.airdaydreamers.housediurnalcycle.watchface.services.PeriodicWorker
import io.github.airdaydreamers.housediurnalcycle.watchface.usecases.GetLocationUseCase
import io.github.airdaydreamers.housediurnalcycle.watchface.utils.Constants
import io.github.airdaydreamers.housediurnalcycle.watchface.utils.changeLayersColor
import io.github.airdaydreamers.housediurnalcycle.watchface.utils.toLibLocation
import io.github.airdaydreamers.housediurnalcycle.watchface.utils.toZonedDateTime
import io.github.airdaydreamers.timetext.TimeText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.GregorianCalendar
import java.util.TimeZone
import kotlin.math.abs


private const val FRAME_PERIOD_MS_DEFAULT: Long = 16L

class DiurnalCanvasRenderer(
    private val context: Context,
    surfaceHolder: SurfaceHolder,
    watchState: WatchState,
    private val complicationSlotsManager: ComplicationSlotsManager,
    currentUserStyleRepository: CurrentUserStyleRepository,
    canvasType: Int
) : Renderer.CanvasRenderer2<DiurnalCanvasRenderer.ClockSharedAssets>(
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

        private const val LOTTIE_NAME_PATH_CONTENT = "content"
        private const val LOTTIE_NAME_PATH_BATTERY_GROUP = "battery_changes"
        private const val LOTTIE_NAME_PATH_BATTERY_CELL_4 = "battery_item_0_4"
        private const val LOTTIE_NAME_PATH_BATTERY_CELL_3 = "battery_item_0_3"
        private const val LOTTIE_NAME_PATH_BATTERY_CELL_2 = "battery_item_0_2"
        private const val LOTTIE_NAME_PATH_BATTERY_CELL_1 = "battery_item_0_1"
    }

    private lateinit var rootView: View
    private lateinit var houseView: LottieAnimationView

    private var dailyTime: DailyTime? = null
    private var currentDailyStatus: DailyStatus = DailyStatus.DAY
    private var currentProgress = 0.0f  //0f to 1f

    private val currentScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var currentLocation: Location? = null

    //region battery
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
    //endregion

    private val locationObserver = Observer<WorkInfo?> { workInfo ->
        if (workInfo != null && workInfo.state == WorkInfo.State.SUCCEEDED) {
            val outputData = workInfo.outputData
            val lat = outputData.getDouble(PeriodicWorker.DATA_RESULT_KEY_LAT, -201.0)
            val long = outputData.getDouble(PeriodicWorker.DATA_RESULT_KEY_LONG, -201.0)

            if (currentLocation == null) {
                currentLocation = Location(null)
            }

            lat.takeIf { it != -201.0 }?.also {
                currentLocation?.latitude = it
            }

            long.takeIf { it != -201.0 }?.also {
                currentLocation?.longitude = it
            }
        }
    }

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
            //TODO: check permissions
            currentLocation = GetLocationUseCase(context.applicationContext)()

            currentUserStyleRepository.userStyle.collect { userStyle ->
                //updateWatchFaceData(userStyle)
            }
        }

        currentScope.launch {
            val id = PeriodicWorker.scheduleWork(context.applicationContext)
            //TODO: bad idea to observeForever.
            withContext(Dispatchers.Main) {
                WorkManager.getInstance(context.applicationContext).getWorkInfoByIdLiveData(id)
                    .observeForever(locationObserver)
            }
        }

        updateDailyTime(ZonedDateTime.now())
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
        Log.v(TAG, "render => drawMode: ${renderParameters.drawMode}")

        //TODO: need to complete
        //region Will use
        when (renderParameters.drawMode) {
            DrawMode.AMBIENT -> {}
            DrawMode.INTERACTIVE -> {}
            DrawMode.LOW_BATTERY_INTERACTIVE -> {}
            DrawMode.MUTE -> {}
        }
        //endregion

        //testTime = testTime.plusSeconds(20)

        val status = computeDailyStatus(zonedDateTime)
        updateDailyTime(zonedDateTime)
        Log.d(TAG, "DailyStatus: $status")

        rootView.draw(canvas)
        animate(status, zonedDateTime)

        //TODO: draw on edge. at this moment we can see circle
        //TODO: should be called less times -> only when something was changed
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

    //TODO: decide to make in separate file
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

//                var sunrise: ZonedDateTime?
//                currentTime.dayOfYear.rem(2).also {
//                    sunrise = listOfTimes[it].sunrise
//                }

                //0.0037 per minute
                val progressOfEvent = abs(ChronoUnit.MINUTES.between(currentTime, dailyTime?.sunrise))
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
//                val sunset: ZonedDateTime?
//                currentTime.dayOfYear.rem(2).also {
//                    sunset = listOfTimes[it].sunset
//                }

                val progressOfEvent = abs(ChronoUnit.MINUTES.between(currentTime, dailyTime?.sunset))

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

    //region dailyStatus
    private fun computeDailyStatus(currentZonedDateTime: ZonedDateTime): DailyStatus {
        //Get current location //TODO: need to request permissions
        //TODO: should be called less times

        updateDailyTime(currentZonedDateTime)

        return getDailyStatus(currentZonedDateTime)
    }

    private fun updateDailyTime(currentZonedDateTime: ZonedDateTime) {
        if (dailyTime?.sunrise?.dayOfYear == currentZonedDateTime.dayOfYear) {
            return
        }
        val calculator = SunriseSunsetCalculator(
            currentLocation?.toLibLocation() ?: com.luckycatlabs.sunrisesunset.dto.Location(
                GetLocationUseCase.DEFAULT_LATITUDE,
                GetLocationUseCase.DEFAULT_LONGITUDE
            ), TimeZone.getDefault()
        )

        val date = GregorianCalendar.from(currentZonedDateTime)
        val sunrise = calculator.getOfficialSunriseCalendarForDate(date)
        val sunset = calculator.getOfficialSunsetCalendarForDate(date)

        dailyTime =
            DailyTime(sunrise = sunrise.toZonedDateTime(), sunset = sunset.toZonedDateTime())
        Log.i(
            TAG,
            "Set new times. sunrise: ${dailyTime?.sunrise.toString()} sunset: ${dailyTime?.sunset.toString()}}"
        )
    }

    private fun getDailyStatus(currentTime: ZonedDateTime): DailyStatus {
        return when {
            isSunriseOrSunset(
                currentTime,
                eventTime = dailyTime?.sunrise
            ) -> DailyStatus.SUNRISE

            isDay(currentTime) -> DailyStatus.DAY
            isSunriseOrSunset(
                currentTime,
                eventTime = dailyTime?.sunset
            ) -> DailyStatus.SUNSET

            else -> DailyStatus.NIGHT
        }
    }

    private fun isDay(currentTime: ZonedDateTime): Boolean {
        if (dailyTime == null || dailyTime?.sunrise == null || dailyTime?.sunset == null) {
            return true //Let's use as default status
        }
        return currentTime.isAfter(dailyTime?.sunrise?.plusMinutes(Constants.DEFAULT_DURATION_RISE_SET_MIN))
                && currentTime.isBefore(dailyTime?.sunset)
    }

    private fun isSunriseOrSunset(currentTime: ZonedDateTime, eventTime: ZonedDateTime?): Boolean {
        return eventTime != null && (currentTime.isEqual(eventTime)
                || (currentTime.isAfter(eventTime)
                && currentTime.isBefore(eventTime.plusMinutes(Constants.DEFAULT_DURATION_RISE_SET_MIN)))
                )
    }
    //endregion

    //region battery
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
    //endregion
}