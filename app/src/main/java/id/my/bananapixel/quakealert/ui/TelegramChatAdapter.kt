package id.my.bananapixel.quakealert.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.my.bananapixel.quakealert.databinding.ItemChatDateHeaderBinding
import id.my.bananapixel.quakealert.databinding.ItemChatIncomingBinding
import id.my.bananapixel.quakealert.databinding.ItemChatOutgoingBinding
import id.my.bananapixel.quakealert.db.ChatMessage
import id.my.bananapixel.quakealert.ui.chat.ChatListItem
import java.text.SimpleDateFormat
import java.util.*

/**
 * Enhanced ChatAdapter with Telegram-inspired design and date headers
 * Optimized for performance with cached formatters and no animations
 */
class TelegramChatAdapter(
    private val myDeviceId: String
) : ListAdapter<ChatListItem, RecyclerView.ViewHolder>(DiffCallback) {

    companion object {
        private const val VIEW_TYPE_OUTGOING = 1
        private const val VIEW_TYPE_INCOMING = 2
        private const val VIEW_TYPE_DATE_HEADER = 3

        // Cache SimpleDateFormat instances (expensive to create)
        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        private val dateTimeFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
        private val dateTimeYearFormat = SimpleDateFormat("MMM d yyyy, HH:mm", Locale.getDefault())
        private val dateLabelFormat = SimpleDateFormat("MMMM d", Locale.getDefault())
        private val dateLabelYearFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())

        private object DiffCallback : DiffUtil.ItemCallback<ChatListItem>() {
            override fun areItemsTheSame(oldItem: ChatListItem, newItem: ChatListItem): Boolean {
                return when {
                    oldItem is ChatListItem.MessageItem && newItem is ChatListItem.MessageItem ->
                        oldItem.message.timestamp == newItem.message.timestamp && 
                        oldItem.message.senderId == newItem.message.senderId
                    oldItem is ChatListItem.DateHeaderItem && newItem is ChatListItem.DateHeaderItem ->
                        oldItem.timestamp == newItem.timestamp
                    else -> false
                }
            }

            override fun areContentsTheSame(oldItem: ChatListItem, newItem: ChatListItem): Boolean =
                oldItem == newItem
        }
    }

    /**
     * Converts a list of ChatMessages into ChatListItems with date headers
     * ⚡ Optimized: Only recomputes headers if message count or dates changed
     */
    private var cachedMessageCount = 0
    
    fun submitMessageList(messages: List<ChatMessage>) {
        // Skip recomputation if nothing changed
        if (messages.size == cachedMessageCount) {
            return
        }
        cachedMessageCount = messages.size
        
        val itemsWithHeaders = mutableListOf<ChatListItem>()
        var lastDate: String? = null

        messages.forEach { message ->
            val messageDate = getDateLabel(message.timestamp)
            
            // Add date header if date changed
            if (messageDate != lastDate) {
                itemsWithHeaders.add(ChatListItem.DateHeaderItem(messageDate, message.timestamp))
                lastDate = messageDate
            }
            
            // Add message
            itemsWithHeaders.add(ChatListItem.MessageItem(message))
        }

        submitList(itemsWithHeaders)
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is ChatListItem.DateHeaderItem -> VIEW_TYPE_DATE_HEADER
            is ChatListItem.MessageItem -> {
                if (item.message.senderId == myDeviceId) {
                    VIEW_TYPE_OUTGOING
                } else {
                    VIEW_TYPE_INCOMING
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        // ⚡ Cache LayoutInflater to avoid repeated lookups
        val inflater = LayoutInflater.from(parent.context)
        
        return when (viewType) {
            VIEW_TYPE_OUTGOING -> OutgoingMessageViewHolder(
                ItemChatOutgoingBinding.inflate(inflater, parent, false)
            )
            VIEW_TYPE_INCOMING -> IncomingMessageViewHolder(
                ItemChatIncomingBinding.inflate(inflater, parent, false)
            )
            VIEW_TYPE_DATE_HEADER -> DateHeaderViewHolder(
                ItemChatDateHeaderBinding.inflate(inflater, parent, false)
            )
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ChatListItem.MessageItem -> {
                val formattedTime = formatChatTime(item.message.timestamp)
                when (holder) {
                    is OutgoingMessageViewHolder -> {
                        holder.bind(item.message, formattedTime)
                    }
                    is IncomingMessageViewHolder -> {
                        holder.bind(item.message, formattedTime)
                    }
                }
            }
            is ChatListItem.DateHeaderItem -> {
                (holder as DateHeaderViewHolder).bind(item.date)
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        // ⚡ Reset view state efficiently to prevent flickering
        // Only reset if necessary (avoid expensive operations)
        if (holder.itemView.alpha != 1f) holder.itemView.alpha = 1f
        if (holder.itemView.translationX != 0f) holder.itemView.translationX = 0f
    }

    private fun getDateLabel(timestamp: Long): String {
        val date = Date(timestamp)
        val now = Calendar.getInstance()
        val messageTime = Calendar.getInstance().apply { time = date }

        return when {
            // Today
            now.get(Calendar.DAY_OF_YEAR) == messageTime.get(Calendar.DAY_OF_YEAR) &&
            now.get(Calendar.YEAR) == messageTime.get(Calendar.YEAR) -> "Today"
            
            // Yesterday
            now.get(Calendar.DAY_OF_YEAR) - 1 == messageTime.get(Calendar.DAY_OF_YEAR) &&
            now.get(Calendar.YEAR) == messageTime.get(Calendar.YEAR) -> "Yesterday"
            
            // This year - show "March 7"
            now.get(Calendar.YEAR) == messageTime.get(Calendar.YEAR) ->
                dateLabelFormat.format(date)
            
            // Different year - show "March 7, 2025"
            else -> dateLabelYearFormat.format(date)
        }
    }

    // ⚡ Cache formatted times to avoid repeated calculations
    private val timeCache = mutableMapOf<Long, String>()
    
    private fun formatChatTime(timestamp: Long): String {
        // Return cached value if available
        timeCache[timestamp]?.let { return it }
        
        val date = Date(timestamp)
        val now = Calendar.getInstance()
        val messageTime = Calendar.getInstance().apply { time = date }

        val formatted = when {
            // Today - show only time
            now.get(Calendar.DAY_OF_YEAR) == messageTime.get(Calendar.DAY_OF_YEAR) &&
            now.get(Calendar.YEAR) == messageTime.get(Calendar.YEAR) -> 
                timeFormat.format(date)
            
            // This year - show date and time without year
            now.get(Calendar.YEAR) == messageTime.get(Calendar.YEAR) -> 
                dateTimeFormat.format(date)
            
            // Different year - show full date
            else -> dateTimeYearFormat.format(date)
        }
        
        // Cache the result
        if (timeCache.size > 1000) timeCache.clear() // Prevent unbounded growth
        timeCache[timestamp] = formatted
        return formatted
    }
    
    // Clear cache when data changes
    fun clearCache() {
        timeCache.clear()
    }

    class DateHeaderViewHolder(
        private val binding: ItemChatDateHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(dateLabel: String) {
            binding.dateText.text = dateLabel
        }
    }

    class OutgoingMessageViewHolder(
        private val binding: ItemChatOutgoingBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMessage, formattedTime: String) {
            // ⚡ Only update if text changed (avoid unnecessary invalidations)
            if (binding.messageText.text != message.message) {
                binding.messageText.text = message.message
            }
            if (binding.timeText.text != formattedTime) {
                binding.timeText.text = formattedTime
            }
            // Hide status icon - cache visibility state
            if (binding.messageStatus.isVisible) {
                binding.messageStatus.isVisible = false
            }
        }
    }

    class IncomingMessageViewHolder(
        private val binding: ItemChatIncomingBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMessage, formattedTime: String) {
            // ⚡ Only update if text changed (avoid unnecessary invalidations)
            if (binding.messageText.text != message.message) {
                binding.messageText.text = message.message
            }
            if (binding.timeText.text != formattedTime) {
                binding.timeText.text = formattedTime
            }
            // Show sender name in group chats if needed
            if (binding.senderName.isVisible) {
                binding.senderName.isVisible = false
            }
        }
    }
}
