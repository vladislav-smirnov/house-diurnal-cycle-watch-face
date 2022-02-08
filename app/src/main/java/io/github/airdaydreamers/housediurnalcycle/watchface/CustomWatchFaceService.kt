package io.github.airdaydreamers.housediurnalcycle.watchface

import android.util.Log
import android.view.SurfaceHolder
import androidx.wear.watchface.*
import androidx.wear.watchface.style.CurrentUserStyleRepository

class CustomWatchFaceService : WatchFaceService() {
    companion object {
        const val TAG = "CustomWatchFaceService"
    }

    override suspend fun createWatchFace(
        surfaceHolder: SurfaceHolder,
        watchState: WatchState,
        complicationSlotsManager: ComplicationSlotsManager,
        currentUserStyleRepository: CurrentUserStyleRepository
    ): WatchFace {
        Log.d(TAG, "createWatchFace()")

        // Creates class that renders the watch face.
        val renderer = DiurnalCanvasRenderer(
            context = applicationContext,
            surfaceHolder = surfaceHolder,
            watchState = watchState,
            complicationSlotsManager = complicationSlotsManager,
            currentUserStyleRepository = currentUserStyleRepository,
            canvasType = CanvasType.HARDWARE
        )

        // Creates the watch face.
        return WatchFace(
            watchFaceType = WatchFaceType.ANALOG,
            renderer = renderer
        )
    }
}