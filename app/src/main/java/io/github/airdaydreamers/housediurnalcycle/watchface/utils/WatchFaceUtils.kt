package io.github.airdaydreamers.housediurnalcycle.watchface.utils

import android.content.Context
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.Icon
import androidx.wear.watchface.CanvasComplicationFactory
import androidx.wear.watchface.ComplicationSlot
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.RoundRectComplicationTapFilter
import androidx.wear.watchface.complications.ComplicationSlotBounds
import androidx.wear.watchface.complications.DefaultComplicationDataSourcePolicy
import androidx.wear.watchface.complications.SystemDataSources
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.rendering.CanvasComplicationDrawable
import androidx.wear.watchface.complications.rendering.ComplicationDrawable
import androidx.wear.watchface.style.CurrentUserStyleRepository
import androidx.wear.watchface.style.UserStyleSchema
import androidx.wear.watchface.style.UserStyleSetting
import androidx.wear.watchface.style.WatchFaceLayer
import io.github.airdaydreamers.housediurnalcycle.watchface.*

class WatchFaceUtils {
    companion object {
        //TODO: not used yet.
        fun createComplicationSlotManager(
            context: Context,
            currentUserStyleRepository: CurrentUserStyleRepository
        ): ComplicationSlotsManager {

            val defaultCanvasComplicationFactory =
                CanvasComplicationFactory { watchState, invalidateCallback ->

                    CanvasComplicationDrawable(
                        ComplicationDrawable.getDrawable(
                            context, R.drawable.default_drawable_complication
                        )!!,
                        watchState,
                        invalidateCallback
                    )
                }

            val leftComplicationSlot = ComplicationSlot.createEdgeComplicationSlotBuilder(
                id = 102,
                canvasComplicationFactory = defaultCanvasComplicationFactory,
                bounds = ComplicationSlotBounds(
                    RectF(
                        0f,
                        0f,
                        0.4f,
                        0.4f
                    )
                ),
                defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(SystemDataSources.DATA_SOURCE_STEP_COUNT),
                supportedTypes = listOf(
                    ComplicationType.RANGED_VALUE,
                    ComplicationType.NO_DATA,
                    ComplicationType.MONOCHROMATIC_IMAGE,
                    ComplicationType.SHORT_TEXT,
                    ComplicationType.SMALL_IMAGE
                ),
                complicationTapFilter = RoundRectComplicationTapFilter()
            )
                .setDefaultDataSourceType(ComplicationType.SHORT_TEXT)
                .build()

            val rightComplicationSlot = ComplicationSlot.createEdgeComplicationSlotBuilder(
                id = 103,
                canvasComplicationFactory = defaultCanvasComplicationFactory,
                supportedTypes = listOf(
                    ComplicationType.RANGED_VALUE,
                    ComplicationType.NO_DATA,
                    ComplicationType.MONOCHROMATIC_IMAGE,
                    ComplicationType.SHORT_TEXT,
                    ComplicationType.SMALL_IMAGE
                ),
                defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(
                    SystemDataSources.DATA_SOURCE_WATCH_BATTERY
                ),
                bounds = ComplicationSlotBounds(
                    RectF(
                        0f,
                        0f,
                        0.4f,
                        0.4f
                    )
                ),
                complicationTapFilter = RoundRectComplicationTapFilter()
            )
                .setDefaultDataSourceType(ComplicationType.SHORT_TEXT)
                .build()

            return ComplicationSlotsManager(
                listOf(leftComplicationSlot, rightComplicationSlot),
                currentUserStyleRepository
            )
        }

        fun createUserStyleSchema(context: Context): UserStyleSchema {
            val colorStyleSetting =
                UserStyleSetting.ListUserStyleSetting(
                    UserStyleSetting.Id(Constants.COLOR_STYLE_SETTING),
                    context.resources,
                    R.string.colors_style_setting,
                    R.string.colors_style_setting_description,
                    null,
                    options = listOf(
                        UserStyleSetting.ListUserStyleSetting.ListOption(
                            UserStyleSetting.Option.Id("red_style"),
                            "Red",
                            icon = Icon.createWithResource(
                                context,
                                R.drawable.ic_launcher_new_foreground
                            ).setTint(Color.RED)
                        ),
                        UserStyleSetting.ListUserStyleSetting.ListOption(
                            UserStyleSetting.Option.Id("green_style"),
                            "Green",
                            icon = null
                        ),
                        UserStyleSetting.ListUserStyleSetting.ListOption(
                            UserStyleSetting.Option.Id("blue_style"),
                            "Blue",
                            icon = null
                        )
                    ),
                    listOf(
                        WatchFaceLayer.BASE,
                        WatchFaceLayer.COMPLICATIONS,
                        WatchFaceLayer.COMPLICATIONS_OVERLAY
                    )
                )

            //region TODO: will be hidden
            val debugSetting = UserStyleSetting.BooleanUserStyleSetting(
                UserStyleSetting.Id(Constants.DEBUG_SETTING),
                context.resources,
                R.string.watchface_debug_setting,
                R.string.watchface_debug_setting_description,
                null,
                listOf(WatchFaceLayer.BASE),
                true
            )
            //endregion

            val clockTypeSetting = UserStyleSetting.ListUserStyleSetting(
                UserStyleSetting.Id(Constants.CLOCK_TYPE_SETTING),
                context.resources,
                R.string.watchface_clock_type_setting,
                R.string.watchface_clock_type_setting_description,
                icon = null,
                options = listOf(
                    UserStyleSetting.ListUserStyleSetting.ListOption(
                        UserStyleSetting.Option.Id("classic_style"), "Classic", icon = null
                    ),
                    UserStyleSetting.ListUserStyleSetting.ListOption(
                        UserStyleSetting.Option.Id("digital_style"), "Digital", icon = null
                    ),
                ),
                listOf(WatchFaceLayer.COMPLICATIONS_OVERLAY)
            )

            return UserStyleSchema(
                listOf(
                    colorStyleSetting,
                    clockTypeSetting,
                    debugSetting,
                )
            )
        }
    }
}