package com.voiddeveloper.tictactoe.utils


import com.google.gson.Gson
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.security.SecureRandom

object Utils {

    private val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    inline fun <reified T> WebSocketSession.deserialize(message: TextMessage): T? {
        return try {
            Gson().fromJson(message.payload, T::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun Any.serialize(): String {
        return try {
            Gson().toJson(this)
        } catch (e: Exception) {
            e.printStackTrace()
            "{}"
        }
    }

    fun generateRandomCode(length: Int = 6): String {
        val random = SecureRandom()
        return (1..length)
            .map { ALPHABET[random.nextInt(ALPHABET.length)] }
            .joinToString("")
    }

}