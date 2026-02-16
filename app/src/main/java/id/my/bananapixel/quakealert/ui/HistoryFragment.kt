package id.my.bananapixel.quakealert.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import id.my.bananapixel.quakealert.R
import id.my.bananapixel.quakealert.app.Application
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HistoryFragment : Fragment(R.layout.fragment_history) {
    // Link to the shared ViewModel
    private val viewModel by viewModels<SubscriptionsViewModel> {
        SubscriptionsViewModelFactory((requireActivity().application as Application).repository)
    }

    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view)
        swipeRefreshLayout = view.findViewById(R.id.history_swipe_refresh)

        historyAdapter = HistoryAdapter()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = historyAdapter

        // 1. Observe the Room Database (Offline-First Logic)
        // This Flow emits cached data instantly even when offline.
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.quakes.collectLatest { quakeList ->
                // This will now match the expected type
                historyAdapter.updateData(quakeList)
                swipeRefreshLayout.isRefreshing = false
            }
        }

        // 2. Swipe to Refresh now triggers the Repository's fetch
        // 1. Update the Swipe to Refresh listener
        swipeRefreshLayout.setOnRefreshListener {
            // Pass requireContext() to the ViewModel
            viewModel.refreshQuakes(requireContext())
        }

// 2. Update the initial fetch call
        viewModel.refreshQuakes(requireContext())
    }
}