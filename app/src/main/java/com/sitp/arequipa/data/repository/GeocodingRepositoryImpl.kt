package com.sitp.arequipa.data.repository

import com.sitp.arequipa.data.remote.GeocodingSource
import com.sitp.arequipa.domain.repository.GeocodingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeocodingRepositoryImpl(
    private val geocodingSource: GeocodingSource = GeocodingSource()
) : GeocodingRepository {

    override suspend fun coordenadasANombre(lat: Double, lng: Double): String =
        withContext(Dispatchers.IO) { geocodingSource.coordenadasANombre(lat, lng) }
}