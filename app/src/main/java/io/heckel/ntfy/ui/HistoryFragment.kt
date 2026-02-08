package io.heckel.ntfy.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import io.heckel.ntfy.R
import io.heckel.ntfy.util.HttpUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import java.io.IOException

class HistoryFragment : Fragment(R.layout.fragment_history) {
    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.history_recycler)
        swipeRefreshLayout = view.findViewById(R.id.history_swipe_refresh)

        historyAdapter = HistoryAdapter()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = historyAdapter

        swipeRefreshLayout.setOnRefreshListener {
            fetchReports()
        }

        swipeRefreshLayout.isRefreshing = true
        fetchReports()
    }

    private fun fetchReports() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val client = HttpUtil.defaultClient(requireContext(), REPORTS_URL)
                val reports = executeRequest(client, REPORTS_URL)
                withContext(Dispatchers.Main) {
                    historyAdapter.updateData(reports)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    historyAdapter.updateData(emptyList())
                }
            } finally {
                withContext(Dispatchers.Main) {
                    swipeRefreshLayout.isRefreshing = false
                }
            }
        }
    }

    private fun executeRequest(client: OkHttpClient, url: String): List<QuakeReport> {
        val request = HttpUtil.requestBuilder(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected response: ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            return parseReports(body)
        }
    }

    private fun parseReports(jsonBody: String): List<QuakeReport> {
        val jsonArray = JSONArray(jsonBody)
        val reports = mutableListOf<QuakeReport>()
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)
            reports.add(
                QuakeReport(
                    id = item.optInt("id"),
                    station_id = item.optString("station_id"),
                    lokasi = item.optString("lokasi"),
                    waktu_kejadian = item.optString("waktu_kejadian"),
                    durasi = item.optDouble("durasi"),
                    pga_maks = item.optString("pga_maks"),
                    intensitas_maks = item.optString("intensitas_maks"),
                    deskripsi = item.optString("deskripsi"),
                    latitude = item.optDouble("latitude"),
                    longitude = item.optDouble("longitude")
                )
            )
        }
        return reports
    }

    companion object {
        private const val REPORTS_URL = "https://quakealert.bananapixel.my.id/laporan"
    }
}
