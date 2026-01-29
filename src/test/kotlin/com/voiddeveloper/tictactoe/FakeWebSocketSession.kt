package com.voiddeveloper.tictactoe

import org.springframework.http.HttpHeaders
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketExtension
import org.springframework.web.socket.WebSocketSession
import java.net.URI
import java.security.Principal
import java.util.concurrent.ConcurrentHashMap

class FakeWebSocketSession : WebSocketSession {

    private val attrs = ConcurrentHashMap<String, Any>()
    val sentMessages = mutableListOf<TextMessage>()
    val newId = System.currentTimeMillis().toString()

    override fun getAttributes(): MutableMap<String, Any> = attrs

    override fun sendMessage(message: org.springframework.web.socket.WebSocketMessage<*>) {
        sentMessages.add(message as TextMessage)
    }

    // --- minimal stubs (unused in test) ---
    override fun getId() = System.currentTimeMillis().toString()
    override fun isOpen() = true
    override fun close() {}
    override fun close(status: org.springframework.web.socket.CloseStatus) {}
    override fun getUri(): URI? = null
    override fun getHandshakeHeaders(): HttpHeaders {
        return HttpHeaders()
    }

    override fun getPrincipal(): Principal? = null
    override fun getLocalAddress() = null
    override fun getRemoteAddress() = null
    override fun getAcceptedProtocol() = null
    override fun setTextMessageSizeLimit(messageSizeLimit: Int) {}
    override fun getTextMessageSizeLimit() = 0
    override fun setBinaryMessageSizeLimit(messageSizeLimit: Int) {}
    override fun getBinaryMessageSizeLimit() = 0
    override fun getExtensions(): List<WebSocketExtension> {
        return emptyList()
    }

    override fun toString(): String {
        return "FakeWebSocketSession(id='$newId')"
    }

}