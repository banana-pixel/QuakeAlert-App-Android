package id.my.bananapixel.quakealert.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.button.MaterialButton
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import android.content.res.ColorStateList
import android.graphics.Color
import id.my.bananapixel.quakealert.R
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class HistoryFragment : Fragment(R.layout.fragment_history) {
    // Use Koin to inject QuakeHistoryViewModel (modern approach)
    private val viewModel: QuakeHistoryViewModel by viewModel()

    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var errorContainer: View
    private lateinit var errorMessage: TextView
    private lateinit var errorRetry: MaterialButton
    private lateinit var emptyContainer: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view)
        swipeRefreshLayout = view.findViewById(R.id.history_swipe_refresh)
        errorContainer = view.findViewById(R.id.history_error_container)
        errorMessage = view.findViewById(R.id.history_error_message)
        errorRetry = view.findViewById(R.id.history_error_retry)
        emptyContainer = view.findViewById(R.id.history_empty_container)
        val btnFilterAll = view.findViewById<TextView>(R.id.btn_filter_all)
        val btnFilterNearby = view.findViewById<TextView>(R.id.btn_filter_nearby)

        historyAdapter = HistoryAdapter()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = historyAdapter

        btnFilterAll.setOnClickListener { viewModel.setNearbyFilter(false) }
        btnFilterNearby.setOnClickListener { viewModel.setNearbyFilter(true) }

        viewLifecycleOwner.lifecycleScope.launch {
            combine(viewModel.isNearbyFilterActive, viewModel.currentAlertRadius) { active, radius ->
                active to radius
            }.collectLatest { (active, radius) ->
                btnFilterNearby.text = "Dekat ${radius}km"
                if (active) {
                    btnFilterNearby.setBackgroundResource(R.drawable.bg_pill_3d_red)
                    btnFilterNearby.setTextColor(Color.WHITE)
                    btnFilterNearby.compoundDrawableTintList = ColorStateList.valueOf(Color.WHITE)
                    
                    btnFilterAll.setBackgroundResource(R.drawable.bg_pill_3d_white)
                    btnFilterAll.setTextColor(Color.BLACK)
                } else {
                    btnFilterAll.setBackgroundResource(R.drawable.bg_pill_3d_blue)
                    btnFilterAll.setTextColor(Color.WHITE)
                    
                    btnFilterNearby.setBackgroundResource(R.drawable.bg_pill_3d_white)
                    btnFilterNearby.setTextColor(Color.BLACK)
                    btnFilterNearby.compoundDrawableTintList = ColorStateList.valueOf(Color.BLACK)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.quakes.collectLatest { quakeList ->
                historyAdapter.updateData(quakeList)
                emptyContainer.visibility = if (quakeList.isEmpty() && errorContainer.visibility != View.VISIBLE)
                    View.VISIBLE else View.GONE
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.quakeLoadState.collectLatest { state ->
                swipeRefreshLayout.isRefreshing = (state == QuakeLoadState.Loading)
                when (state) {
                    is QuakeLoadState.Error -> {
                        errorMessage.text = state.message
                        errorContainer.visibility = View.VISIBLE
                        emptyContainer.visibility = View.GONE
                    }
                    QuakeLoadState.Success, QuakeLoadState.Idle -> {
                        errorContainer.visibility = View.GONE
                        emptyContainer.visibility = if (historyAdapter.itemCount == 0) View.VISIBLE else View.GONE
                    }
                    QuakeLoadState.Loading -> { /* refresh indicator already set */ }
                }
            }
        }

        swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshQuakes(requireContext())
        }

        errorRetry.setOnClickListener {
            viewModel.refreshQuakes(requireContext())
        }

        viewModel.refreshQuakes(requireContext())
    }
}