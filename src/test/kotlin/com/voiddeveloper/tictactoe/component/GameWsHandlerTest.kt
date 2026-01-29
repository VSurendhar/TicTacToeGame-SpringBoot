package com.voiddeveloper.tictactoe.component

import com.voiddeveloper.tictactoe.FakeWebSocketSession
import com.voiddeveloper.tictactoe.model.GameActionType
import com.voiddeveloper.tictactoe.model.ServerResponse
import com.voiddeveloper.tictactoe.utils.Utils.generateRandomCode
import kotlinx.serialization.json.Json
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue


@SpringBootTest
class GameWsHandlerTest {

    @Autowired
    private lateinit var gameWsHandler: GameWsHandler

    val json: Json = Json {}

    @Test
    fun `should create room when action is create_room`() {

        val session1 = FakeWebSocketSession()
        session1.attributes["action"] = "create_room"

        gameWsHandler.afterConnectionEstablished(session1)
        val payload = session1.sentMessages.first().payload

        val response = json.decodeFromString<ServerResponse>(payload)
        val secureUserId = response.userId
        val secureRoomId = response.roomId

        println(secureUserId)
        val room = secureRoomId?.split(".")[0]

        assertTrue(
            response.message?.content == "Room is Created and you are connected"
        )

        assertTrue(
            response.message.command == GameActionType.PLAYER_CONNECTED
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
        val creatorResponse = json.decodeFromString<ServerResponse>(creatorPayload)
        val secureRoomId = creatorResponse.roomId

        val roomKey = secureRoomId?.split(".")[0]

        val joinerSession = FakeWebSocketSession()
        joinerSession.attributes["action"] = "join_room"
        joinerSession.attributes["roomId"] = secureRoomId ?: 0

        gameWsHandler.afterConnectionEstablished(joinerSession)

        val joinerPayload = joinerSession.sentMessages.first().payload
        val joinerResponse = json.decodeFromString<ServerResponse>(joinerPayload)

        assertTrue(joinerResponse.message?.content == "Room is Available and you are connected")
        assertTrue(joinerResponse.message.command == GameActionType.PLAYER_CONNECTED)

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
        val creatorResponse = json.decodeFromString<ServerResponse>(creatorPayload)
        val secureRoomId = creatorResponse.roomId

        val creatorRoomKey = secureRoomId?.split(".")[0]
        val pamperdRoomKey = generateRandomCode()

        val joinerSession = FakeWebSocketSession()
        joinerSession.attributes["action"] = "join_room"
        val secureRoomIdForJoiner = secureRoomId?.split(".").let { parts ->
            parts?.size?.let { if (it > 1) "${pamperdRoomKey}.${parts[1]}" else pamperdRoomKey }
        }

        joinerSession.attributes["roomId"] = secureRoomIdForJoiner ?: 0

        gameWsHandler.afterConnectionEstablished(joinerSession)

        val joinerPayload = joinerSession.sentMessages.first().payload

        val joinerResponse = json.decodeFromString<ServerResponse>(joinerPayload)
        assertTrue(joinerResponse.message?.content == "Invalid Room Id or Room Id Missing")
        assertTrue(joinerResponse.message?.command == GameActionType.INVALID_CREDENTIALS)

        assertFalse(gameWsHandler.gameRooms.containsKey(pamperdRoomKey))

        assertTrue(gameWsHandler.gameRooms.contains(creatorRoomKey))
        assertTrue(gameWsHandler.gameRooms[creatorRoomKey]?.firstOrNull { it == creatorSession } != null)

        assertFalse(gameWsHandler.gameRooms.contains(pamperdRoomKey))

        assertTrue(gameWsHandler.gameRooms[creatorRoomKey]?.firstOrNull { it == joinerSession } == null)

    }


}