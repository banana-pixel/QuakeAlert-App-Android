package id.my.bananapixel.quakealert.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import com.google.android.material.card.MaterialCardView
import id.my.bananapixel.quakealert.R

/**
 * Enhanced chat input layout inspired by Telegram's ChatActivityEnterView.
 * Features:
 * - Expandable input field
 * - Smooth animations
 * - Send button state changes
 * - Attachment button
 */
class EnhancedChatInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val mainContainer: LinearLayout
    private val inputCardView: MaterialCardView
    private val messageEditText: EditText
    private val sendButton: ImageButton
    private val attachButton: ImageButton
    
    private var onSendClickListener: ((String) -> Unit)? = null
    private var onAttachClickListener: (() -> Unit)? = null
    
    private val animationDuration = 200L
    private val maxInputHeight: Int

    init {
        // Main container
        mainContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(dpToPx(12f), dpToPx(12f), dpToPx(12f), dpToPx(12f))
        }

        // Attachment button (optional - can be enabled)
        attachButton = ImageButton(context).apply {
            setImageResource(R.drawable.ic_attach_file)
            background = createRippleDrawable()
            setPadding(dpToPx(12f), dpToPx(12f), dpToPx(12f), dpToPx(12f))
            visibility = View.GONE // Hidden by default
            setColorFilter(ContextCompat.getColor(context, R.color.md_theme_onSurface))
        }

        // Input field in a card
        inputCardView = MaterialCardView(context).apply {
            radius = dpToPx(24f).toFloat()
            cardElevation = dpToPx(2f).toFloat()
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.md_theme_surface))
        }

        messageEditText = EditText(context).apply {
            hint = context.getString(R.string.chat_message_hint)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            maxLines = 4
            background = null
            setPadding(dpToPx(16f), dpToPx(12f), dpToPx(16f), dpToPx(12f))
            setTextColor(ContextCompat.getColor(context, R.color.md_theme_onSurface))
            setHintTextColor(ContextCompat.getColor(context, R.color.md_theme_onSurfaceVariant))
            imeOptions = EditorInfo.IME_ACTION_SEND
            inputType = EditorInfo.TYPE_CLASS_TEXT or 
                        EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES or 
                        EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT
        }

        inputCardView.addView(messageEditText, LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        ))

        // Send button
        sendButton = ImageButton(context).apply {
            setImageResource(R.drawable.send)
            background = createCircleDrawable()
            setPadding(dpToPx(12f), dpToPx(12f), dpToPx(12f), dpToPx(12f))
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            isEnabled = false
            alpha = 0.5f
        }

        // Add views to container
        val inputParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dpToPx(8f)
        }
        
        val buttonParams = LinearLayout.LayoutParams(dpToPx(48f), dpToPx(48f))

        mainContainer.addView(attachButton, buttonParams)
        mainContainer.addView(inputCardView, inputParams)
        mainContainer.addView(sendButton, buttonParams)

        addView(mainContainer, LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        ))

        maxInputHeight = dpToPx(120f)

        setupListeners()
    }

    private fun setupListeners() {
        // Text change listener for send button state
        messageEditText.addTextChangedListener { text ->
            val hasText = !text.isNullOrBlank()
            animateSendButton(hasText)
        }

        // Send button click
        sendButton.setOnClickListener {
            val message = messageEditText.text.toString().trim()
            if (message.isNotEmpty()) {
                onSendClickListener?.invoke(message)
                clearInput()
            }
        }

        // Attach button click
        attachButton.setOnClickListener {
            onAttachClickListener?.invoke()
        }

        // Send on IME action
        messageEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendButton.performClick()
                true
            } else {
                false
            }
        }
    }

    private fun animateSendButton(enabled: Boolean) {
        sendButton.isEnabled = enabled
        
        ValueAnimator.ofFloat(if (enabled) 0.5f else 1f, if (enabled) 1f else 0.5f).apply {
            duration = animationDuration
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                sendButton.alpha = value
                sendButton.scaleX = 0.8f + (value * 0.2f)
                sendButton.scaleY = 0.8f + (value * 0.2f)
            }
            start()
        }
    }

    fun setOnSendClickListener(listener: (String) -> Unit) {
        onSendClickListener = listener
    }

    fun setOnAttachClickListener(listener: () -> Unit) {
        onAttachClickListener = listener
        attachButton.visibility = View.VISIBLE
    }

    fun clearInput() {
        messageEditText.text?.clear()
    }

    fun setInputText(text: String) {
        messageEditText.setText(text)
        messageEditText.setSelection(text.length)
    }

    fun requestInputFocus() {
        messageEditText.requestFocus()
    }

    private fun createCircleDrawable(): android.graphics.drawable.Drawable {
        val drawable = android.graphics.drawable.GradientDrawable()
        drawable.shape = android.graphics.drawable.GradientDrawable.OVAL
        drawable.setColor(ContextCompat.getColor(context, R.color.md_theme_primary))
        return drawable
    }

    private fun createRippleDrawable(): android.graphics.drawable.Drawable? {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            val mask = android.graphics.drawable.GradientDrawable()
            mask.shape = android.graphics.drawable.GradientDrawable.OVAL
            mask.setColor(android.graphics.Color.WHITE)
            android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.md_theme_surfaceVariant)
                ),
                null,
                mask
            )
        } else {
            null
        }
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        ).toInt()
    }
}
