package io.heckel.ntfy.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.heckel.ntfy.databinding.ItemChatMeBinding
import io.heckel.ntfy.databinding.ItemChatOtherBinding
import io.heckel.ntfy.msg.ChatMessage
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter(private val currentUserId: String) : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(DiffCallback) {

    companion object {
        private const val VIEW_TYPE_ME = 1
        private const val VIEW_TYPE_OTHER = 2

        private object DiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
            override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
                oldItem.timestamp == newItem.timestamp && oldItem.senderId == newItem.senderId
            override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
                oldItem == newItem
        }
    }

    // Custom helper to format the time exactly as: 14 Feb 2026, 18:34
    private fun formatChatTime(timestamp: Long): String {
        // Convert seconds (from server) to milliseconds (for Android)
        val date = Date(timestamp * 1000)
        val pattern = "d MMM yyyy, HH:mm"
        val formatter = SimpleDateFormat(pattern, Locale.getDefault())
        return formatter.format(date)
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).senderId == currentUserId) VIEW_TYPE_ME else VIEW_TYPE_OTHER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_ME) {
            val binding = ItemChatMeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            MyMessageViewHolder(binding)
        } else {
            val binding = ItemChatOtherBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            OtherMessageViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        val formattedTime = formatChatTime(message.timestamp) // Process time here

        when (holder) {
            is MyMessageViewHolder -> holder.bind(message, formattedTime)
            is OtherMessageViewHolder -> holder.bind(message, formattedTime)
        }
    }

    class MyMessageViewHolder(private val binding: ItemChatMeBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMessage, formattedTime: String) {
            binding.chatMessageText.text = message.message
            binding.chatTimeText.text = formattedTime // Set the text
        }
    }

    class OtherMessageViewHolder(private val binding: ItemChatOtherBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMessage, formattedTime: String) {
            binding.chatMessageText.text = message.message
            binding.chatTimeText.text = formattedTime // Set the text
        }
    }
}