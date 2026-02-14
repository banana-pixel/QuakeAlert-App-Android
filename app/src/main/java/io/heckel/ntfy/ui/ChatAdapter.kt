package io.heckel.ntfy.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.heckel.ntfy.databinding.ItemChatMeBinding
import io.heckel.ntfy.databinding.ItemChatOtherBinding
import io.heckel.ntfy.msg.ChatMessage

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
        if (holder is MyMessageViewHolder) holder.bind(message)
        else if (holder is OtherMessageViewHolder) holder.bind(message)
    }

    class MyMessageViewHolder(private val binding: ItemChatMeBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMessage) {
            binding.chatMessageText.text = message.message
        }
    }

    class OtherMessageViewHolder(private val binding: ItemChatOtherBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMessage) {
            binding.chatMessageText.text = message.message
        }
    }
}
