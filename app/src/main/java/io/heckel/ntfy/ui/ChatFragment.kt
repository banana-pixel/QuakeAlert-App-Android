package io.heckel.ntfy.ui

import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.heckel.ntfy.app.Application
import io.heckel.ntfy.databinding.FragmentChatBinding
import io.heckel.ntfy.db.ChatMessage
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.engineio.client.transports.WebSocket
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
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
                    if (messages.isNotEmpty()) {
                        // CHANGED: chatRecyclerView -> recyclerView
                        binding.recyclerView.scrollToPosition(messages.size - 1)
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
                val data = args[0] as JSONArray
                val history = mutableListOf<ChatMessage>()
                for (i in 0 until data.length()) {
                    parseMessage(data.getJSONObject(i))?.let { history.add(it) }
                }
                // 2. Save history to Database instead of just updating adapter
                viewModel.saveChatMessages(history)
            }

            socket?.on("receive_message") { args ->
                parseMessage(args[0] as JSONObject)?.let {
                    // 3. Save new message to Database
                    viewModel.saveChatMessages(listOf(it))
                }
            }
            socket?.connect()
        } catch (e: URISyntaxException) { e.printStackTrace() }
    }

    private fun parseMessage(obj: JSONObject): ChatMessage? {
        val senderId = obj.optString("senderId", "")
        val message = obj.optString("message", "")
        val timestamp = obj.optLong("timestamp", System.currentTimeMillis())

        if (senderId.isEmpty() || message.isEmpty()) return null

        // Create a unique ID string. If the same message comes again, it will have the same ID.
        val uniqueId = "${senderId}-${timestamp}-${message.hashCode()}"

        return ChatMessage(
            id = uniqueId,
            senderId = senderId,
            message = message,
            timestamp = timestamp
        )
    }

    private fun sendMessage() {
        val text = binding.chatInputEditText.text.toString().trim()
        if (text.isNotEmpty()) {
            val messageObj = JSONObject().apply {
                put("senderId", deviceId)
                put("message", text)
                put("timestamp", System.currentTimeMillis())
            }
            socket?.emit("send_message", messageObj)
            binding.chatInputEditText.text.clear()
        }
    }

    override fun onDestroyView() {
        socket?.off(); socket?.disconnect(); socket = null
        _binding = null
        super.onDestroyView()
    }
}