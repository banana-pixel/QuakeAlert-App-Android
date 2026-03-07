package id.my.bananapixel.quakealert.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import id.my.bananapixel.quakealert.R

/**
 * Custom view for chat message bubble inspired by Telegram's ChatMessageCell.
 * Handles dynamic sizing, proper padding, and message presentation.
 */
class ChatMessageBubbleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var isOutgoing: Boolean = false
    private var bubbleDrawable: MessageBubbleDrawable? = null

    private val messageTextView: TextView
    private val timestampTextView: TextView
    
    private val messagePadding = dpToPx(12f)
    private val timestampPadding = dpToPx(4f)

    init {
        orientation = VERTICAL
        
        // Message text
        messageTextView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setLineSpacing(dpToPx(2f), 1f)
        }
        
        // Timestamp text
        timestampTextView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            alpha = 0.6f
        }
        
        val messageParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            setMargins(messagePadding.toInt(), messagePadding.toInt(), 
                       messagePadding.toInt(), timestampPadding.toInt())
        }
        
        val timestampParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            setMargins(messagePadding.toInt(), 0, messagePadding.toInt(), messagePadding.toInt())
            gravity = Gravity.END
        }
        
        addView(messageTextView, messageParams)
        addView(timestampTextView, timestampParams)
    }

    fun setMessage(text: String, timestamp: String, isOutgoing: Boolean) {
        this.isOutgoing = isOutgoing
        messageTextView.text = text
        timestampTextView.text = timestamp
        
        // Set colors based on message direction
        val bgColor: Int
        val textColor: Int
        
        if (isOutgoing) {
            bgColor = ContextCompat.getColor(context, R.color.chat_bubble_out_color)
            textColor = Color.WHITE
        } else {
            bgColor = ContextCompat.getColor(context, R.color.chat_bubble_in_color)
            textColor = ContextCompat.getColor(context, R.color.md_theme_onSurface)
        }
        
        // Create and set bubble drawable
        bubbleDrawable = MessageBubbleDrawable(bgColor, isOutgoing)
        background = bubbleDrawable
        
        messageTextView.setTextColor(textColor)
        timestampTextView.setTextColor(textColor)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec)
        
        // Maximum bubble width is 75% of available width
        val maxBubbleWidth = (availableWidth * 0.75f).toInt()
        val bubbleWidthSpec = MeasureSpec.makeMeasureSpec(maxBubbleWidth, MeasureSpec.AT_MOST)
        
        super.onMeasure(bubbleWidthSpec, heightMeasureSpec)
        
        // Ensure minimum width for short messages
        val minWidth = dpToPx(80f).toInt()
        val finalWidth = measuredWidth.coerceAtLeast(minWidth)
        setMeasuredDimension(finalWidth, measuredHeight)
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        )
    }
}
