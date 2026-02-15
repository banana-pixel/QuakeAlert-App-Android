package io.heckel.ntfy.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.heckel.ntfy.R
import io.heckel.ntfy.msg.Sensor
import okhttp3.*
import java.io.IOException

class SensorsFragment : Fragment(R.layout.fragment_sensors) {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val adapter = SensorsAdapter()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private lateinit var healthDot: View
    private lateinit var healthStatus: TextView
    private lateinit var appLatency: TextView

    private val refreshRunnable = object : Runnable {
        override fun run() {
            fetchData()
            handler.postDelayed(this, 3000)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        swipeRefreshLayout = view.findViewById(R.id.sensors_swipe_refresh)
        val recyclerView: RecyclerView = view.findViewById(R.id.sensors_recycler_view)
        recyclerView.adapter = adapter

        healthDot = view.findViewById(R.id.view_health_dot)
        healthStatus = view.findViewById(R.id.tv_health_status)
        appLatency = view.findViewById(R.id.tv_app_latency)

        swipeRefreshLayout.setOnRefreshListener {
            fetchData()
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }

    private fun fetchData() {
        val startTime = System.currentTimeMillis()

        // Only start animation if we are actually refreshing manually or it's the first load
        // (Optional optimization: avoids flickering on auto-refresh)
        if (swipeRefreshLayout.isRefreshing) {
            activity?.runOnUiThread { startPulseAnimation(healthDot) }
        }

        val request = Request.Builder()
            .url("https://quakealert.bananapixel.my.id/stations") // Your URL
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
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
                                if (!isAdded) return@runOnUiThread // Crash prevention

                                stopPulseAnimation(healthDot)
                                updateHealthBar(true, latency)

                                // --- THE FIX IS HERE ---
                                adapter.submitList(stations) {
                                    // This block runs AFTER the list is updated.
                                    // We force the list to redraw immediately.
                                    // This triggers 'onBindViewHolder' again, which recalculates
                                    // "System.currentTimeMillis() - lastPing"
                                    adapter.notifyDataSetChanged()
                                }

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

    // Simple helper to clean up your code
    private fun handleError(startTime: Long) {
        val latency = System.currentTimeMillis() - startTime
        activity?.runOnUiThread {
            if (isAdded) { // Ensure fragment is still attached to avoid crashes
                stopPulseAnimation(healthDot)
                updateHealthBar(false, latency)
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun startPulseAnimation(view: View) {
        val animation = AlphaAnimation(0.3f, 1.0f).apply {
            duration = 600
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }
        view.startAnimation(animation)
    }

    private fun stopPulseAnimation(view: View) {
        view.clearAnimation()
    }

    private fun updateHealthBar(isSuccess: Boolean, latencyMs: Long) {
        appLatency.text = "${latencyMs} ms"

        if (!isSuccess) {
            healthStatus.text = "Server Offline"
            healthDot.background = ContextCompat.getDrawable(requireContext(), R.drawable.shape_dot_red)
            return
        }

        healthStatus.text = "Server Healthy"
        when {
            latencyMs < 200 -> {
                healthDot.background = ContextCompat.getDrawable(requireContext(), R.drawable.shape_dot_green)
            }
            latencyMs in 200..500 -> {
                healthDot.background = ContextCompat.getDrawable(requireContext(), R.drawable.shape_dot_yellow)
            }
            else -> {
                healthDot.background = ContextCompat.getDrawable(requireContext(), R.drawable.shape_dot_red)
            }
        }
    }
}
