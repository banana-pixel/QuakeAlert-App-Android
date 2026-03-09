package id.my.bananapixel.quakealert.api

import id.my.bananapixel.quakealert.msg.Sensor
import id.my.bananapixel.quakealert.ui.QuakeReport
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit API for the Quake Alert backend.
 */
interface QuakeAlertApi {

    @GET("laporan")
    suspend fun getLaporan(
        @Query("page") page: Int? = null
    ): List<QuakeReport>

    @GET("stations")
    suspend fun getStations(): List<Sensor>
}
