package com.jagadaun.app.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val ZONE = ZoneId.of("Asia/Jakarta")
private val DAY_FORMAT = DateTimeFormatter.ofPattern("d MMM")

fun LocalDate.toEpochMillis(): Long = atStartOfDay(ZONE).toInstant().toEpochMilli()

fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZONE).toLocalDate()

/** Label hari relatif untuk jadwal pengingat: "Hari ini", "Besok", "Terlambat", atau tanggal. */
fun Long.dayLabel(today: LocalDate = LocalDate.now(ZONE)): String {
    val due = toLocalDate()
    val days = ChronoUnit.DAYS.between(today, due)
    return when {
        days == 0L -> "Hari ini"
        days == 1L -> "Besok"
        days < 0L -> "Terlambat"
        else -> due.format(DAY_FORMAT)
    }
}

/** Apakah jadwal jatuh pada hari ini atau sudah lewat. */
fun Long.isTodayOrOverdue(today: LocalDate = LocalDate.now(ZONE)): Boolean =
    !toLocalDate().isAfter(today)
