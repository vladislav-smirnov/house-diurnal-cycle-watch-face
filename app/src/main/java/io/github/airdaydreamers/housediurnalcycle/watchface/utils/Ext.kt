package io.github.airdaydreamers.housediurnalcycle.watchface.utils

import android.location.Location
import java.time.Instant
import java.time.ZonedDateTime
import java.util.Calendar

fun Location.toLibLocation(): com.luckycatlabs.sunrisesunset.dto.Location {
    return com.luckycatlabs.sunrisesunset.dto.Location(this.latitude, this.longitude)
}

fun ZonedDateTime.tomorrow(): ZonedDateTime = ZonedDateTime.of(
    this.year,
    this.monthValue,
    this.dayOfMonth + 1,
    1,
    0,
    0,
    0,
    this.zone
)

fun Calendar.toZonedDateTime(): ZonedDateTime {
    val instant: Instant = this.time.toInstant()

    return instant.atZone(this.timeZone.toZoneId())
}