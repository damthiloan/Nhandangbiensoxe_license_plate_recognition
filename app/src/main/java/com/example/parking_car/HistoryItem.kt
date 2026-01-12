package com.example.parking_car

data class HistoryItem(
    val id: String,
    val licensePlate: String,
    val entryTime: String,
    val exitTime: String,
    val parkingSpotId: String? = null
)