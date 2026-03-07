package id.my.bananapixel.quakealert.util

import android.os.Build
import android.view.View
import android.view.WindowInsets
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView

/**
 * Helper class for synchronized keyboard (IME) animations.
 * Provides Telegram-style "elastic" behavior where UI elements follow the keyboard 1:1.
 * 
 * **Key principles:**
 * - Uses `translationY` to move input bar (not padding/margin)
 * - Updates RecyclerView bottom padding dynamically to match input bar position
 * - No fitsSystemWindows - manual inset handling for full control
 * - Edge-to-edge with WindowCompat.setDecorFitsSystemWindows(window, false)
 * 
 * Usage:
 * ```
 * KeyboardAnimationHelper.setupKeyboardAnimation(
 *     rootView = binding.root,
 *     recyclerView = binding.recyclerView,
 *     bottomContainer = binding.bottomFloatingUi,
 *     onKeyboardVisibilityChanged = { isVisible, imeHeight ->
 *         // Optional: Handle visibility changes
 *     }
 * )
 * ```
 */
object KeyboardAnimationHelper {

    /**
     * Sets up synchronized keyboard animation for a chat-like UI.
     * 
     * @param rootView The root view to apply WindowInsetsAnimation callback to
     * @param recyclerView The RecyclerView containing messages
     * @param bottomContainer The container with the input field and send button
     * @param onKeyboardVisibilityChanged Optional callback for keyboard visibility changes
     */
    fun setupKeyboardAnimation(
        rootView: View,
        recyclerView: RecyclerView,
        bottomContainer: View,
        onKeyboardVisibilityChanged: ((isVisible: Boolean, imeHeight: Int) -> Unit)? = null
    ) {
        // Store initial padding values
        val initialRecyclerBottomPadding = recyclerView.paddingBottom
        var lastImeHeight = 0

        // Apply WindowInsetsAnimationCallback for smooth keyboard tracking
        ViewCompat.setWindowInsetsAnimationCallback(
            rootView,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
                
                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: List<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    // Get IME and system bars insets
                    val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
                    val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                    
                    // Calculate keyboard height (above navigation bar)
                    val imeHeight = (imeInsets.bottom - systemBarsInsets.bottom).coerceAtLeast(0)
                    
                    // Move input bar up using translationY (1:1 sync with keyboard)
                    // Negative translation moves the view UP
                    bottomContainer.translationY = -imeHeight.toFloat()
                    
                    // Update RecyclerView bottom padding to keep messages visible
                    // When translationY is used, the view's layout position stays the same
                    // So we need padding for: imeHeight (keyboard) + bottomContainer.height (input bar)
                    // This ensures messages don't go behind the visually translated input bar
                    val additionalPadding = if (imeHeight > 0) {
                        imeHeight + bottomContainer.height
                    } else {
                        0
                    }
                    recyclerView.setPadding(
                        recyclerView.paddingLeft,
                        recyclerView.paddingTop,
                        recyclerView.paddingRight,
                        initialRecyclerBottomPadding + additionalPadding
                    )
                    
                    return insets
                }
            }
        )

        // Apply WindowInsetsListener for state changes
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeHeight = (imeInsets.bottom - systemBarsInsets.bottom).coerceAtLeast(0)
            val isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())

            // Notify callback if IME visibility changed
            if (imeHeight != lastImeHeight) {
                lastImeHeight = imeHeight
                onKeyboardVisibilityChanged?.invoke(isImeVisible, imeHeight)
            }

            // Reset to initial state when keyboard is fully hidden
            if (!isImeVisible && imeHeight == 0) {
                bottomContainer.translationY = 0f
                recyclerView.setPadding(
                    recyclerView.paddingLeft,
                    recyclerView.paddingTop,
                    recyclerView.paddingRight,
                    initialRecyclerBottomPadding
                )
            }

            // Don't consume insets - let them propagate
            insets
        }
    }

    /**
     * Extension function to animate RecyclerView scroll when keyboard appears.
     * Ensures the last message is visible when keyboard is shown.
     */
    fun RecyclerView.scrollToBottomWithKeyboard() {
        adapter?.let { adapter ->
            if (adapter.itemCount > 0) {
                post {
                    smoothScrollToPosition(adapter.itemCount - 1)
                }
            }
        }
    }
}
