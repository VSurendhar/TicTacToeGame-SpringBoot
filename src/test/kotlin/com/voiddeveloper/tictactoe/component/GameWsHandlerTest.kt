package com.voiddeveloper.tictactoe.component

import com.voiddeveloper.tictactoe.FakeWebSocketSession
import com.voiddeveloper.tictactoe.model.*
import com.voiddeveloper.tictactoe.utils.Utils.generateRandomCode
import com.voiddeveloper.tictactoe.utils.Utils.getCoin
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
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

        // Simulate connection established
        gameWsHandler.afterConnectionEstablished(session1)

        // Grab the first message sent to the session
        val payload = session1.sentMessages.first().payload

        // Decode using the new model
        val response = json.decodeFromString<GameServerResponse>(payload)

        val secureUserId = response.userId
        val secureRoomId = response.roomId

        println(secureUserId)

        // --- Assertions ---

        assertTrue(response.message is ServerEvent.RoomCreated)

        // The room should exist on the server
        assertTrue(gameWsHandler.gameRooms.containsKey(secureRoomId))

        // Session should be added to room
        val room = gameWsHandler.gameRooms[secureRoomId]
        assertTrue(room?.socketList?.firstOrNull { it == session1 } != null)
    }

    @Test
    fun `can join room created by another person`() {

        // --- Creator creates the room ---
        val creatorSession = FakeWebSocketSession()
        creatorSession.attributes["action"] = "create_room"

        gameWsHandler.afterConnectionEstablished(creatorSession)

        val creatorPayload = creatorSession.sentMessages.first().payload
        val creatorResponse = json.decodeFromString<GameServerResponse>(creatorPayload)
        val secureRoomId = creatorResponse.roomId

        // --- Joiner joins the same room ---
        val joinerSession = FakeWebSocketSession()
        joinerSession.attributes["action"] = "join_room"
        joinerSession.attributes["roomId"] = secureRoomId ?: ""

        gameWsHandler.afterConnectionEstablished(joinerSession)

        val joinerPayload = joinerSession.sentMessages.first().payload
        val joinerResponse = json.decodeFromString<GameServerResponse>(joinerPayload)

        // --- Assertions ---

        // Payload should be PlayerConnected
        assertTrue(joinerResponse.message is ServerEvent.PlayerConnected)

        // Room should exist
        assertTrue(gameWsHandler.gameRooms.containsKey(secureRoomId))

        // Room should contain both sessions
        val roomSessions = gameWsHandler.gameRooms[secureRoomId]
        assertTrue(roomSessions?.socketList?.contains(creatorSession) == true)
        assertTrue(roomSessions.socketList.contains(joinerSession) == true)

        println("Room sessions: $roomSessions")
    }

    @Test
    fun `should fail to join a room when action is join_room with a wrong room_id`() {

        // --- Creator creates a valid room ---
        val creatorSession = FakeWebSocketSession()
        creatorSession.attributes["action"] = "create_room"
        gameWsHandler.afterConnectionEstablished(creatorSession)

        val creatorPayload = creatorSession.sentMessages.first().payload
        val creatorResponse = json.decodeFromString<GameServerResponse>(creatorPayload)
        val secureRoomId = creatorResponse.roomId

        // --- Tampered room ID for joiner ---
        val tamperedRoomKey = generateRandomCode()
        val securedTamperedRoomKey = tokenHandler.createRoomToken(roomId = tamperedRoomKey)

        val joinerSession = FakeWebSocketSession()
        joinerSession.attributes["action"] = "join_room"

        // Generate a "wrong" room key
        val secureRoomIdForJoiner = secureRoomId?.split(".").let { parts ->
            parts?.size?.let { if (it > 1) "${tamperedRoomKey}.${parts[1]}" else tamperedRoomKey }
        }
        joinerSession.attributes["roomId"] = secureRoomIdForJoiner ?: ""

        // --- Attempt to join ---
        gameWsHandler.afterConnectionEstablished(joinerSession)

        val joinerPayload = joinerSession.sentMessages.first().payload
        val joinerResponse = json.decodeFromString<GameServerResponse>(joinerPayload)

        // --- Assertions ---

        // Payload should be InvalidCredentials
        assertTrue(joinerResponse.message is ServerEvent.InvalidCredentials)
        val invalidCredentials = joinerResponse.message
        assertEquals("Invalid Room Id or Room Id Missing", invalidCredentials.message)

        // The tampered room should not exist
        assertFalse(gameWsHandler.gameRooms.containsKey(tamperedRoomKey))
        assertFalse(gameWsHandler.gameRooms.containsKey(securedTamperedRoomKey))

        // Original room should still exist with the creator
        assertTrue(gameWsHandler.gameRooms.containsKey(secureRoomId))
        val room = gameWsHandler.gameRooms[secureRoomId]
        assertTrue(room?.socketList?.contains(creatorSession) == true)

        // Joiner should not be added to the room
        assertFalse(room.socketList.contains(joinerSession) == true)
    }

    @Test
    fun `should cleanup room and notify players when socket is closed`() {

        // -------- Arrange --------

        // Creator creates a room
        val creatorSession = FakeWebSocketSession()
        creatorSession.attributes["action"] = "create_room"
        gameWsHandler.afterConnectionEstablished(creatorSession)

        val creatorPayload = creatorSession.sentMessages.first().payload
        val creatorResponse = json.decodeFromString<GameServerResponse>(creatorPayload)
        val secureRoomId = creatorResponse.roomId!!

        // Joiner joins the same room
        val joinerSession = FakeWebSocketSession()
        joinerSession.attributes["action"] = "join_room"
        joinerSession.attributes["roomId"] = secureRoomId
        gameWsHandler.afterConnectionEstablished(joinerSession)

        val room = gameWsHandler.gameRooms[secureRoomId]!!

        // Capture joiner's data before disconnect
        val joinerCoin = joinerSession.getCoin()

        assertTrue(room.socketList.contains(joinerSession))
        assertFalse(room.availableCoins.contains(joinerCoin))

        // -------- Act --------

        gameWsHandler.afterConnectionClosed(joinerSession, CloseStatus.NORMAL)

        // -------- Assert --------

        // 1️⃣ Joiner removed from room
        assertFalse(room.socketList.contains(joinerSession))

        // 2️⃣ Coin returned to available pool
        assertTrue(room.availableCoins.contains(joinerCoin))

        // 3️⃣ Creator receives PlayerDisconnected event
        val lastMessage = creatorSession.sentMessages.last().payload
        val response = json.decodeFromString<GameServerResponse>(lastMessage)

        assertTrue(response.message is ServerEvent.PlayerDisconnected)
        val disconnectedEvent = response.message
        // toString() returns the message
        assertEquals("PLAYER_DISCONNECTED", disconnectedEvent.toString())

        // Optionally, if you want a descriptive content:
        // val content = response.messageDescription // if you add such a field

        // 4️⃣ Room still exists
        assertTrue(gameWsHandler.gameRooms.containsKey(secureRoomId))
    }

    @Test
    fun `should cleanup room when last player disconnects`() {

        // -------- Arrange --------
        val session = FakeWebSocketSession()
        session.attributes["action"] = "create_room"

        // Create room
        gameWsHandler.afterConnectionEstablished(session)

        val payload = session.sentMessages.first().payload
        val response = json.decodeFromString<GameServerResponse>(payload)

        val secureRoomId = response.roomId!!
        val roomBeforeClose = gameWsHandler.gameRooms[secureRoomId]!!

        // Room should exist and contain the session
        assertTrue(roomBeforeClose.socketList.contains(session))

        val assignedCoin = session.getCoin()
        assertFalse(roomBeforeClose.availableCoins.contains(assignedCoin))

        // -------- Act --------
        gameWsHandler.afterConnectionClosed(session, CloseStatus.NORMAL)

        // -------- Assert --------

        // Room should be removed since last player disconnected
        assertFalse(gameWsHandler.gameRooms.containsKey(secureRoomId))
    }


    @Test
    fun `third person should not be able to join when room is full`() {

        // -------- Arrange --------

        // Creator creates a room
        val creatorSession = FakeWebSocketSession()
        creatorSession.attributes["action"] = "create_room"
        gameWsHandler.afterConnectionEstablished(creatorSession)

        val creatorPayload = creatorSession.sentMessages.first().payload
        val creatorResponse = json.decodeFromString<GameServerResponse>(creatorPayload)
        val secureRoomId = creatorResponse.roomId!!

        // First joiner
        val joiner1 = FakeWebSocketSession()
        joiner1.attributes["action"] = "join_room"
        joiner1.attributes["roomId"] = secureRoomId
        gameWsHandler.afterConnectionEstablished(joiner1)

        // Second joiner attempts to join the same room (should fail)
        val joiner2 = FakeWebSocketSession()
        joiner2.attributes["action"] = "join_room"
        joiner2.attributes["roomId"] = secureRoomId
        gameWsHandler.afterConnectionEstablished(joiner2)

        // -------- Act --------
        val joiner2Payload = joiner2.sentMessages.first().payload
        val joiner2Response = json.decodeFromString<GameServerResponse>(joiner2Payload)

        // -------- Assert --------

        // Payload should be RoomFull
        assertTrue(joiner2Response.message is ServerEvent.RoomFull)
        val roomFullEvent = joiner2Response.message
        assertEquals("ROOM_FULL", roomFullEvent.toString())

        // Room should still exist and contain only two players
        val room = gameWsHandler.gameRooms[secureRoomId]
        assertTrue(room?.socketList?.size == 2)
        assertTrue(room.socketList.contains(creatorSession) == true)
        assertTrue(room.socketList.contains(joiner1) == true)
        assertFalse(room.socketList.contains(joiner2) == true)
    }


    @Test
    fun `two different players in a room should get different coins and exhaust coin pool`() {

        // -------- Arrange --------

        // Creator 1 creates a room
        val session1 = FakeWebSocketSession()
        session1.attributes["action"] = "create_room"
        gameWsHandler.afterConnectionEstablished(session1)

        val response1 = json.decodeFromString<GameServerResponse>(
            session1.sentMessages.first().payload
        )
        val secureRoomId1 = response1.roomId!!
        val coin1 = session1.getCoin()

        // Joiner (player 2) joins the same room
        val session2 = FakeWebSocketSession()
        session2.attributes["action"] = "join_room"
        session2.attributes["roomId"] = secureRoomId1
        gameWsHandler.afterConnectionEstablished(session2)

        val response2 = json.decodeFromString<GameServerResponse>(
            session2.sentMessages.first().payload
        )
        val coin2 = session2.getCoin()

        // -------- Assert --------

        // Coins must be different
        assertTrue(coin1 != coin2)

        // Assigned chars must match the coins
        assertEquals(coin1, response1.assignedChar)
        assertEquals(coin2, response2.assignedChar)

        // Coin pool should be exhausted
        val room = gameWsHandler.gameRooms[secureRoomId1]!!
        assertTrue(room.availableCoins.isEmpty())

        // Room should contain exactly 2 players
        assertTrue(room.socketList.size == 2)
        assertTrue(room.socketList.contains(session1))
        assertTrue(room.socketList.contains(session2))
    }


    @Test
    fun `game should start when second player joins the room`() {

        // -------- Arrange --------

        // Player 1 creates a room
        val creatorSession = FakeWebSocketSession()
        creatorSession.attributes["action"] = "create_room"
        gameWsHandler.afterConnectionEstablished(creatorSession)

        val creatorResponse = json.decodeFromString<GameServerResponse>(
            creatorSession.sentMessages.first().payload
        )
        val roomId = creatorResponse.roomId!!
        val room = gameWsHandler.gameRooms[roomId]!!

        // Player 2 joins the same room
        val joinerSession = FakeWebSocketSession()
        joinerSession.attributes["action"] = "join_room"
        joinerSession.attributes["roomId"] = roomId
        gameWsHandler.afterConnectionEstablished(joinerSession)

        // -------- Act --------
        // After second player joins, a GameStarted event is sent to both players
        val creatorGameStartedMsg = creatorSession.sentMessages
            .map { json.decodeFromString<GameServerResponse>(it.payload) }
            .firstOrNull { it.message is GameEvent.GameStarted }

        val joinerGameStartedMsg = joinerSession.sentMessages
            .map { json.decodeFromString<GameServerResponse>(it.payload) }
            .firstOrNull { it.message is GameEvent.GameStarted }

        // -------- Assert --------
        assertNotNull(creatorGameStartedMsg)
        assertNotNull(joinerGameStartedMsg)

        // Type-safe check for GameStarted event
        assertTrue(creatorGameStartedMsg!!.message is GameEvent.GameStarted)
        assertTrue(joinerGameStartedMsg!!.message is GameEvent.GameStarted)

        // The room should now contain exactly two players
        assertEquals(2, room.socketList.size)
        assertTrue(room.socketList.contains(creatorSession))
        assertTrue(room.socketList.contains(joinerSession))
    }

    @Test
    fun `should reject move when player plays out of turn`() {

        // ---------- Player 1 creates room ----------
        val player1 = FakeWebSocketSession()
        player1.attributes["action"] = "create_room"
        gameWsHandler.afterConnectionEstablished(player1)

        val roomId = json.decodeFromString<GameServerResponse>(
            player1.sentMessages.first().payload
        ).roomId!!

        // ---------- Player 2 joins room ----------
        val player2 = FakeWebSocketSession()
        player2.attributes["action"] = "join_room"
        player2.attributes["roomId"] = roomId
        gameWsHandler.afterConnectionEstablished(player2)

        // ---------- Detect who got YOUR_TURN ----------
        val player1Turn = player1.sentMessages
            .map { json.decodeFromString<GameServerResponse>(it.payload).message }
            .any { it is GameEvent.YourTurn }

        val currentPlayer = if (player1Turn) player1 else player2
        val wrongPlayer = if (player1Turn) player2 else player1

        // ---------- Wrong player tries to move ----------
        val moveMsg = ClientMessage(move = GridPosition(x = 0, y = 0))
        val moveStr = json.encodeToString(ClientMessage.serializer(), moveMsg)
        gameWsHandler.handleTextMessage(wrongPlayer, TextMessage(moveStr))

        val invalidMoveResponse = json.decodeFromString<GameServerResponse>(
            wrongPlayer.sentMessages.last().payload
        )

        assertTrue(invalidMoveResponse.message is GameEvent.InvalidMove)

        // ---------- Correct player makes a valid move ----------
        gameWsHandler.handleTextMessage(currentPlayer, TextMessage(moveStr))
        val validMoveResponse = json.decodeFromString<GameServerResponse>(
            currentPlayer.sentMessages.last().payload
        )

        assertTrue(validMoveResponse.message is GameEvent.MoveAccepted)

        // ---------- Turn toggles to other player ----------
        val nextTurnPlayer = if (currentPlayer == player1) player2 else player1
        val nextTurnResponse = json.decodeFromString<GameServerResponse>(
            nextTurnPlayer.sentMessages.last().payload
        )

        assertTrue(nextTurnResponse.message is GameEvent.YourTurn)

        // ---------- Old player tries again (invalid) ----------
        val secondMoveMsg = ClientMessage(move = GridPosition(x = 1, y = 1))
        val secondMoveStr = json.encodeToString(ClientMessage.serializer(), secondMoveMsg)
        gameWsHandler.handleTextMessage(currentPlayer, TextMessage(secondMoveStr))

        val secondInvalidResponse = json.decodeFromString<GameServerResponse>(
            currentPlayer.sentMessages.last().payload
        )

        assertTrue(secondInvalidResponse.message is GameEvent.InvalidMove)
    }

}