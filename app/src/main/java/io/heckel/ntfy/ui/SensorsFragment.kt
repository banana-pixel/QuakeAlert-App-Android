package io.heckel.ntfy.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
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

    private val refreshRunnable = object : Runnable {
        override fun run() {
            fetchData()
            handler.postDelayed(this, 30000)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        swipeRefreshLayout = view.findViewById(R.id.sensors_swipe_refresh)
        val recyclerView: RecyclerView = view.findViewById(R.id.sensors_recycler_view)
        recyclerView.adapter = adapter

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
        val request = Request.Builder()
            .url("https://quakealert.bananapixel.my.id/stations")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    swipeRefreshLayout.isRefreshing = false
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        activity?.runOnUiThread {
                            swipeRefreshLayout.isRefreshing = false
                        }
                        return
                    }

                    val body = response.body?.string()
                    if (body != null) {
                        val type = object : TypeToken<List<Sensor>>() {}.type
                        val stations: List<Sensor> = gson.fromJson(body, type)
                        activity?.runOnUiThread {
                            adapter.submitList(stations)
                            swipeRefreshLayout.isRefreshing = false
                        }
                    } else {
                        activity?.runOnUiThread {
                            swipeRefreshLayout.isRefreshing = false
                        }
                    }
                }
            }
        })
    }
}
