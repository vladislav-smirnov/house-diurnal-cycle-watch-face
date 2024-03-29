package io.github.airdaydreamers.housediurnalcycle.watchface.utils

import android.graphics.ColorFilter
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.SimpleColorFilter
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback

fun LottieAnimationView.changeLayersColor(
    @ColorRes colorRes: Int, keys: ArrayList<String>? = arrayListOf()
) {
    val color = ContextCompat.getColor(context, colorRes)
    val filter = SimpleColorFilter(color)
    val keyPath = if (!keys.isNullOrEmpty()) {
        KeyPath(*keys.toTypedArray(), "**")
    } else {
        KeyPath("**")
    }
    val callback: LottieValueCallback<ColorFilter> = LottieValueCallback(filter)

    addValueCallback(keyPath, LottieProperty.COLOR_FILTER, callback)
}