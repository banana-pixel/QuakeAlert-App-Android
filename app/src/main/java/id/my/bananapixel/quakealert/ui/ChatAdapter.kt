package id.my.bananapixel.quakealert.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.my.bananapixel.quakealert.databinding.ItemChatMeBinding
import id.my.bananapixel.quakealert.databinding.ItemChatOtherBinding
// REMOVE the .msg import and ONLY keep the .db one
import id.my.bananapixel.quakealert.db.ChatMessage
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter(private val myDeviceId: String) : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(DiffCallback) {

    companion object {
        private const val VIEW_TYPE_ME = 1
        private const val VIEW_TYPE_OTHER = 2

        // Fixed the DiffCallback reference
        private object DiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
            override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
                oldItem.timestamp == newItem.timestamp && oldItem.senderId == newItem.senderId
            override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
                oldItem == newItem
        }
    }

    // Inside ChatAdapter.kt

    private fun formatChatTime(timestamp: Long): String {
        // FIX: Multiply by 1000L to convert seconds to milliseconds
        // The 'L' forces it to be a Long calculation so it doesn't overflow
        val date = Date(timestamp * 1000L)

        val pattern = "d MMM yyyy, HH:mm"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(date)
    }

    override fun getItemViewType(position: Int): Int {
        // Use myDeviceId here to distinguish messages
        return if (getItem(position).senderId == myDeviceId) VIEW_TYPE_ME else VIEW_TYPE_OTHER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_ME) {
            MyMessageViewHolder(ItemChatMeBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        } else {
            OtherMessageViewHolder(ItemChatOtherBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        val formattedTime = formatChatTime(message.timestamp)

        when (holder) {
            is MyMessageViewHolder -> holder.bind(message, formattedTime)
            is OtherMessageViewHolder -> holder.bind(message, formattedTime)
        }
    }

    class MyMessageViewHolder(private val binding: ItemChatMeBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMessage, formattedTime: String) {
            binding.chatMessageText.text = message.message
            binding.chatTimeText.text = formattedTime
        }
    }

    class OtherMessageViewHolder(private val binding: ItemChatOtherBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMessage, formattedTime: String) {
            binding.chatMessageText.text = message.message
            binding.chatTimeText.text = formattedTime
        }
    }
}