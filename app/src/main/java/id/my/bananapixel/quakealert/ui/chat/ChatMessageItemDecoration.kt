package id.my.bananapixel.quakealert.ui.chat

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * ItemDecoration for chat messages with Telegram-inspired spacing
 */
class ChatMessageItemDecoration(
    private val verticalSpacing: Int = 4,
    private val groupSpacing: Int = 12
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        
        if (position == RecyclerView.NO_POSITION) {
            return
        }

        // Add spacing between messages - consistent spacing, no extra padding at bottom
        outRect.top = if (position == 0) groupSpacing else verticalSpacing
        outRect.bottom = 0  // No bottom padding to avoid weird gap below last message
    }
}
