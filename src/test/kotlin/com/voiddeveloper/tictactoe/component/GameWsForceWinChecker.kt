package com.voiddeveloper.tictactoe.component

import com.voiddeveloper.tictactoe.FakeWebSocketSession
import com.voiddeveloper.tictactoe.model.ClientMessage
import com.voiddeveloper.tictactoe.model.GameServerResponse
import com.voiddeveloper.tictactoe.model.GridPosition
import com.voiddeveloper.tictactoe.model.Payload
import com.voiddeveloper.tictactoe.utils.Utils.json
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
class GameWsForceWinChecker {

    @Autowired
    private lateinit var gameWsHandler: GameWsHandler

    @Test
    fun `should remove room when creator disconnects before joiner`() {
        // --- creator creates a room ---
        val creatorSession = FakeWebSocketSession()
        creatorSession.attributes["action"] = "create_room"
        gameWsHandler.afterConnectionEstablished(creatorSession)

        val creatorResponse = json.decodeFromString<GameServerResponse>(creatorSession.sentMessages[1].payload)
        val roomId = creatorResponse.roomId!!

        assertTrue(gameWsHandler.gameRooms.containsKey(roomId))

        // --- simulate creator disconnect ---
        creatorSession.setOpen(false)

        // --- trigger cleanup via afterConnectionClosed ---
        gameWsHandler.afterConnectionClosed(creatorSession, CloseStatus.NORMAL)

        // --- assertions ---
        // Room should be removed because it's empty
        assertFalse(gameWsHandler.gameRooms.containsKey(roomId))
    }

    @Test
    fun `should avoid forced win for joiner when creator disconnects before join`() {
        // --- creator creates a room ---
        val creatorSession = FakeWebSocketSession()
        creatorSession.attributes["action"] = "create_room"
        gameWsHandler.afterConnectionEstablished(creatorSession)

        val creatorResponse = json.decodeFromString<GameServerResponse>(creatorSession.sentMessages[1].payload)
        val roomId = creatorResponse.roomId!!

        // --- simulate creator disconnect ---
        creatorSession.setOpen(false)

        // --- joiner attempts to join (but should find the room is cleaned up or invalid) ---
        val joinerSession = FakeWebSocketSession()
        joinerSession.attributes["action"] = "join_room"
        joinerSession.attributes["roomId"] = roomId
        
        gameWsHandler.afterConnectionEstablished(joinerSession)

        // --- assertions ---
        val joinerLastMessage = json.decodeFromString<GameServerResponse>(joinerSession.sentMessages.last().payload)
        
        // Should get invalid credentials because the room was cleaned up (or should have been)
        // Note: joinRoom at line 87 calls checkAndCleanRoom which removes the room
        assertTrue(joinerLastMessage.message is Payload.InvalidCredentials)
    }

    @Test 
    fun `should remove disconnected person and award forced win to remaining player`() {
        // --- setup full room ---
        val creatorSession = FakeWebSocketSession()
        creatorSession.attributes["action"] = "create_room"
        gameWsHandler.afterConnectionEstablished(creatorSession)
        val roomId = json.decodeFromString<GameServerResponse>(creatorSession.sentMessages[1].payload).roomId!!

        val joinerSession = FakeWebSocketSession()
        joinerSession.attributes["action"] = "join_room"
        joinerSession.attributes["roomId"] = roomId
        gameWsHandler.afterConnectionEstablished(joinerSession)

        val room = gameWsHandler.gameRooms[roomId]!!
        assertTrue(room.getSocketCount() == 2)

        // --- simulate creator disconnect ---
        creatorSession.setOpen(false)

        // --- joiner sends a move request ---
        val moveMsg = ClientMessage(move = GridPosition(row = 0, col = 0))
        val moveStr = json.encodeToString(ClientMessage.serializer(), moveMsg)
        
        // This call to handleTextMessage triggers checkAndCleanRoom at the start
        gameWsHandler.handleTextMessage(joinerSession, TextMessage(moveStr))

        // --- assertions ---
        
        // Joiner should receive a WIN payload with isForced = true
        val joinerWinMsg = joinerSession.sentMessages
            .map { json.decodeFromString<GameServerResponse>(it.payload) }
            .firstOrNull { it.message is Payload.Win }
            
        assertTrue(joinerWinMsg != null, "Joiner should have received a Win message")
        val winPayload = joinerWinMsg.message as Payload.Win
        assertTrue(winPayload.isForced == true, "Win should be marked as forced")
        
        // Room should be removed
        assertFalse(gameWsHandler.gameRooms.containsKey(roomId), "Room should be removed after forced win")
    }
}
