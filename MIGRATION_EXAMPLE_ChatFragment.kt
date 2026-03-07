package id.my.bananapixel.quakealert.ui

import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
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
 * MIGRATION EXAMPLE: Updated ChatFragment using Telegram-style components
 * 
 * Changes made:
 * 1. Changed binding from FragmentChatBinding to FragmentChatTelegramBinding
 * 2. Changed adapter from ChatAdapter to TelegramChatAdapter
 * 3. Added ChatMessageItemDecoration for proper spacing
 * 4. Enhanced input view with better animations
 * 5. Improved auto-scroll logic
 * 6. Added empty state handling
 */
class ChatFragmentMigrated : Fragment() {
    
    // CHANGE 1: Update binding type
    private var _binding: FragmentChatTelegramBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatViewModel by viewModel()
    
    // CHANGE 2: Update adapter type
    private lateinit var chatAdapter: TelegramChatAdapter
    private var socket: Socket? = null
    private lateinit var deviceId: String
    private var isFirstLoad = true
    private var lastMessageCount = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // CHANGE 3: Update binding inflation
        _binding = FragmentChatTelegramBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        deviceId = Settings.Secure.getString(
            requireContext().contentResolver,
            Settings.Secure.ANDROID_ID
        )
        
        setupRecyclerView()
        setupInputView()
        setupSocketConnection()
        observeMessages()
    }

    // CHANGE 4: Enhanced RecyclerView setup
    private fun setupRecyclerView() {
        chatAdapter = TelegramChatAdapter(deviceId)
        
        val layoutManager = LinearLayoutManager(requireContext())
        layoutManager.stackFromEnd = true
        
        // NEW: Add item decoration for proper spacing
        binding.messagesRecyclerView.apply {
            adapter = chatAdapter
            this.layoutManager = layoutManager
            addItemDecoration(ChatMessageItemDecoration(
                verticalSpacing = resources.getDimensionPixelSize(R.dimen.chat_message_spacing),
                groupSpacing = resources.getDimensionPixelSize(R.dimen.chat_group_spacing)
            ))
        }
    }

    // CHANGE 5: Enhanced input view with animations
    private fun setupInputView() {
        binding.sendButton.isEnabled = false
        binding.sendButton.alpha = 0.5f
        
        // NEW: Input text watcher with button animation
        binding.messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                val hasText = !s.isNullOrBlank()
                binding.sendButton.isEnabled = hasText
                
                // Animate send button
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
                
                // Haptic feedback
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
            
            socket?.on(Socket.EVENT_CONNECT) {
                requireActivity().runOnUiThread {
                    // Connection established
                }
            }
            
            socket?.on("receive_message") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    val payload = Json.decodeFromString(
                        ChatMessagePayload.serializer(),
                        data.toString()
                    )
                    
                    val chatMessage = ChatMessage(
                        senderId = payload.senderId,
                        message = payload.message,
                        timestamp = payload.timestamp
                    )
                    
                    lifecycleScope.launch {
                        viewModel.insertMessage(chatMessage)
                    }
                }
            }
            
            socket?.on("chat_history") { args ->
                if (args.isNotEmpty()) {
                    val historyArray = args[0] as JSONArray
                    lifecycleScope.launch {
                        for (i in 0 until historyArray.length()) {
                            val msgObj = historyArray.getJSONObject(i)
                            val payload = Json.decodeFromString(
                                ChatMessagePayload.serializer(),
                                msgObj.toString()
                            )
                            
                            val chatMessage = ChatMessage(
                                senderId = payload.senderId,
                                message = payload.message,
                                timestamp = payload.timestamp
                            )
                            
                            viewModel.insertMessage(chatMessage)
                        }
                    }
                }
            }
            
            socket?.connect()
            socket?.emit("request_history")
            
        } catch (e: URISyntaxException) {
            e.printStackTrace()
        }
    }

    // CHANGE 6: Enhanced message observation with better scroll logic
    private fun observeMessages() {
        lifecycleScope.launch {
            viewModel.chatMessages.collectLatest { messages ->
                chatAdapter.submitList(messages) {
                    // Update empty state
                    binding.emptyStateContainer.isVisible = messages.isEmpty()
                    
                    // Smart auto-scroll
                    if (messages.isNotEmpty()) {
                        if (isFirstLoad) {
                            binding.messagesRecyclerView.scrollToPosition(messages.size - 1)
                            isFirstLoad = false
                        } else if (messages.size > lastMessageCount) {
                            val layoutManager = binding.messagesRecyclerView.layoutManager as LinearLayoutManager
                            val lastVisiblePosition = layoutManager.findLastCompletelyVisibleItemPosition()
                            
                            // Auto-scroll if user is near bottom
                            if (lastVisiblePosition >= lastMessageCount - 3) {
                                binding.messagesRecyclerView.smoothScrollToPosition(messages.size - 1)
                            }
                        }
                        lastMessageCount = messages.size
                    }
                }
            }
        }
    }

    private fun sendMessage(message: String) {
        val payload = ChatMessagePayload(
            senderId = deviceId,
            message = message,
            timestamp = System.currentTimeMillis() / 1000
        )
        
        val jsonMessage = JSONObject().apply {
            put("senderId", payload.senderId)
            put("message", payload.message)
            put("timestamp", payload.timestamp)
        }
        
        socket?.emit("send_message", jsonMessage)
        
        lifecycleScope.launch {
            viewModel.insertMessage(
                ChatMessage(
                    senderId = payload.senderId,
                    message = payload.message,
                    timestamp = payload.timestamp
                )
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        socket?.disconnect()
        socket?.off()
        _binding = null
    }
}
