package id.my.bananapixel.quakealert.ui

import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import id.my.bananapixel.quakealert.BuildConfig
import id.my.bananapixel.quakealert.R
import id.my.bananapixel.quakealert.databinding.FragmentChatTelegramBinding
import id.my.bananapixel.quakealert.db.ChatMessage
import id.my.bananapixel.quakealert.msg.ChatMessagePayload
import id.my.bananapixel.quakealert.ui.chat.ChatMessageItemDecoration
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.engineio.client.transports.WebSocket
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.net.URISyntaxException

/**
 * Enhanced Chat Fragment with Telegram-inspired UI
 */
class TelegramChatFragment : Fragment() {
    
    private var _binding: FragmentChatTelegramBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: ChatViewModel by viewModel()
    private lateinit var chatAdapter: TelegramChatAdapter
    private var socket: Socket? = null
    private lateinit var deviceId: String
    private var isFirstLoad = true
    private var lastMessageCount = 0
    private var isSending = false  // ✅ Prevent double-send


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatTelegramBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        deviceId = Settings.Secure.getString(
            requireContext().contentResolver,
            Settings.Secure.ANDROID_ID
        )
        
        // Debug: Log fragment creation
        android.util.Log.d("TelegramChat", "📱 Fragment created - DeviceID: ${deviceId.take(8)}")
        
