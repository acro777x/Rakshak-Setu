package com.rakshaksetu.voip.webrtc

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * WebSocket signaling client for peer-to-peer WebRTC session initiation.
 */
class SignalingClient(
    val serverUrl: String = DEFAULT_SERVER_URL,
    private val listener: Listener
) {
    companion object {
        private const val TAG = "SignalingClient"
        const val DEFAULT_SERVER_URL = "ws://10.0.2.2:8080" // Android emulator loopback to host
    }

    interface Listener {
        fun onConnected()
        fun onDisconnected()
        fun onOfferReceived(from: String, sdp: String)
        fun onAnswerReceived(from: String, sdp: String)
        fun onCandidateReceived(from: String, mid: String, index: Int, sdp: String)
        fun onHangupReceived(from: String, reason: String)
        fun onError(message: String)
    }

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Keep-alive WebSocket
        .build()

    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var myClientId: String = ""

    fun connect(clientId: String) {
        this.myClientId = clientId
        try {
            val request = Request.Builder().url(serverUrl).build()
            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    Log.i(TAG, "Signaling WebSocket connected to $serverUrl")
                    isConnected = true
                    // Register clientId
                    val reg = JsonObject().apply {
                        addProperty("type", "register")
                        addProperty("clientId", clientId)
                    }
                    ws.send(gson.toJson(reg))
                    listener.onConnected()
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    handleMessage(text)
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    Log.w(TAG, "Signaling WebSocket failed: ${t.message}")
                    isConnected = false
                    listener.onError(t.message ?: "Connection failure")
                    listener.onDisconnected()
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    Log.i(TAG, "Signaling WebSocket closed ($code: $reason)")
                    isConnected = false
                    listener.onDisconnected()
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initiate WebSocket connection: ${e.message}")
            listener.onError(e.message ?: "Initialization error")
        }
    }

    fun sendOffer(to: String, sdp: String) {
        val json = JsonObject().apply {
            addProperty("type", "offer")
            addProperty("from", myClientId)
            addProperty("to", to)
            addProperty("sdp", sdp)
        }
        send(gson.toJson(json))
    }

    fun sendAnswer(to: String, sdp: String) {
        val json = JsonObject().apply {
            addProperty("type", "answer")
            addProperty("from", myClientId)
            addProperty("to", to)
            addProperty("sdp", sdp)
        }
        send(gson.toJson(json))
    }

    fun sendCandidate(to: String, mid: String, index: Int, sdp: String) {
        val json = JsonObject().apply {
            addProperty("type", "candidate")
            addProperty("from", myClientId)
            addProperty("to", to)
            addProperty("sdpMid", mid)
            addProperty("sdpMLineIndex", index)
            addProperty("sdpCandidate", sdp)
        }
        send(gson.toJson(json))
    }

    fun sendHangup(to: String, reason: String = "normal_clearing") {
        val json = JsonObject().apply {
            addProperty("type", "hangup")
            addProperty("from", myClientId)
            addProperty("to", to)
            addProperty("reason", reason)
        }
        send(gson.toJson(json))
    }

    private fun send(text: String) {
        webSocket?.send(text)
    }

    fun disconnect() {
        webSocket?.close(1000, "Client closed")
        webSocket = null
        isConnected = false
    }

    private fun handleMessage(text: String) {
        try {
            val obj = gson.fromJson(text, JsonObject::class.java)
            val type = obj.get("type")?.asString ?: return
            val from = obj.get("from")?.asString ?: ""

            when (type) {
                "offer" -> {
                    val sdp = obj.get("sdp")?.asString ?: ""
                    listener.onOfferReceived(from, sdp)
                }
                "answer" -> {
                    val sdp = obj.get("sdp")?.asString ?: ""
                    listener.onAnswerReceived(from, sdp)
                }
                "candidate" -> {
                    val mid = obj.get("sdpMid")?.asString ?: ""
                    val index = obj.get("sdpMLineIndex")?.asInt ?: 0
                    val sdp = obj.get("sdpCandidate")?.asString ?: ""
                    listener.onCandidateReceived(from, mid, index, sdp)
                }
                "hangup" -> {
                    val reason = obj.get("reason")?.asString ?: "cleared"
                    listener.onHangupReceived(from, reason)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing signaling JSON: $text", e)
        }
    }
}
