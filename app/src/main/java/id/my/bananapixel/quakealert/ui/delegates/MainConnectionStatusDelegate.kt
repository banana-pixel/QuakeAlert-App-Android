package id.my.bananapixel.quakealert.ui.delegates

import android.graphics.Color
import android.content.res.ColorStateList
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.LifecycleOwner
import id.my.bananapixel.quakealert.R
import id.my.bananapixel.quakealert.db.ConnectionDetails
import id.my.bananapixel.quakealert.db.ConnectionState
import id.my.bananapixel.quakealert.db.Repository

/**
 * Handles connection status: observes repository connection details, updates the 3D health bar
 * and the connection-error menu item visibility.
 */
class MainConnectionStatusDelegate(
    private val activity: androidx.appcompat.app.AppCompatActivity,
    private val repository: Repository
) {
    private var menu: android.view.Menu? = null

    fun setMenu(menu: android.view.Menu?) {
        this.menu = menu
    }

    /**
     * Clears references when Activity is destroyed to prevent memory leaks.
     * Call from Activity.onDestroy().
     */
    fun clear() {
        menu = null
    }

    fun observeConnectionDetails(lifecycleOwner: LifecycleOwner) {
        repository.getConnectionDetailsLiveData().observe(lifecycleOwner) { details ->
            showHideConnectionErrorMenuItem(details)
            val hasError = details.values.any { it.hasError() }
            val isConnecting = details.values.any { it.state == ConnectionState.CONNECTING }
            val status = when {
                hasError -> "CRITICAL"
                isConnecting -> "CONNECTING"
                details.isEmpty() -> "CONNECTING"
                else -> "HEALTHY"
            }
            val latency = if (status == "HEALTHY") {
                details.values
                    .filter { it.state == ConnectionState.CONNECTED }
                    .mapNotNull { it.latencyMs }
                    .maxOrNull() ?: 0
            } else 0
            updateHealthStatus(status, latency)
        }
    }

    fun showHideConnectionErrorMenuItem(details: Map<String, ConnectionDetails>) {
        if (activity.isDestroyed) return
        val m = menu ?: return
        activity.runOnUiThread {
            if (activity.isDestroyed) return@runOnUiThread
            val connectionErrorItem = m.findItem(R.id.main_menu_connection_error)
            val hasErrors = details.values.any { it.hasError() }
            connectionErrorItem?.isVisible = hasErrors
        }
    }

    fun updateHealthStatus(status: String, latency: Int) {
        if (activity.isDestroyed) return
        val healthBar = activity.findViewById<ConstraintLayout>(R.id.health_bar) ?: return
        val tvHealthStatus = activity.findViewById<TextView>(R.id.tv_health_status) ?: return
        val tvAppLatency = activity.findViewById<TextView>(R.id.tv_app_latency) ?: return
        val viewHealthDot = activity.findViewById<View>(R.id.view_health_dot) ?: return

        val showLatency = status == "HEALTHY" || status == "WARNING"
        tvAppLatency.visibility = if (showLatency) View.VISIBLE else View.GONE
        if (showLatency) tvAppLatency.text = activity.getString(R.string.sensors_latency_format, latency)

        when (status) {
            "HEALTHY" -> {
                healthBar.setBackgroundResource(R.drawable.bg_pill_3d_green2)
                tvHealthStatus.text = activity.getString(R.string.sensors_status_healthy)
                tvHealthStatus.setTextColor(Color.WHITE)
                viewHealthDot.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
                tvAppLatency.setTextColor(Color.parseColor("#E8F5E9"))
            }
            "CONNECTING" -> {
                healthBar.setBackgroundResource(R.drawable.bg_pill_3d_blue)
                tvHealthStatus.text = activity.getString(R.string.sensors_status_connecting)
                tvHealthStatus.setTextColor(Color.WHITE)
                viewHealthDot.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
                tvAppLatency.setTextColor(Color.parseColor("#E1F5FE"))
            }
            "WARNING" -> {
                healthBar.setBackgroundResource(R.drawable.bg_pill_3d_orange)
                tvHealthStatus.text = activity.getString(R.string.sensors_status_unstable)
                tvHealthStatus.setTextColor(Color.parseColor("#FFF3E0"))
                viewHealthDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFF3E0"))
                tvAppLatency.setTextColor(Color.parseColor("#FFF3E0"))
            }
            "CRITICAL" -> {
                healthBar.setBackgroundResource(R.drawable.bg_pill_3d_red)
                tvHealthStatus.text = activity.getString(R.string.sensors_status_offline)
                tvHealthStatus.setTextColor(Color.WHITE)
                viewHealthDot.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
                tvAppLatency.setTextColor(Color.parseColor("#FFEBEE"))
            }
        }
    }
}
