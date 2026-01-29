package com.voiddeveloper.tictactoe.component

import com.voiddeveloper.tictactoe.FakeWebSocketSession
import com.voiddeveloper.tictactoe.model.GameActionType
import com.voiddeveloper.tictactoe.model.ServerResponse
import com.voiddeveloper.tictactoe.utils.Utils.generateRandomCode
import com.voiddeveloper.tictactoe.utils.Utils.getCleanId
import com.voiddeveloper.tictactoe.utils.Utils.getCoin
import com.voiddeveloper.tictactoe.utils.Utils.getSecureUserId
import kotlinx.serialization.json.Json
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.web.socket.CloseStatus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue


@SpringBootTest
class GameWsHandlerTest {

    @Autowired
    private lateinit var tokenHandler: TokenHandler

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

        assertTrue(gameWsHandler.gameRooms.contains(secureRoomId))
        assertTrue(gameWsHandler.gameRooms[secureRoomId]?.socketList?.firstOrNull { it == session1 } != null)

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

        assertTrue(gameWsHandler.gameRooms.containsKey(secureRoomId))

        val roomSessions = gameWsHandler.gameRooms[secureRoomId]
        assertTrue(roomSessions?.socketList?.contains(creatorSession) == true)
        assertTrue(roomSessions.socketList.contains(joinerSession) == true)

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
        val securedPamperdRoomKey = tokenHandler.createRoomToken(roomId = pamperdRoomKey)

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

        assertTrue(gameWsHandler.gameRooms.contains(secureRoomId))
        assertTrue(gameWsHandler.gameRooms[secureRoomId]?.socketList?.firstOrNull { it == creatorSession } != null)

        assertFalse(gameWsHandler.gameRooms.contains(securedPamperdRoomKey))

        assertTrue(gameWsHandler.gameRooms[secureRoomId]?.socketList?.firstOrNull { it == joinerSession } == null)

    }

    @Test
    fun `should cleanup room and notify players when socket is closed`() {

        // -------- Arrange --------

        // Creator creates a room
        val creatorSession = FakeWebSocketSession()
        creatorSession.attributes["action"] = "create_room"

        gameWsHandler.afterConnectionEstablished(creatorSession)

        val creatorPayload = creatorSession.sentMessages.first().payload
        val creatorResponse = json.decodeFromString<ServerResponse>(creatorPayload)
        val secureRoomId = creatorResponse.roomId!!

        val roomKey = secureRoomId.split(".")[0]

        // Joiner joins the same room
        val joinerSession = FakeWebSocketSession()
        joinerSession.attributes["action"] = "join_room"
        joinerSession.attributes["roomId"] = secureRoomId

        gameWsHandler.afterConnectionEstablished(joinerSession)

        val room = gameWsHandler.gameRooms[secureRoomId]!!

        // Capture joiner's data before disconnect
        val joinerCoin = joinerSession.getCoin()
        val joinerCleanId = joinerSession.getSecureUserId().getCleanId()

        assertTrue(room.socketList.contains(joinerSession))
        assertFalse(room.availableCoins.contains(joinerCoin))

        // -------- Act --------

        gameWsHandler.afterConnectionClosed(
            joinerSession,
            CloseStatus.NORMAL
        )

        // -------- Assert --------

        // 1️⃣ Joiner removed from room
        assertFalse(room.socketList.contains(joinerSession))

        // 2️⃣ Coin returned to available pool
        assertTrue(room.availableCoins.contains(joinerCoin))

        // 3️⃣ Creator receives PLAYER_DISCONNECTED message
        val lastMessage = creatorSession.sentMessages.last().payload
        val response = json.decodeFromString<ServerResponse>(lastMessage)

        assertTrue(
            response.message?.command == GameActionType.PLAYER_DISCONNECTED
        )

        assertTrue(
            response.message?.content ==
                    "Player $joinerCleanId Disconnected"
        )

        // 4️⃣ Room still exists
        assertTrue(gameWsHandler.gameRooms.containsKey(secureRoomId))
    }

    @Test
    fun `should cleanup room when last player disconnects`() {

        // Arrange
        val session = FakeWebSocketSession()
        session.attributes["action"] = "create_room"

        // Create room
        gameWsHandler.afterConnectionEstablished(session)

        val payload = session.sentMessages.first().payload
        val response = json.decodeFromString<ServerResponse>(payload)

        val secureRoomId = response.roomId!!

        val roomBeforeClose = gameWsHandler.gameRooms[secureRoomId]
        assertTrue(roomBeforeClose != null)
        assertTrue(roomBeforeClose.socketList.contains(session))

        val assignedCoin = session.getCoin()
        assertFalse(roomBeforeClose.availableCoins.contains(assignedCoin))

        // Act — close the only socket
        gameWsHandler.afterConnectionClosed(session, CloseStatus.NORMAL)

        assertFalse(gameWsHandler.gameRooms.containsKey(secureRoomId))

    }

    @Test
    fun `third person should not be able to join when room is full`() {

        // Creator
        val creatorSession = FakeWebSocketSession()
        creatorSession.attributes["action"] = "create_room"

        gameWsHandler.afterConnectionEstablished(creatorSession)

        val creatorPayload = creatorSession.sentMessages.first().payload
        val creatorResponse = json.decodeFromString<ServerResponse>(creatorPayload)

        val secureRoomId = creatorResponse.roomId!!

        // First joiner
        val joiner1 = FakeWebSocketSession()
        joiner1.attributes["action"] = "join_room"
        joiner1.attributes["roomId"] = secureRoomId

        gameWsHandler.afterConnectionEstablished(joiner1)

        // Second joiner
        val joiner2 = FakeWebSocketSession()
        joiner2.attributes["action"] = "join_room"
        joiner2.attributes["roomId"] = secureRoomId

        gameWsHandler.afterConnectionEstablished(joiner2)

        val joiner2Payload = joiner2.sentMessages.first().payload
        val joiner2Response = json.decodeFromString<ServerResponse>(joiner2Payload)

        assertTrue(
            joiner2Response.message?.content == "Room Already Full"
        )

        assertTrue(
            joiner2Response.message.command == GameActionType.ROOM_FULL
        )

        // Assert room still exists and has only two players
        val room = gameWsHandler.gameRooms[secureRoomId]
        assertTrue(room?.socketList?.size == 2)
    }

    @Test
    fun `two different secure rooms should assign different coins and exhaust coin pool`() {

        // Creator 1
        val session1 = FakeWebSocketSession()
        session1.attributes["action"] = "create_room"

        gameWsHandler.afterConnectionEstablished(session1)

        val response1 = json.decodeFromString<ServerResponse>(
            session1.sentMessages.first().payload
        )
        val secureRoomId1 = response1.roomId!!

        val coin1 = session1.getCoin()

        // Creator 2
        val session2 = FakeWebSocketSession()
        session2.attributes["action"] = "join_room"
        session2.attributes["roomId"] = secureRoomId1

        gameWsHandler.afterConnectionEstablished(session2)

        val response2 = json.decodeFromString<ServerResponse>(
            session2.sentMessages.first().payload
        )

        val coin2 = session2.getCoin()

        // Coins must be different
        assertTrue(coin1 != coin2)

        // No coins left in either room
        val room1 = gameWsHandler.gameRooms[secureRoomId1]

        assertTrue(room1?.availableCoins?.isEmpty() == true)
    }


}