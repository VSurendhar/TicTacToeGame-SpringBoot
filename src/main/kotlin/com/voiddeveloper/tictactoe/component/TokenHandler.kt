package com.voiddeveloper.tictactoe.component

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class TokenHandler(
    @Value("\${userId.private.key}") private val userPrivateKey: String,
    @Value("\${groupId.private.key}") private val roomPrivateKey: String,
) {

    fun verifyRoomToken(token: String): Boolean {
        return verifyToken(token, roomPrivateKey)
    }

    fun createRoomToken(roomId: String): String {
        return createToken(roomId, roomPrivateKey)
    }

    fun verifyUserToken(token: String): Boolean {
        return verifyToken(token, userPrivateKey)
    }

    fun createUserToken(userId: String): String {
        return createToken(userId, userPrivateKey)
    }

    private fun verifyToken(token: String, secretBase64: String): Boolean {
        val secretKey = SecretKeySpec(Base64.getDecoder().decode(secretBase64), "HmacSHA256")
        val parts = token.split(".")
        if (parts.size != 2) return false

        val payload = parts[0]
        val signature = parts[1]

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(secretKey)
        val expectedSignature = Base64.getEncoder().encodeToString(mac.doFinal(payload.toByteArray()))

        return signature == expectedSignature
    }

    private fun createToken(id: String, secretBase64: String): String {
        val secretKey = SecretKeySpec(Base64.getDecoder().decode(secretBase64), "HmacSHA256")
        val payload = id
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(secretKey)
        val signature = Base64.getEncoder().encodeToString(mac.doFinal(payload.toByteArray()))
        return "$payload.$signature"
    }

}
