package id.my.bananapixel.quakealert.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
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
 * Enhanced ChatAdapter with Telegram-inspired design, animations, and date headers
 */
class TelegramChatAdapter(
    private val myDeviceId: String
) : ListAdapter<ChatListItem, RecyclerView.ViewHolder>(DiffCallback) {

    companion object {
        private const val VIEW_TYPE_OUTGOING = 1
        private const val VIEW_TYPE_INCOMING = 2
        private const val VIEW_TYPE_DATE_HEADER = 3
        
        private const val ANIMATION_DURATION = 300L
        private const val ANIMATION_DELAY = 50L

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

    // Track animated messages by their content (senderId + message text), NOT by position
    // This prevents re-animating the same message when timestamps change or positions shift
    private val animatedMessages = mutableSetOf<String>()

    /**
     * Converts a list of ChatMessages into ChatListItems with date headers
     */
    fun submitMessageList(messages: List<ChatMessage>) {
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
        return when (viewType) {
            VIEW_TYPE_OUTGOING -> OutgoingMessageViewHolder(
                ItemChatOutgoingBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
            VIEW_TYPE_INCOMING -> IncomingMessageViewHolder(
                ItemChatIncomingBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
            VIEW_TYPE_DATE_HEADER -> DateHeaderViewHolder(
                ItemChatDateHeaderBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
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
                        animateMessage(holder.itemView, item.message, position)
                    }
                    is IncomingMessageViewHolder -> {
                        holder.bind(item.message, formattedTime)
                        animateMessage(holder.itemView, item.message, position)
                    }
                }
            }
            is ChatListItem.DateHeaderItem -> {
                (holder as DateHeaderViewHolder).bind(item.date)
            }
        }
    }

    private fun animateMessage(view: View, message: ChatMessage, position: Int) {
        // Create a unique key based on message content, not timestamp or ID
        // This prevents re-animating when server updates timestamp
        val messageKey = "${message.senderId}:${message.message}"
        
        // Only animate new messages once
        if (animatedMessages.contains(messageKey)) {
            // Message already animated, ensure view is visible
            view.alpha = 1f
            view.translationX = 0f
            return
        }
        
        animatedMessages.add(messageKey)
        
        // Slide and fade animation with proper layout handling
        view.alpha = 0f
        view.translationX = if (getItemViewType(position) == VIEW_TYPE_OUTGOING) 100f else -100f
        
        val alphaAnimator = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f)
        val translationXAnimator = ObjectAnimator.ofFloat(view, "translationX", view.translationX, 0f)
        
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(alphaAnimator, translationXAnimator)
        animatorSet.duration = 250L
        animatorSet.interpolator = DecelerateInterpolator()
        // Fix spacing issue: force layout update after animation completes
        animatorSet.addListener(object : android.animation.Animator.AnimatorListener {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                view.requestLayout()
            }
            override fun onAnimationStart(animation: android.animation.Animator) {}
            override fun onAnimationCancel(animation: android.animation.Animator) {}
            override fun onAnimationRepeat(animation: android.animation.Animator) {}
        })
        animatorSet.start()
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        // Reset animations when view is recycled
        holder.itemView.alpha = 1f
        holder.itemView.translationX = 0f
    }

    fun clearAnimations() {
        animatedMessages.clear()
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
                SimpleDateFormat("MMMM d", Locale.getDefault()).format(date)
            
            // Different year - show "March 7, 2025"
            else -> SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(date)
        }
    }

    private fun formatChatTime(timestamp: Long): String {
        val date = Date(timestamp) // Already in milliseconds
        val now = Calendar.getInstance()
        val messageTime = Calendar.getInstance().apply { time = date }

        return if (now.get(Calendar.DAY_OF_YEAR) == messageTime.get(Calendar.DAY_OF_YEAR) &&
            now.get(Calendar.YEAR) == messageTime.get(Calendar.YEAR)
        ) {
            // Today - show only time
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        } else if (now.get(Calendar.YEAR) == messageTime.get(Calendar.YEAR)) {
            // This year - show date and time without year
            SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(date)
        } else {
            // Different year - show full date
            SimpleDateFormat("MMM d yyyy, HH:mm", Locale.getDefault()).format(date)
        }
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
            binding.messageText.text = message.message
            binding.timeText.text = formattedTime
            // Hide status icon
            binding.messageStatus.isVisible = false
        }
    }

    class IncomingMessageViewHolder(
        private val binding: ItemChatIncomingBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMessage, formattedTime: String) {
            binding.messageText.text = message.message
            binding.timeText.text = formattedTime
            // Show sender name in group chats if needed
            binding.senderName.isVisible = false
        }
    }
}
