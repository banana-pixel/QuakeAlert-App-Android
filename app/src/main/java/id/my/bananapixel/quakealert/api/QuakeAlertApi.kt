package id.my.bananapixel.quakealert.api

import android.content.Context
import id.my.bananapixel.quakealert.R
import id.my.bananapixel.quakealert.util.HttpUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit API for the Quake Alert backend.
 * Returns raw JSON string; parsing is done via [id.my.bananapixel.quakealert.db.QuakeReportParser].
 */
interface QuakeAlertApi {

    @GET("laporan")
    suspend fun getLaporan(
        @Query("page") page: Int? = null
    ): String

    companion object {
        /**
         * Creates an API instance using the app's base URL and TLS/cert config from [HttpUtil].
         * Must be called from a coroutine (uses [HttpUtil.defaultClient]).
         */
        suspend fun create(context: Context, baseUrl: String): QuakeAlertApi = withContext(Dispatchers.IO) {
            val normalizedBase = baseUrl.trimEnd('/') + "/"
            val baseClient = HttpUtil.defaultClient(context, normalizedBase)
            val client = baseClient.newBuilder()
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .addHeader("User-Agent", HttpUtil.USER_AGENT)
                            .build()
                    )
                }
                .build()
            Retrofit.Builder()
                .baseUrl(normalizedBase)
                .client(client)
                .addConverterFactory(ScalarsConverterFactory.create())
                .build()
                .create(QuakeAlertApi::class.java)
        }

        /**
         * Convenience: create API using [context.getString] for base URL.
         */
        suspend fun create(context: Context): QuakeAlertApi {
            val baseUrl = context.getString(R.string.app_base_url).trimEnd('/')
            return create(context, baseUrl)
        }
    }
}
