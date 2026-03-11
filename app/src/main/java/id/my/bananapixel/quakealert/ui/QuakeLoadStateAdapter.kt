
package id.my.bananapixel.quakealert.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.paging.LoadState
import androidx.paging.LoadStateAdapter
import androidx.recyclerview.widget.RecyclerView
import id.my.bananapixel.quakealert.R
import id.my.bananapixel.quakealert.domain.AppError
import java.io.IOException

class QuakeLoadStateAdapter(private val retry: () -> Unit) :
    LoadStateAdapter<QuakeLoadStateAdapter.LoadStateViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, loadState: LoadState): LoadStateViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_load_state, parent, false)
        return LoadStateViewHolder(view, retry)
    }

    override fun onBindViewHolder(holder: LoadStateViewHolder, loadState: LoadState) {
        holder.bind(loadState)
    }

    class LoadStateViewHolder(view: View, retry: () -> Unit) : RecyclerView.ViewHolder(view) {
        private val progressBar: ProgressBar = view.findViewById(R.id.load_state_progress)
        private val errorIcon: ImageView = view.findViewById(R.id.load_state_error_icon)
        private val errorMsg: TextView = view.findViewById(R.id.load_state_error_msg)
        private val retryButton: Button = view.findViewById(R.id.load_state_retry_button)

        init {
            retryButton.setOnClickListener { retry.invoke() }
        }

        fun bind(loadState: LoadState) {
            val isError = loadState is LoadState.Error
            progressBar.isVisible = loadState is LoadState.Loading
            errorIcon.isVisible = isError
            errorMsg.isVisible = isError
            retryButton.isVisible = isError

            if (loadState is LoadState.Error) {
                errorMsg.text = loadState.error.toUserMessage(itemView.context)
            }
        }
    }

    companion object {

        /**
         * Converts a paging [Throwable] into a user-friendly string.
         *
         * The mapper layer intentionally throws [IllegalArgumentException] for malformed
         * EWS data, and the RemoteMediator wraps these in the paging error channel.
         * This function is the UI boundary where technical exception messages are
         * translated into human-readable text.
         */
        fun Throwable.toUserMessage(context: Context): String = when (this) {
            // AppError types surfaced by the data layer
            is AppError.NetworkError ->
                context.getString(R.string.error_quake_network)
            is AppError.ParseError ->
                context.getString(R.string.error_quake_parse)
            is AppError.ValidationError ->
                context.getString(R.string.error_quake_validation)

            // Raw exception types that may arrive from Retrofit / OkHttp / the mapper
            is IOException ->
                context.getString(R.string.error_quake_network)
            is IllegalArgumentException ->
                context.getString(R.string.error_quake_validation)

            // Catch-all: show a generic message but never expose a raw stack trace
            else ->
                context.getString(R.string.error_generic_message)
        }
    }
}

