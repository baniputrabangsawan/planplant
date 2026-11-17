package com.jagadaun.app.data.repository

import com.jagadaun.app.data.local.PlantEntity
import com.jagadaun.app.data.local.ReminderEntity
import com.jagadaun.app.domain.CareGuideSource
import com.jagadaun.app.domain.Origin
import com.jagadaun.app.domain.Plant
import com.jagadaun.app.domain.Reminder

fun PlantEntity.toDomain(): Plant = Plant(
    id = id,
    displayName = displayName,
    scientificName = scientificName,
    notes = notes,
    photoRef = photoRef,
    origin = runCatching { Origin.valueOf(origin) }.getOrDefault(Origin.MANUAL),
    createdAt = createdAt,
    guide = CareGuideSource.forScientificName(scientificName),
)

fun Plant.toEntity(): PlantEntity = PlantEntity(
    id = id,
    displayName = displayName,
    scientificName = scientificName,
    notes = notes,
    photoRef = photoRef,
    origin = origin.name,
    createdAt = createdAt,
)

fun ReminderEntity.toDomain(): Reminder = Reminder(
    id = id,
    plantId = plantId,
    careType = careType,
    intervalDays = intervalDays,
    preferredTime = preferredTime,
    zoneId = zoneId,
    active = active,
    nextDue = nextDue,
)

fun Reminder.toEntity(): ReminderEntity = ReminderEntity(
    id = id,
    plantId = plantId,
    careType = careType,
    intervalDays = intervalDays,
    preferredTime = preferredTime,
    zoneId = zoneId,
    active = active,
    nextDue = nextDue,
)
