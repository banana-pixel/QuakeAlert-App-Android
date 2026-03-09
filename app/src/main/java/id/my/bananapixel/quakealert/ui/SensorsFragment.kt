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
import id.my.bananapixel.quakealert.BuildConfig
import id.my.bananapixel.quakealert.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import id.my.bananapixel.quakealert.domain.ServerHealthStatus
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class SensorsFragment : Fragment(R.layout.fragment_sensors) {
    private val viewModel: SensorsViewModel by viewModel()
    private val adapter = SensorsAdapter()
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    // UI Elements
    private lateinit var healthBar: ConstraintLayout
    private lateinit var healthDot: View
    private lateinit var healthStatus: TextView
    private lateinit var appLatency: TextView
    private lateinit var errorContainer: View
    private lateinit var emptyContainer: View



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

        swipeRefreshLayout.setOnRefreshListener {
            viewModel.refresh()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateHealthStatus(state.status, state.latency)
                    adapter.submitList(state.stations) {
                        adapter.notifyDataSetChanged()
                    }
                    if (state.isError) {
                        errorContainer.visibility = View.VISIBLE
                        emptyContainer.visibility = View.GONE
                    } else {
                        errorContainer.visibility = View.GONE
                        emptyContainer.visibility = if (state.isEmpty) View.VISIBLE else View.GONE
                    }
                    if (swipeRefreshLayout.isRefreshing && state.hasReceivedFirstResult) {
                        swipeRefreshLayout.isRefreshing = false
                    }
                }
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
        if (showLatency) appLatency.text = getString(R.string.sensors_latency_format, latency)

        when (status) {
            ServerHealthStatus.HEALTHY -> {
                // GREEN STYLE
                healthBar.setBackgroundResource(R.drawable.bg_pill_3d_green2)
                healthStatus.text = getString(R.string.sensors_status_healthy)
                healthStatus.setTextColor(Color.WHITE)
                healthDot.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
                appLatency.setTextColor(Color.parseColor("#E8F5E9")) // Very Light Green
            }
            ServerHealthStatus.CONNECTING -> {
                // BLUE STYLE (Connecting)
                healthBar.setBackgroundResource(R.drawable.bg_pill_3d_blue)
                healthStatus.text = getString(R.string.sensors_status_connecting)
                healthStatus.setTextColor(Color.WHITE)
                healthDot.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
                appLatency.setTextColor(Color.parseColor("#E1F5FE")) // Very Light Blue
            }
            ServerHealthStatus.WARNING -> {
                // ORANGE STYLE (High Latency)
                healthBar.setBackgroundResource(R.drawable.bg_pill_3d_orange)
                healthStatus.text = getString(R.string.sensors_status_unstable)
                healthStatus.setTextColor(Color.parseColor("#FFF3E0")) // Very light orange
                healthDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFF3E0"))
                appLatency.setTextColor(Color.parseColor("#FFF3E0"))
            }
            ServerHealthStatus.CRITICAL -> {
                // RED STYLE (Offline / Error)
                healthBar.setBackgroundResource(R.drawable.bg_pill_3d_red)
                healthStatus.text = getString(R.string.sensors_status_offline)
                healthStatus.setTextColor(Color.WHITE)
                healthDot.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
                appLatency.setTextColor(Color.parseColor("#FFEBEE")) // Very light red
            }
        }
    }
}