package id.my.bananapixel.quakealert.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.my.bananapixel.quakealert.R
import id.my.bananapixel.quakealert.db.ChatMessage
import id.my.bananapixel.quakealert.ui.components.ChatMessageBubbleView
import java.text.SimpleDateFormat
import java.util.*

/**
 * Enhanced chat adapter inspired by Telegram's message rendering patterns.
 * Features:
 * - Custom bubble views with tails
 * - Smooth animations
 * - Better message grouping
 * - Optimized view recycling
 */
class EnhancedChatAdapter(
    private val myDeviceId: String
) : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(DiffCallback) {

    companion object {
        private const val VIEW_TYPE_ME = 1
        private const val VIEW_TYPE_OTHER = 2

        private object DiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
            override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
                return oldItem.timestamp == newItem.timestamp && 
                       oldItem.senderId == newItem.senderId
            }

            override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).senderId == myDeviceId) {
            VIEW_TYPE_ME
        } else {
            VIEW_TYPE_OTHER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_ME -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_me_enhanced, parent, false)
                OutgoingMessageViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_other_enhanced, parent, false)
                IncomingMessageViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        val formattedTime = formatChatTime(message.timestamp)

        when (holder) {
            is OutgoingMessageViewHolder -> holder.bind(message, formattedTime)
            is IncomingMessageViewHolder -> holder.bind(message, formattedTime)
        }
    }

    private fun formatChatTime(timestamp: Long): String {
        val date = Date(timestamp * 1000L)
        val now = Date()
        val diff = now.time - date.time
        
        return when {
            diff < 86400000 -> { // Less than 24 hours
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
            }
            diff < 604800000 -> { // Less than 7 days
                SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(date)
            }
            else -> {
                SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(date)
            }
        }
    }

    /**
     * ViewHolder for outgoing messages (from current user)
     */
    class OutgoingMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageBubble: ChatMessageBubbleView = 
            itemView.findViewById(R.id.message_bubble)

        fun bind(message: ChatMessage, formattedTime: String) {
            messageBubble.setMessage(
                text = message.message,
                timestamp = formattedTime,
                isOutgoing = true
            )
            
            // Optional: Add animation for newly added messages
            animateIfNew()
        }

        private fun animateIfNew() {
            if (bindingAdapterPosition == itemView.parent?.let { 
                (it as? RecyclerView)?.adapter?.itemCount?.minus(1) 
            }) {
                itemView.alpha = 0f
                itemView.translationX = 100f
                itemView.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(200)
                    .start()
            }
        }
    }

    /**
     * ViewHolder for incoming messages (from other users)
     */
    class IncomingMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageBubble: ChatMessageBubbleView = 
            itemView.findViewById(R.id.message_bubble)

        fun bind(message: ChatMessage, formattedTime: String) {
            messageBubble.setMessage(
                text = message.message,
                timestamp = formattedTime,
                isOutgoing = false
            )
            
            // Optional: Add animation for newly added messages
            animateIfNew()
        }

        private fun animateIfNew() {
            if (bindingAdapterPosition == itemView.parent?.let { 
                (it as? RecyclerView)?.adapter?.itemCount?.minus(1) 
            }) {
                itemView.alpha = 0f
                itemView.translationX = -100f
                itemView.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(200)
                    .start()
            }
        }
    }
}
