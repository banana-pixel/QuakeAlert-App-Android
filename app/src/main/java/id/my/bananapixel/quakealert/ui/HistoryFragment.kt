package id.my.bananapixel.quakealert.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
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
    private val viewModel by viewModels<SubscriptionsViewModel> {
        SubscriptionsViewModelFactory((requireActivity().application as Application).repository)
    }

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

        historyAdapter = HistoryAdapter()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = historyAdapter

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