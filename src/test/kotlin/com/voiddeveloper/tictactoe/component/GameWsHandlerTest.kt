package com.voiddeveloper.tictactoe.component

import com.google.gson.Gson
import com.voiddeveloper.tictactoe.FakeWebSocketSession
import com.voiddeveloper.tictactoe.model.ServerResponse
import com.voiddeveloper.tictactoe.utils.Utils.generateRandomCode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue


@SpringBootTest
class GameWsHandlerTest {

    @Autowired
    private lateinit var gameWsHandler: GameWsHandler

    @Test
    fun `should create room when action is create_room`() {

        val session1 = FakeWebSocketSession()
        session1.attributes["action"] = "create_room"

        gameWsHandler.afterConnectionEstablished(session1)
        val payload = session1.sentMessages.first().payload
        val secureUserId = Gson().fromJson(payload, ServerResponse::class.java).userId
        val secureRoomId = Gson().fromJson(payload, ServerResponse::class.java).roomId

        println(secureUserId)
        val room = secureRoomId.split(".")[0]

        assertTrue(
            payload.contains(
                """
                "message":{"content":"Room is Created and you are connected","command":"PLAYER_CONNECTED"}
                """.trimIndent()
            )
        )

        assertTrue(gameWsHandler.gameRooms.contains(room))
        assertTrue(gameWsHandler.gameRooms[room]?.firstOrNull { it == session1 } != null)

    }

    @Test
    fun `can able to join room created by another person`() {

        val creatorSession = FakeWebSocketSession()
        creatorSession.attributes["action"] = "create_room"

        gameWsHandler.afterConnectionEstablished(creatorSession)

        val creatorPayload = creatorSession.sentMessages.first().payload
        val creatorResponse = Gson().fromJson(creatorPayload, ServerResponse::class.java)
        val secureRoomId = creatorResponse.roomId

        val roomKey = secureRoomId.split(".")[0]

        val joinerSession = FakeWebSocketSession()
        joinerSession.attributes["action"] = "join_room"
        joinerSession.attributes["roomId"] = secureRoomId

        gameWsHandler.afterConnectionEstablished(joinerSession)

        val joinerPayload = joinerSession.sentMessages.first().payload
        val joinerResponse = Gson().fromJson(joinerPayload, ServerResponse::class.java)

        assertTrue(
            joinerPayload.contains(
                """
            "message":{"content":"Room is Available and you are connected","command":"PLAYER_CONNECTED"}
            """.trimIndent()
            )
        )

        assertTrue(gameWsHandler.gameRooms.containsKey(roomKey))

        val roomSessions = gameWsHandler.gameRooms[roomKey]
        assertTrue(roomSessions?.contains(creatorSession) == true)
        assertTrue(roomSessions?.contains(joinerSession) == true)

        println("Room sessions: $roomSessions")

    }

    @Test
    fun `should join a room when action is join_room with a wrong room_id`() {

        val creatorSession = FakeWebSocketSession()
        creatorSession.attributes["action"] = "create_room"

        gameWsHandler.afterConnectionEstablished(creatorSession)

        val creatorPayload = creatorSession.sentMessages.first().payload
        val creatorResponse = Gson().fromJson(creatorPayload, ServerResponse::class.java)
        val secureRoomId = creatorResponse.roomId

        val creatorRoomKey = secureRoomId.split(".")[0]
        val pamperdRoomKey = generateRandomCode()

        val joinerSession = FakeWebSocketSession()
        joinerSession.attributes["action"] = "join_room"
        val secureRoomIdForJoiner = secureRoomId.split(".").let { parts ->
            if (parts.size > 1) "${pamperdRoomKey}.${parts[1]}" else pamperdRoomKey
        }

        joinerSession.attributes["roomId"] = secureRoomIdForJoiner

        gameWsHandler.afterConnectionEstablished(joinerSession)

        val joinerPayload = joinerSession.sentMessages.first().payload

        assertTrue(
            joinerPayload.contains(
                "Invalid Room Id or Room Id Missing"
            )
        )

        assertFalse(gameWsHandler.gameRooms.containsKey(pamperdRoomKey))

        assertTrue(gameWsHandler.gameRooms.contains(creatorRoomKey))
        assertTrue(gameWsHandler.gameRooms[creatorRoomKey]?.firstOrNull { it == creatorSession } != null)

        assertFalse(gameWsHandler.gameRooms.contains(pamperdRoomKey))

        assertTrue(gameWsHandler.gameRooms[creatorRoomKey]?.firstOrNull { it == joinerSession } == null)

    }


}