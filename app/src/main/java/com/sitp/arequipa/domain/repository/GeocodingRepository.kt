package com.sitp.arequipa.domain.repository

interface GeocodingRepository {
    suspend fun coordenadasANombre(lat: Double, lng: Double): String
}