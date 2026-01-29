package com.voiddeveloper.tictactoe.component

import com.voiddeveloper.tictactoe.utils.Utils.generateRandomCode
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
class TokenHandlerTest {

    @Autowired
    private lateinit var tokenHandler: TokenHandler

    private lateinit var roomId: String
    private lateinit var secureRoomToken: String

    @BeforeEach
    fun setup() {
        roomId = generateRandomCode()
        secureRoomToken = tokenHandler.createRoomToken(roomId)
    }

    @Test
    fun assertRoomSecureTokenStartsWithRoomID() {
        assertTrue(secureRoomToken.startsWith(roomId))
    }

    @Test
    fun assertRoomIdValidAfterTampering() {
        val tamperedToken =
            generateRandomCode() + secureRoomToken.substringAfter(roomId)

        assertFalse(tokenHandler.verifyRoomToken(tamperedToken))
    }

    @Test
    fun assertRoomIdValidWithoutTampering() {
        assertTrue(tokenHandler.verifyRoomToken(secureRoomToken))
    }

}