package id.my.bananapixel.quakealert.ui.components

import android.graphics.*
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import kotlin.math.min

/**
 * Custom drawable for chat message bubbles inspired by Telegram's message rendering.
 * Provides smooth rounded corners with tail (arrow) pointing to sender/receiver.
 */
class MessageBubbleDrawable(
    @ColorInt private var backgroundColor: Int,
    private val isOutgoing: Boolean
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = backgroundColor
    }

    private val path = Path()
    private val cornerRadius = 18f
    private val tailWidth = 8f
    private val tailHeight = 12f

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty) return

        path.reset()

        val rect = RectF(bounds)
        
        if (isOutgoing) {
            // Outgoing message (right side with tail on bottom-right)
            drawOutgoingBubble(rect)
        } else {
            // Incoming message (left side with tail on bottom-left)
            drawIncomingBubble(rect)
        }

        canvas.drawPath(path, paint)
    }

    private fun drawOutgoingBubble(rect: RectF) {
        // Main rounded rectangle
        path.addRoundRect(
            rect.left,
            rect.top,
            rect.right - tailWidth,
            rect.bottom - tailHeight,
            cornerRadius,
            cornerRadius,
            Path.Direction.CW
        )

        // Add tail (arrow) at bottom-right
        path.moveTo(rect.right - tailWidth, rect.bottom - tailHeight - cornerRadius)
        path.lineTo(rect.right, rect.bottom)
        path.lineTo(rect.right - tailWidth - 2f, rect.bottom - tailHeight)
        path.close()
    }

    private fun drawIncomingBubble(rect: RectF) {
        // Main rounded rectangle
        path.addRoundRect(
            rect.left + tailWidth,
            rect.top,
            rect.right,
            rect.bottom - tailHeight,
            cornerRadius,
            cornerRadius,
            Path.Direction.CW
        )

        // Add tail (arrow) at bottom-left
        path.moveTo(rect.left + tailWidth, rect.bottom - tailHeight - cornerRadius)
        path.lineTo(rect.left, rect.bottom)
        path.lineTo(rect.left + tailWidth + 2f, rect.bottom - tailHeight)
        path.close()
    }

    fun setBackgroundColor(@ColorInt color: Int) {
        backgroundColor = color
        paint.color = color
        invalidateSelf()
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
