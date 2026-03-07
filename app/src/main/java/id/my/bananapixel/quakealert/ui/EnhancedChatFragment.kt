package id.my.bananapixel.quakealert.ui

import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import id.my.bananapixel.quakealert.BuildConfig
import id.my.bananapixel.quakealert.R
import id.my.bananapixel.quakealert.db.ChatMessage
import id.my.bananapixel.quakealert.msg.ChatMessagePayload
import id.my.bananapixel.quakealert.ui.components.EnhancedChatInputView
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
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * Enhanced chat fragment with Telegram-inspired UI components.
 * Features:
 * - Custom bubble message views
 * - Enhanced input layout with animations
 * - Scroll to bottom FAB
 * - Smooth animations and transitions
 */
class EnhancedChatFragment : Fragment() {

    private val viewModel: ChatViewModel by viewModel()

    private lateinit var recyclerView: RecyclerView
    private lateinit var chatInputView: EnhancedChatInputView
    private lateinit var emptyContainer: View
    private lateinit var scrollToBottomFab: FloatingActionButton
    
    private lateinit var chatAdapter: EnhancedChatAdapter
    private var socket: Socket? = null
    private lateinit var deviceId: String
    private var isFirstLoad = true
    private var lastMessageCount = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_chat_enhanced, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        deviceId = Settings.Secure.getString(
            requireContext().contentResolver,
            Settings.Secure.ANDROID_ID
        )
        
        initializeViews(view)
        setupRecyclerView()
        setupInputView()
        setupScrollToBottomFab()
        observeChatMessages()
        initSocket()
    }

    private fun initializeViews(view: View) {
        recyclerView = view.findViewById(R.id.recycler_view)
        chatInputView = view.findViewById(R.id.chat_input_container)
        emptyContainer = view.findViewById(R.id.chat_empty_container)
        scrollToBottomFab = view.findViewById(R.id.scroll_to_bottom_fab)
    }

    private fun setupRecyclerView() {
        chatAdapter = EnhancedChatAdapter(deviceId)
        
        val layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        
        recyclerView.apply {
            adapter = chatAdapter
            this.layoutManager = layoutManager
            
            // Show/hide scroll to bottom FAB based on scroll position
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    
                    val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
                    val totalItemCount = chatAdapter.itemCount
                    
                    // Show FAB if not at bottom
                    if (totalItemCount > 0 && lastVisiblePosition < totalItemCount - 3) {
                        scrollToBottomFab.show()
                    } else {
                        scrollToBottomFab.hide()
                    }
                }
            })
        }
    }

    private fun setupInputView() {
        chatInputView.setOnSendClickListener { message ->
            sendMessage(message)
        }
        
        // Optional: Add attachment support
        // chatInputView.setOnAttachClickListener {
        //     showAttachmentOptions()
        // }
    }

    private fun setupScrollToBottomFab() {
        scrollToBottomFab.setOnClickListener {
            scrollToBottom(smooth = true)
        }
    }

    private fun observeChatMessages() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.chatMessages.collectLatest { messages ->
                chatAdapter.submitList(messages) {
                    // Update empty state
                    emptyContainer.visibility = if (messages.isEmpty()) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
                    
                    // Auto-scroll logic
                    if (messages.isNotEmpty()) {
                        val hasNewMessages = messages.size > lastMessageCount
                        lastMessageCount = messages.size

                        // Scroll to bottom on first load or new message
                        if (isFirstLoad || hasNewMessages) {
                            scrollToBottom(smooth = !isFirstLoad)
                            isFirstLoad = false
                        }
                    }
                }
            }
        }
    }

    private fun initSocket() {
        try {
            val opts = IO.Options().apply {
                transports = arrayOf(WebSocket.NAME)
            }
            socket = IO.socket(BuildConfig.APP_BASE_URL, opts)

            socket?.on("chat_history") { args ->
                val jsonString = (args[0] as JSONArray).toString()
                val history = parseChatHistory(jsonString)
                viewModel.saveChatMessages(history)
            }

            socket?.on("receive_message") { args ->
                val jsonString = (args[0] as JSONObject).toString()
                parseChatMessage(jsonString)?.let { message ->
                    viewModel.saveChatMessages(listOf(message))
                }
            }

            socket?.connect()
        } catch (e: URISyntaxException) {
            e.printStackTrace()
        }
    }

    private fun parseChatMessage(jsonString: String): ChatMessage? {
        return try {
            val payload = chatJson.decodeFromString<ChatMessagePayload>(jsonString)
            
            if (payload.senderId.isEmpty() || payload.message.isEmpty()) {
                return null
            }
            
            val timestamp = if (payload.timestamp == 0L) {
                System.currentTimeMillis()
            } else {
                payload.timestamp
            }
            
            val uniqueId = "${payload.senderId}-${timestamp}-${payload.message.hashCode()}"
            
            ChatMessage(
                id = uniqueId,
                senderId = payload.senderId,
                message = payload.message,
                timestamp = timestamp
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseChatHistory(jsonString: String): List<ChatMessage> {
        return try {
            chatJson.decodeFromString<List<ChatMessagePayload>>(jsonString)
                .mapNotNull { payload ->
                    if (payload.senderId.isEmpty() || payload.message.isEmpty()) {
                        null
                    } else {
                        val timestamp = if (payload.timestamp == 0L) {
                            System.currentTimeMillis()
                        } else {
                            payload.timestamp
                        }
                        
                        val uniqueId = "${payload.senderId}-${timestamp}-${payload.message.hashCode()}"
                        
                        ChatMessage(
                            id = uniqueId,
                            senderId = payload.senderId,
                            message = payload.message,
                            timestamp = timestamp
                        )
                    }
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun sendMessage(text: String) {
        val payload = ChatMessagePayload(
            senderId = deviceId,
            message = text,
            timestamp = System.currentTimeMillis()
        )
        
        val messageObj = JSONObject(
            chatJson.encodeToString(ChatMessagePayload.serializer(), payload)
        )
        
        socket?.emit("send_message", messageObj)
    }

    private fun scrollToBottom(smooth: Boolean = true) {
        val itemCount = chatAdapter.itemCount
        if (itemCount > 0) {
            if (smooth) {
                recyclerView.smoothScrollToPosition(itemCount - 1)
            } else {
                recyclerView.scrollToPosition(itemCount - 1)
            }
        }
    }

    override fun onDestroyView() {
        socket?.off()
        socket?.disconnect()
        socket = null
        super.onDestroyView()
    }

    companion object {
        private val chatJson = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }
}
