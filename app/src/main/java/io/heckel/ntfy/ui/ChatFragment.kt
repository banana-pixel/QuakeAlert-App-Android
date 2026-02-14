package io.heckel.ntfy.ui

import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import io.heckel.ntfy.databinding.FragmentChatBinding
import io.heckel.ntfy.msg.ChatMessage
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.engineio.client.transports.WebSocket
import org.json.JSONArray
import org.json.JSONObject
import java.net.URISyntaxException

class ChatFragment : Fragment() {
    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var chatAdapter: ChatAdapter
    private var socket: Socket? = null
    private lateinit var deviceId: String

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        deviceId = Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ANDROID_ID)
        chatAdapter = ChatAdapter(deviceId)

        binding.chatRecyclerView.apply {
            adapter = chatAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        initSocket()

        binding.chatSendButton.setOnClickListener {
            sendMessage()
        }
    }

    private fun initSocket() {
        try {
            val opts = IO.Options().apply {
                transports = arrayOf(WebSocket.NAME)
            }
            socket = IO.socket("https://quakealert.bananapixel.my.id", opts)

            // Register socket.on("chat_history") BEFORE socket.connect()
            socket?.on("chat_history") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONArray
                    val history = mutableListOf<ChatMessage>()
                    for (i in 0 until data.length()) {
                        val obj = data.getJSONObject(i)
                        val message = parseMessage(obj)
                        if (message != null) {
                            history.add(message)
                        }
                    }
                    activity?.runOnUiThread {
                        chatAdapter.submitList(history) {
                            if (history.isNotEmpty()) {
                                binding.chatRecyclerView.scrollToPosition(history.size - 1)
                            }
                        }
                    }
                }
            }

            // Register socket.on("receive_message") BEFORE socket.connect()
            socket?.on("receive_message") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    val message = parseMessage(data)
                    if (message != null) {
                        activity?.runOnUiThread {
                            val currentList = chatAdapter.currentList.toMutableList()
                            currentList.add(message)
                            chatAdapter.submitList(currentList) {
                                binding.chatRecyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
                            }
                        }
                    }
                }
            }

            socket?.connect()
        } catch (e: URISyntaxException) {
            e.printStackTrace()
        }
    }

    private fun parseMessage(obj: JSONObject): ChatMessage? {
        val senderId = obj.optString("senderId", "")
        val message = obj.optString("message", "")
        if (senderId.isEmpty() || message.isEmpty()) {
            return null
        }
        return ChatMessage(
            senderId = senderId,
            message = message,
            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
        )
    }

    private fun sendMessage() {
        val text = binding.chatInputEditText.text.toString().trim()
        if (text.isNotEmpty()) {
            val messageObj = JSONObject().apply {
                put("senderId", deviceId)
                put("message", text)
            }
            socket?.emit("send_message", messageObj)
            binding.chatInputEditText.text.clear()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Ensure that when the Fragment is destroyed, the socket properly disconnects
        socket?.disconnect()
        socket?.off()
        _binding = null
    }
}
