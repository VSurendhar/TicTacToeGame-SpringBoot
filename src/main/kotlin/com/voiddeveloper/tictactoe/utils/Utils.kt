package com.voiddeveloper.tictactoe.utils


import com.voiddeveloper.tictactoe.model.Payload
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.springframework.web.socket.TextMessage
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

    fun WebSocketSession.getRoomId(): String? {
        return this.attributes["roomId"] as String
    }

    fun WebSocketSession.getUserId(): String? {
        return this.attributes["userId"] as String
    }

    fun WebSocketSession.getCoin(): Char? {
        return (this.attributes["coin"] as String?)?.get(0)
    }

    fun WebSocketSession.setUserId(userId: String) {
        this.attributes["userId"] = userId
    }

    fun WebSocketSession.setRoomId(roomId: String) {
        this.attributes["roomId"] = roomId
    }

    fun WebSocketSession.setCoin(coin: Char) {
        this.attributes["coin"] = coin.toString()
    }

    fun String?.getCleanId(): String? {
        return this?.split(".")?.firstOrNull()
    }

    fun List<List<Char?>>.snapShotList() = this.map { it.toList() }

    fun WebSocketSession.safeSendMessage(msg: String) {
        if (this.isOpen) {
            this.sendMessage(TextMessage(msg))
        }
    }

    fun somethingWentWrong(message : String = "Something went wrong!"): String {
        return Payload.SomethingWentWrong(message).toJson()
    }

    @OptIn(ExperimentalSerializationApi::class)
    val json = Json {
        explicitNulls = false
    }

    fun Payload.toJson(): String = json.encodeToString(Payload.serializer(), this)

}