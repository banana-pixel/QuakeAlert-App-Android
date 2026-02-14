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

        deviceId = Settings.Secure.getString(
            requireContext().contentResolver,
            Settings.Secure.ANDROID_ID
        )

        chatAdapter = ChatAdapter(deviceId)

        binding.chatRecyclerView.apply {
            adapter = chatAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        binding.chatSendButton.setOnClickListener {
            sendMessage()
        }

        initSocket()
    }

    private fun initSocket() {
        try {
            val opts = IO.Options().apply {
                transports = arrayOf(WebSocket.NAME)
            }

            socket = IO.socket("https://quakealert.bananapixel.my.id", opts)

            socket?.on("chat_history") { args ->
                if (!isAdded || _binding == null) return@on

                if (args.isNotEmpty()) {
                    val data = args[0] as JSONArray
                    val history = mutableListOf<ChatMessage>()

                    for (i in 0 until data.length()) {
                        parseMessage(data.getJSONObject(i))?.let { history.add(it) }
                    }

                    view?.post {
                        if (_binding == null) return@post

                        chatAdapter.submitList(history) {
                            if (history.isNotEmpty()) {
                                binding.chatRecyclerView
                                    .scrollToPosition(history.size - 1)
                            }
                        }
                    }
                }
            }

            socket?.on("receive_message") { args ->
                if (!isAdded || _binding == null) return@on

                if (args.isNotEmpty()) {
                    val message = parseMessage(args[0] as JSONObject)

                    if (message != null) {
                        view?.post {
                            if (_binding == null) return@post

                            val currentList =
                                chatAdapter.currentList.toMutableList()

                            currentList.add(message)

                            chatAdapter.submitList(currentList) {
                                binding.chatRecyclerView
                                    .smoothScrollToPosition(chatAdapter.itemCount - 1)
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

        if (senderId.isEmpty() || message.isEmpty()) return null

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
        socket?.off()
        socket?.disconnect()
        socket = null

        _binding = null
        super.onDestroyView()
    }
}

