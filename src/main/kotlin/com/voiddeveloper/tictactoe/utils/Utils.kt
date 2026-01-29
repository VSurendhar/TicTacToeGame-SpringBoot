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
        return this.attributes["roomId"] as String
    }

    fun WebSocketSession.getSecureUserId(): String? {
        return this.attributes["userId"] as String
    }

    fun WebSocketSession.getCoin(): Char {
        return (this.attributes["coin"] as String?)?.get(0) ?: ' '
    }

    fun WebSocketSession.setSecureUserId(userId: String) {
        this.attributes["userId"] = userId
    }

    fun WebSocketSession.setSecureRoomId(roomId: String) {
        this.attributes["roomId"] = roomId
    }

    fun WebSocketSession.setCoin(coin: Char) {
        this.attributes["coin"] = coin.toString()
    }

    fun String?.getCleanId(): String? {
        return this?.split(".")?.firstOrNull()
    }

}