        setupRecyclerView()
        setupInputView()
        setupSocketConnection()
        observeMessages()
    }

    private fun setupRecyclerView() {
        chatAdapter = TelegramChatAdapter(deviceId)
        
        val layoutManager = LinearLayoutManager(requireContext())
        layoutManager.stackFromEnd = true
        
        binding.recyclerView.apply {
            adapter = chatAdapter
            this.layoutManager = layoutManager
            addItemDecoration(ChatMessageItemDecoration(
                verticalSpacing = resources.getDimensionPixelSize(R.dimen.chat_message_spacing),
                groupSpacing = resources.getDimensionPixelSize(R.dimen.chat_group_spacing)
            ))
            
            // ⚡ Performance optimizations for smooth scrolling
            // Disable item animations (no jank during scroll)
            itemAnimator = null
            
            // Cache fixed-size items (faster measure/layout passes)
            setHasFixedSize(false)
            
            // Cache 8 view holders to reduce inflation overhead
            setItemViewCacheSize(8)
            
            // Prefetch with 2 item lookahead for smooth scrolling
            (this.layoutManager as? LinearLayoutManager)?.setItemPrefetchEnabled(true)
            // Prefetch 4 items ahead to prevent stutter
            Recycler().apply { setViewCacheExtension(null) }
            
            // Setup scroll listener for scroll-to-bottom button
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    updateScrollButtonVisibility()
                }
            })
        }
        
        // Scroll to bottom button
        binding.scrollToBottomButton.setOnClickListener {
            binding.recyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
        }
    }

    private fun setupInputView() {
        // Send button animation and state
        binding.sendButton.isEnabled = false
        binding.sendButton.alpha = 0.5f
        
        binding.messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                val hasText = !s.isNullOrBlank()
                binding.sendButton.isEnabled = hasText
                binding.sendButton.animate()
                    .alpha(if (hasText) 1f else 0.5f)
                    .scaleX(if (hasText) 1f else 0.9f)
                    .scaleY(if (hasText) 1f else 0.9f)
                    .setDuration(150)
                    .start()
            }
        })
        
        binding.sendButton.setOnClickListener {
            val message = binding.messageInput.text.toString().trim()
            if (message.isNotEmpty()) {
                sendMessage(message)
                binding.messageInput.text?.clear()
                
                // Auto-scroll to bottom after sending
                binding.recyclerView.postDelayed({
                    val itemCount = chatAdapter.itemCount
                    if (itemCount > 0) {
                        binding.recyclerView.smoothScrollToPosition(itemCount - 1)
                    }
                }, 100)
                
                // Vibrate feedback
                it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }
    }

    private fun setupSocketConnection() {
        try {
            val opts = IO.Options().apply {
                transports = arrayOf(WebSocket.NAME)
                reconnection = true
                reconnectionAttempts = Int.MAX_VALUE
                reconnectionDelay = 1000
            }
            
            socket = IO.socket(BuildConfig.APP_BASE_URL, opts)
            
            // ✅ Remove ALL old listeners first to prevent duplicates
            socket?.off()
            
            socket?.on(Socket.EVENT_CONNECT) {
                requireActivity().runOnUiThread {
                    // Connection established
                    android.util.Log.d("TelegramChat", "🔌 Socket CONNECTED")
                }
            }
            
            // ✅ Handle server error messages (too long, rate limited, etc.)
            socket?.on("error_message") { args ->
                if (args.isNotEmpty()) {
                    val errorMsg = args[0].toString()
                    requireActivity().runOnUiThread {
                        android.widget.Toast.makeText(
                            requireContext(),
                            errorMsg,
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            
            socket?.on("receive_message") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    val payload = Json.decodeFromString(
                        ChatMessagePayload.serializer(),
                        data.toString()
                    )
                    
                    // ✅ Server now sends milliseconds directly (no conversion needed)
                    val ts = if (payload.timestamp == 0L) {
                        System.currentTimeMillis()
                    } else {
                        payload.timestamp  // Already in milliseconds
                    }
                    val uniqueId = "${payload.senderId}-${ts}-${payload.message.hashCode()}"
                    
                    // Debug log to track message reception
                    android.util.Log.d("TelegramChat", "Received message: $uniqueId")
                    
                    val chatMessage = ChatMessage(
                        id = uniqueId,
                        senderId = payload.senderId,
                        message = payload.message,
                        timestamp = ts
                    )
                    
                    lifecycleScope.launch {
                        viewModel.saveChatMessages(listOf(chatMessage))
                    }
                }
            }
            
            socket?.on("chat_history") { args ->
                if (args.isNotEmpty()) {
                    val historyArray = args[0] as JSONArray
                    lifecycleScope.launch {
                        val messages = mutableListOf<ChatMessage>()
                        for (i in 0 until historyArray.length()) {
                            val msgObj = historyArray.getJSONObject(i)
                            val payload = Json.decodeFromString(
                                ChatMessagePayload.serializer(),
                                msgObj.toString()
                            )
                            
                            // ✅ Server now sends milliseconds directly (no conversion needed)
                            val ts = if (payload.timestamp == 0L) {
                                System.currentTimeMillis()
                            } else {
                                payload.timestamp  // Already in milliseconds
                            }
                            val uniqueId = "${payload.senderId}-${ts}-${payload.message.hashCode()}"
                            
                            messages.add(ChatMessage(
                                id = uniqueId,
                                senderId = payload.senderId,
                                message = payload.message,
                                timestamp = ts
                            ))
                        }
                        viewModel.saveChatMessages(messages)
                    }
                }
            }
            
            // Listen for online user count updates
            socket?.on("online_count") { args ->
                if (args.isNotEmpty()) {
                    val count = args[0] as Int
                    lifecycleScope.launch {
                        binding.onlineCount.text = "$count online"
                        android.util.Log.d("TelegramChat", "👥 Online count updated: $count")
                    }
                }
            }
            
            socket?.connect()
            socket?.emit("request_history")
            
        } catch (e: URISyntaxException) {
            e.printStackTrace()
        }
    }

    private fun observeMessages() {
        lifecycleScope.launch {
            viewModel.chatMessages.collectLatest { messages ->
                // Fix old messages that might have timestamps in seconds
                val fixedMessages = messages.map { message ->
                    // If timestamp is < year 2000 (946684800000), it's in seconds - convert it
                    if (message.timestamp > 0 && message.timestamp < 946684800000L) {
                        message.copy(timestamp = message.timestamp * 1000L)
                    } else {
                        message
                    }
                }
                
                chatAdapter.submitMessageList(fixedMessages)
                
                // Update empty state
                binding.emptyStateContainer.isVisible = fixedMessages.isEmpty()
                
                // Auto-scroll logic
                if (fixedMessages.isNotEmpty()) {
                    if (isFirstLoad) {
                        binding.recyclerView.scrollToPosition(chatAdapter.itemCount - 1)
                        isFirstLoad = false
                    } else if (fixedMessages.size > lastMessageCount) {
                        // New message arrived
                        val layoutManager = binding.recyclerView.layoutManager as LinearLayoutManager
                        val lastVisiblePosition = layoutManager.findLastCompletelyVisibleItemPosition()
                        
                        // Auto-scroll if user is near bottom
                        if (lastVisiblePosition >= chatAdapter.itemCount - 5) {
                            binding.recyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
                        }
                    }
                    lastMessageCount = fixedMessages.size
                }
            }
        }
    }

    private fun sendMessage(message: String) {
        // ✅ Prevent double-send from rapid clicks
        if (isSending) {
            return
        }
        
        isSending = true
        
        val timestamp = System.currentTimeMillis()
        val payload = ChatMessagePayload(
            senderId = deviceId,
            message = message,
            timestamp = timestamp
        )
        
        val jsonMessage = JSONObject().apply {
            put("senderId", payload.senderId)
            put("message", payload.message)
            put("timestamp", payload.timestamp)
        }
        
        socket?.emit("send_message", jsonMessage)
        
        // ✅ DON'T save locally - let server echo it back via receive_message
        // This prevents duplicate messages with different timestamps
        
        // Reset send flag after a short delay
        binding.root.postDelayed({
            isSending = false
        }, 500)
    }
    
    private fun updateScrollButtonVisibility() {
        val layoutManager = binding.recyclerView.layoutManager as? LinearLayoutManager ?: return
        val lastVisiblePosition = layoutManager.findLastCompletelyVisibleItemPosition()
        val totalItemCount = chatAdapter.itemCount
        
        // Show button if user scrolled up (not at the bottom)
        val shouldShow = totalItemCount > 0 && lastVisiblePosition < totalItemCount - 1
        
        if (shouldShow && binding.scrollToBottomButton.visibility != View.VISIBLE) {
            binding.scrollToBottomButton.visibility = View.VISIBLE
            binding.scrollToBottomButton.alpha = 0f
            binding.scrollToBottomButton.animate()
                .alpha(1f)
                .setDuration(200)
                .start()
        } else if (!shouldShow && binding.scrollToBottomButton.visibility == View.VISIBLE) {
            binding.scrollToBottomButton.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    binding.scrollToBottomButton.visibility = View.GONE
                }
                .start()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        android.util.Log.d("TelegramChat", "🔌 Socket DISCONNECTING")
        socket?.disconnect()
        socket?.off()
        _binding = null
    }
}
