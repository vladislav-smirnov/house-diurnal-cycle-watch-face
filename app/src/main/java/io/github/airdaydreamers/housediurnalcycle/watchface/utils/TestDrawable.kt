package io.github.airdaydreamers.housediurnalcycle.watchface.utils

import android.annotation.SuppressLint
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Rect
import android.util.TypedValue
import androidx.annotation.CallSuper
import androidx.wear.watchface.CanvasComplication
import androidx.wear.watchface.RenderParameters
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.rendering.CanvasComplicationDrawable
import androidx.wear.watchface.complications.rendering.ComplicationDrawable
import androidx.wear.watchface.complications.rendering.ComplicationHighlightRenderer
import androidx.wear.watchface.utility.TraceEvent
import java.time.ZonedDateTime

class TestDrawable constructor(
    drawable: ComplicationDrawable,
    private val watchState: WatchState,
    private val invalidateCallback: CanvasComplication.InvalidateCallback
) : CanvasComplication {
    internal companion object {
        // Complications are highlighted when tapped and after this delay the highlight is removed.
        internal const val COMPLICATION_HIGHLIGHT_DURATION_MS = 300L

        internal const val EXPANSION_DP = 6.0f
        internal const val STROKE_WIDTH_DP = 3.0f
    }

    private val complicationHighlightRenderer by lazy {
        ComplicationHighlightRenderer(
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                EXPANSION_DP,
                Resources.getSystem().displayMetrics
            ),

            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                STROKE_WIDTH_DP,
                Resources.getSystem().displayMetrics
            )
        )
    }

    private var _data: ComplicationData = NoDataComplicationData()

    override fun drawHighlight(
        canvas: Canvas,
        bounds: Rect,
        boundsType: Int,
        zonedDateTime: ZonedDateTime,
        color: Int
    ) {
        complicationHighlightRenderer.drawComplicationHighlight(
            canvas,
            bounds,
            color
        )
    }

    override fun getData(): ComplicationData {
        return _data
    }

    @CallSuper
    override fun loadData(
        complicationData: ComplicationData,
        loadDrawablesAsynchronous: Boolean
    ): Unit = TraceEvent("CanvasComplicationDrawable.setIdAndData").use {
        _data = complicationData

//        drawable.setComplicationData(
//            complicationData,
//            loadDrawablesAsynchronous
//        )
    }
    @SuppressLint("ResourceAsColor")
    override fun render(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        renderParameters: RenderParameters,
        slotId: Int
    ) {
        canvas.drawColor(android.R.color.holo_red_dark)
    }

}