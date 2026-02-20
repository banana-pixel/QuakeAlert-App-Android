package id.my.bananapixel.quakealert.ui

import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import id.my.bananapixel.quakealert.app.Application
import id.my.bananapixel.quakealert.databinding.FragmentChatBinding
import id.my.bananapixel.quakealert.db.ChatMessage
import id.my.bananapixel.quakealert.msg.ChatMessagePayload
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.engineio.client.transports.WebSocket
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.net.URISyntaxException

class ChatFragment : Fragment() {
    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    // Link to ViewModel for Database access
    private val viewModel by viewModels<SubscriptionsViewModel> {
        SubscriptionsViewModelFactory((requireActivity().application as Application).repository)
    }

    private lateinit var chatAdapter: ChatAdapter
    private var socket: Socket? = null
    private lateinit var deviceId: String
    private var isFirstLoad = true  // Only auto-scroll on first load
    private var lastMessageCount = 0  // Track message count to detect new messages

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        deviceId = Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ANDROID_ID)
        chatAdapter = ChatAdapter(deviceId)

        // CHANGED: chatRecyclerView -> recyclerView
        binding.recyclerView.apply {
            adapter = chatAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        // 1. Observe Room Database
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.chatMessages.collectLatest { messages ->
                chatAdapter.submitList(messages) {
                    binding.chatEmptyContainer.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
                    if (messages.isNotEmpty()) {
                        val hasNewMessages = messages.size > lastMessageCount
                        lastMessageCount = messages.size

                        // Auto-scroll only on:
                        // 1. First load, OR
                        // 2. New message added (count increased)
                        if (isFirstLoad || hasNewMessages) {
                            binding.recyclerView.scrollToPosition(messages.size - 1)
                            isFirstLoad = false
                        }
                    }
                }
            }
        }

        binding.chatSendButton.setOnClickListener { sendMessage() }
        initSocket()
    }

    private fun initSocket() {
        try {
            val opts = IO.Options().apply { transports = arrayOf(WebSocket.NAME) }
            socket = IO.socket("https://quakealert.bananapixel.my.id", opts)

            socket?.on("chat_history") { args ->
                val jsonString = (args[0] as JSONArray).toString()
                val history = parseChatHistory(jsonString)
                viewModel.saveChatMessages(history)
            }

            socket?.on("receive_message") { args ->
                val jsonString = (args[0] as JSONObject).toString()
                parseChatMessage(jsonString)?.let {
                    viewModel.saveChatMessages(listOf(it))
                }
            }
            socket?.connect()
        } catch (e: URISyntaxException) { e.printStackTrace() }
    }

    private fun parseChatMessage(jsonString: String): ChatMessage? {
        return try {
            val payload = chatJson.decodeFromString<ChatMessagePayload>(jsonString)
            if (payload.senderId.isEmpty() || payload.message.isEmpty()) return null
            val ts = if (payload.timestamp == 0L) System.currentTimeMillis() else payload.timestamp
            val uniqueId = "${payload.senderId}-${ts}-${payload.message.hashCode()}"
            ChatMessage(id = uniqueId, senderId = payload.senderId, message = payload.message, timestamp = ts)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseChatHistory(jsonString: String): List<ChatMessage> {
        return try {
            chatJson.decodeFromString<List<ChatMessagePayload>>(jsonString)
                .mapNotNull { payload ->
                    if (payload.senderId.isEmpty() || payload.message.isEmpty()) null
                    else {
                        val ts = if (payload.timestamp == 0L) System.currentTimeMillis() else payload.timestamp
                        val uniqueId = "${payload.senderId}-${ts}-${payload.message.hashCode()}"
                        ChatMessage(id = uniqueId, senderId = payload.senderId, message = payload.message, timestamp = ts)
                    }
                }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun sendMessage() {
        val text = binding.chatInputEditText.text.toString().trim()
        if (text.isNotEmpty()) {
            val payload = ChatMessagePayload(deviceId, text, System.currentTimeMillis())
            val messageObj = JSONObject(chatJson.encodeToString(ChatMessagePayload.serializer(), payload))
            socket?.emit("send_message", messageObj)
            binding.chatInputEditText.text.clear()
        }
    }

    companion object {
        private val chatJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    }

    override fun onDestroyView() {
        socket?.off(); socket?.disconnect(); socket = null
        _binding = null
        super.onDestroyView()
    }
}