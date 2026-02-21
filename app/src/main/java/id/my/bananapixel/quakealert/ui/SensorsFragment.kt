package id.my.bananapixel.quakealert.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import id.my.bananapixel.quakealert.R
import id.my.bananapixel.quakealert.domain.ServerHealthStatus
import id.my.bananapixel.quakealert.msg.Sensor
import okhttp3.*
import java.io.IOException

class SensorsFragment : Fragment(R.layout.fragment_sensors) {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val adapter = SensorsAdapter()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    // UI Elements
    private lateinit var healthBar: ConstraintLayout
    private lateinit var healthDot: View
    private lateinit var healthStatus: TextView
    private lateinit var appLatency: TextView
    private lateinit var errorContainer: View
    private lateinit var emptyContainer: View

    /** Only show CONNECTING (blue) on first load or pull-to-refresh; keep current state during background polls. */
    private var hasReceivedFirstResult = false

    private val refreshRunnable = object : Runnable {
        override fun run() {
            fetchData()
            handler.postDelayed(this, 3000)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        swipeRefreshLayout = view.findViewById(R.id.sensors_swipe_refresh)

        // FIX: Change R.id.sensors_recycler_view to R.id.recycler_view
        val recyclerView: RecyclerView = view.findViewById(R.id.recycler_view)
        recyclerView.adapter = adapter

        // Initialize Views
        healthBar = view.findViewById(R.id.health_bar)
        healthDot = view.findViewById(R.id.view_health_dot)
        healthStatus = view.findViewById(R.id.tv_health_status)
        appLatency = view.findViewById(R.id.tv_app_latency)
        errorContainer = view.findViewById(R.id.sensors_error_container)
        emptyContainer = view.findViewById(R.id.sensors_empty_container)

        // Start in CONNECTING state so we never show default "Server Healthy" + blue
        // before the first fetch completes (avoids wrong state when offline on first open).
        updateHealthStatus(ServerHealthStatus.CONNECTING, 0)

        swipeRefreshLayout.setOnRefreshListener {
            fetchData()
        }
    }

    override fun onResume() {
        super.onResume()
        warmupThenStartPolling()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }

    /**
     * Run one silent request to establish the connection (DNS, TCP, TLS), then start the 3s polling.
     * The first fetch that updates the UI will then reuse the warm connection, so the shown ping is low.
     */
    private fun warmupThenStartPolling() {
        val ctx = context ?: return
        val baseUrl = ctx.getString(R.string.app_base_url).trimEnd('/')
        val request = Request.Builder().url("$baseUrl/stations").build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    if (isAdded) handler.post(refreshRunnable)
                }
            }
            override fun onResponse(call: Call, response: Response) {
                response.close()
                activity?.runOnUiThread {
                    if (isAdded) handler.post(refreshRunnable)
                }
            }
        })
    }

    private fun fetchData() {
        val startTime = System.currentTimeMillis()

        activity?.runOnUiThread {
            if (isAdded) {
                errorContainer.visibility = View.GONE
                // Show CONNECTING only on first load or user pull-to-refresh; avoid blinking to blue every 3s poll.
                if (!hasReceivedFirstResult || swipeRefreshLayout.isRefreshing) {
                    updateHealthStatus(ServerHealthStatus.CONNECTING, 0)
                }
            }
        }

        val ctx = context ?: return
        val baseUrl = ctx.getString(R.string.app_base_url).trimEnd('/')
        val request = Request.Builder()
            .url("$baseUrl/stations")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Network Fail -> CRITICAL (Red 3D Bar)
                handleError(startTime)
            }

            override fun onResponse(call: Call, response: Response) {
                val latency = System.currentTimeMillis() - startTime
                response.use {
                    if (!response.isSuccessful) {
                        handleError(startTime)
                        return
                    }

                    val bodyString = response.body?.string()

                    if (bodyString != null && bodyString.trim().startsWith("[")) {
                        try {
                            val type = object : TypeToken<List<Sensor>>() {}.type
                            val stations: List<Sensor> = gson.fromJson(bodyString, type)

                            activity?.runOnUiThread {
                                if (!isAdded) return@runOnUiThread

                                hasReceivedFirstResult = true
                                val status = if (latency > 300) ServerHealthStatus.WARNING else ServerHealthStatus.HEALTHY
                                updateHealthStatus(status, latency.toInt())
                                adapter.submitList(stations) {
                                    adapter.notifyDataSetChanged()
                                }
                                errorContainer.visibility = View.GONE
                                emptyContainer.visibility = if (stations.isEmpty()) View.VISIBLE else View.GONE
                                swipeRefreshLayout.isRefreshing = false
                            }
                        } catch (e: Exception) {
                            handleError(startTime)
                        }
                    } else {
                        handleError(startTime)
                    }
                }
            }
        })
    }

    private fun handleError(startTime: Long) {
        val latency = System.currentTimeMillis() - startTime
        activity?.runOnUiThread {
            if (isAdded) {
                hasReceivedFirstResult = true
                updateHealthStatus(ServerHealthStatus.CRITICAL, latency.toInt())
                errorContainer.visibility = View.VISIBLE
                emptyContainer.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    /**
     * THE NEW 3D UI LOGIC
     * Updates the background resource and text colors based on the state.
     * Strings and colors are applied only at this UI boundary.
     */
    private fun updateHealthStatus(status: ServerHealthStatus, latency: Int) {
        // Latency: only show when we have a valid ping; hide when offline/connecting
        val showLatency = status.isConnectionHealthy
        appLatency.visibility = if (showLatency) View.VISIBLE else View.GONE
        if (showLatency) appLatency.text = "$latency ms"

        when (status) {
            ServerHealthStatus.HEALTHY -> {
                // GREEN STYLE
                healthBar.setBackgroundResource(R.drawable.bg_pill_3d_green2)
                healthStatus.text = "Server Healthy"
                healthStatus.setTextColor(Color.WHITE)
                healthDot.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
                appLatency.setTextColor(Color.parseColor("#E8F5E9")) // Very Light Green
            }
            ServerHealthStatus.CONNECTING -> {
                // BLUE STYLE (Connecting)
                healthBar.setBackgroundResource(R.drawable.bg_pill_3d_blue)
                healthStatus.text = "Connecting..."
                healthStatus.setTextColor(Color.WHITE)
                healthDot.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
                appLatency.setTextColor(Color.parseColor("#E1F5FE")) // Very Light Blue
            }
            ServerHealthStatus.WARNING -> {
                // ORANGE STYLE (High Latency)
                healthBar.setBackgroundResource(R.drawable.bg_pill_3d_orange)
                healthStatus.text = "Unstable Connection"
                healthStatus.setTextColor(Color.parseColor("#FFF3E0")) // Very light orange
                healthDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFF3E0"))
                appLatency.setTextColor(Color.parseColor("#FFF3E0"))
            }
            ServerHealthStatus.CRITICAL -> {
                // RED STYLE (Offline / Error)
                healthBar.setBackgroundResource(R.drawable.bg_pill_3d_red)
                healthStatus.text = "Server Offline"
                healthStatus.setTextColor(Color.WHITE)
                healthDot.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
                appLatency.setTextColor(Color.parseColor("#FFEBEE")) // Very light red
            }
        }
    }
}