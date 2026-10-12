package com.rakshaksetu.app.webrtc

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SignalingClient message parsing and error handling.
 */
class SignalingClientTest {

    private lateinit var listenerEvents: MutableList<String>
    private lateinit var client: SignalingClient

    private val listener = object : SignalingClient.Listener {
        override fun onConnected() { listenerEvents.add("connected") }
        override fun onDisconnected() { listenerEvents.add("disconnected") }
        override fun onOfferReceived(from: String, sdp: String) { listenerEvents.add("offer:$from:$sdp") }
        override fun onAnswerReceived(from: String, sdp: String) { listenerEvents.add("answer:$from:$sdp") }
        override fun onCandidateReceived(from: String, mid: String, index: Int, sdp: String) {
            listenerEvents.add("candidate:$from:$mid:$index:$sdp")
        }
        override fun onHangupReceived(from: String, reason: String) { listenerEvents.add("hangup:$from:$reason") }
        override fun onError(message: String) { listenerEvents.add("error:$message") }
    }

    @Before
    fun setUp() {
        listenerEvents = mutableListOf()
        client = SignalingClient("ws://127.0.0.1:8080", listener)
    }

    @Test
    fun testToOkHttpUrlMapping() {
        assertEquals("http://127.0.0.1:8080", SignalingClient.toOkHttpUrl("ws://127.0.0.1:8080"))
        assertEquals("https://secure.example.com", SignalingClient.toOkHttpUrl("wss://secure.example.com"))
        assertEquals("http://localhost:8080", SignalingClient.toOkHttpUrl("http://localhost:8080"))
    }

    @Test
    fun testHandleMessageOffer() {
        client.handleMessage("""{"type":"offer","from":"+919876543210","sdp":"v=0\r\no=..."}""")
        assertEquals(1, listenerEvents.size)
        assertTrue(listenerEvents[0].startsWith("offer:+919876543210:v=0"))
    }

    @Test
    fun testHandleMessageAnswer() {
        client.handleMessage("""{"type":"answer","from":"+919876543210","sdp":"v=0\r\no=answer..."}""")
        assertEquals(1, listenerEvents.size)
        assertTrue(listenerEvents[0].startsWith("answer:+919876543210:v=0"))
    }

    @Test
    fun testHandleMessageCandidate() {
        client.handleMessage("""{"type":"candidate","from":"peer1","sdpMid":"0","sdpMLineIndex":0,"sdpCandidate":"candidate:..."}""")
        assertEquals(1, listenerEvents.size)
        assertEquals("candidate:peer1:0:0:candidate:...", listenerEvents[0])
    }

    @Test
    fun testHandleMessageHangup() {
        client.handleMessage("""{"type":"hangup","from":"peer1","reason":"user_terminated"}""")
        assertEquals(1, listenerEvents.size)
        assertEquals("hangup:peer1:user_terminated", listenerEvents[0])
    }

    @Test
    fun testHandleMessagePeerOfflineTriggersError() {
        client.handleMessage("""{"type":"peer_offline","peer":"+919876543210","message":"Peer is not reachable"}""")
        assertEquals(1, listenerEvents.size)
        assertEquals("error:Peer is not reachable", listenerEvents[0])
    }

    @Test
    fun testHandleMessagePeerOfflineFallbackFormat() {
        client.handleMessage("""{"type":"peer_offline","to":"+919876543210"}""")
        assertEquals(1, listenerEvents.size)
        assertEquals("error:Peer offline: +919876543210", listenerEvents[0])
    }

    @Test
    fun testHandleMessageErrorTriggersError() {
        client.handleMessage("""{"type":"error","message":"Invalid payload format"}""")
        assertEquals(1, listenerEvents.size)
        assertEquals("error:Invalid payload format", listenerEvents[0])
    }
}
