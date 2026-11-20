package com.jagadaun.app.domain

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Next care is measured in calendar days from completion, at the chosen local clock time. */
fun nextReminderDue(intervalDays: Int, preferredTime: String, zoneId: String, fromMillis: Long): Long {
    require(intervalDays > 0)
    require(preferredTime.length == 5 && preferredTime[2] == ':')
    val time = LocalTime.parse(preferredTime)
    val zone = ZoneId.of(zoneId)
    return Instant.ofEpochMilli(fromMillis).atZone(zone).toLocalDate()
        .plusDays(intervalDays.toLong()).atTime(time).atZone(zone).toInstant().toEpochMilli()
}
