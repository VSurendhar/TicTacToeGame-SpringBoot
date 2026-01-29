package com.voiddeveloper.tictactoe.utils


import org.springframework.web.socket.WebSocketSession
import java.security.SecureRandom

object Utils {

    private val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun generateRandomCode(length: Int = 6): String {
        val random = SecureRandom()
        return (1..length)
            .map { ALPHABET[random.nextInt(ALPHABET.length)] }
            .joinToString("")
    }

    fun WebSocketSession.getSecureRoomId(): String? {
        return this.attributes["roomId"] as String?
    }

    fun WebSocketSession.getSecureUserId(): String? {
        return this.attributes["userId"] as String?
    }

    fun WebSocketSession.setSecureUserId(userId: String) {
        this.attributes["userId"] = userId
    }

    fun WebSocketSession.setSecureRoomId(roomId: String) {
        this.attributes["roomId"] = roomId
    }

}