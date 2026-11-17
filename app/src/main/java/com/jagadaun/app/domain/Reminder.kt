package com.jagadaun.app.domain

/** Pengingat perawatan untuk satu tanaman. */
data class Reminder(
    val id: String,
    val plantId: String,
    val careType: String,
    val intervalDays: Int,
    val preferredTime: String,
    val zoneId: String = "Asia/Jakarta",
    val active: Boolean = true,
    val nextDue: Long,
)
