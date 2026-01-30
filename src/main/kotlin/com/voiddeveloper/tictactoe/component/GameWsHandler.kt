package com.voiddeveloper.tictactoe.component

import com.voiddeveloper.tictactoe.model.*
import com.voiddeveloper.tictactoe.utils.Utils.generateRandomCode
import com.voiddeveloper.tictactoe.utils.Utils.getCoin
import com.voiddeveloper.tictactoe.utils.Utils.getSecureRoomId
import com.voiddeveloper.tictactoe.utils.Utils.getSecureUserId
import com.voiddeveloper.tictactoe.utils.Utils.setCoin
import com.voiddeveloper.tictactoe.utils.Utils.setSecureRoomId
import com.voiddeveloper.tictactoe.utils.Utils.setSecureUserId
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlin.random.Random
import kotlin.random.nextInt

@Component
class GameWsHandler : TextWebSocketHandler() {

    val gameRooms: ConcurrentMap<String, Room> = ConcurrentHashMap()
    val gameController: GameController = GameController()

    @Autowired
    private lateinit var tokenHandler: TokenHandler

    @OptIn(ExperimentalSerializationApi::class)
    val json = Json {
        explicitNulls = false
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        super.afterConnectionEstablished(session)
        val action = session.attributes["action"]
        when (action) {
            "create_room" -> createRoom(session)
            "join_room" -> joinRoom(session)
            else -> {
                val response = GameServerResponse(
                    message = ServerEvent.InvalidAction
                )
                val responseStr = json.encodeToString(GameServerResponse.serializer(), response)
                session.sendMessage(TextMessage(responseStr))
            }
        }
    }

    private fun joinRoom(session: WebSocketSession) {

        val secureRoomId = session.getSecureRoomId()
        val isValidRoomToken = secureRoomId?.let { tokenHandler.verifyRoomToken(it) } ?: false

        if (secureRoomId == null || !isValidRoomToken || !gameRooms.containsKey(secureRoomId)) {

            val response = GameServerResponse(
                message = ServerEvent.InvalidCredentials(
                    message = "Invalid Room Id or Room Id Missing"
                )
            )

            session.sendMessage(
                TextMessage(json.encodeToString(GameServerResponse.serializer(), response))
            )

            return
        }

        val room = gameRooms[secureRoomId]
        if (room == null) {

            val response = GameServerResponse(
                message = ServerEvent.InvalidCredentials(
                    message = "Invalid Room Id or Room Id Missing"
                )
            )

            session.sendMessage(
                TextMessage(json.encodeToString(GameServerResponse.serializer(), response))
            )
            return
        }

        // Room full check
        if (room.socketList.size == 2) {

            val response = GameServerResponse(
                message = ServerEvent.RoomFull
            )

            session.sendMessage(
                TextMessage(json.encodeToString(GameServerResponse.serializer(), response))
            )
            return
        }

        // Generate & assign user
        val userId = generateRandomCode()
        val secureUserId = tokenHandler.createUserToken(userId)
        session.setSecureUserId(secureUserId)

        // Assign coin
        val availableCoin = room.availableCoins.first()
        room.availableCoins.remove(availableCoin)
        session.setCoin(availableCoin)

        room.socketList.add(session)

        // Notify joined player
        val joinedResponse = GameServerResponse(
            userId = secureUserId,
            roomId = secureRoomId,
            assignedChar = session.getCoin(),
            message = ServerEvent.PlayerConnected
        )

        session.sendMessage(
            TextMessage(json.encodeToString(GameServerResponse.serializer(), joinedResponse))
        )


        if (room.socketList.size == 2) {

            clearGame(room.board)

            val gameStarted = GameServerResponse(
                roomId = secureRoomId, message = GameEvent.GameStarted
            )

            room.socketList.forEach {
                it.sendMessage(
                    TextMessage(json.encodeToString(GameServerResponse.serializer(), gameStarted))
                )
            }

            // Randomize turn
            repeat(Random.nextInt(1..5)) {
                room.toggleSocketList()
            }

            val currentSocket = room.socketList.first()

            val yourTurn = GameServerResponse(
                message = GameEvent.YourTurn
            )

            currentSocket.sendMessage(
                TextMessage(json.encodeToString(GameServerResponse.serializer(), yourTurn))
            )

        }
    }

    private fun createRoom(session: WebSocketSession) {

        val userId = generateRandomCode()
        val roomId = generateRandomCode()

        val secureUserId = tokenHandler.createUserToken(userId = userId)
        val secureRoomId = tokenHandler.createRoomToken(roomId = roomId)

        session.setSecureUserId(secureUserId)
        session.setSecureRoomId(secureRoomId)

        gameRooms[secureRoomId] = Room(
            socketList = mutableListOf(session)
        )

        val selectedCoin = gameRooms[secureRoomId]?.availableCoins?.random() ?: ' '

        session.setCoin(selectedCoin)
        gameRooms[secureRoomId]?.availableCoins?.remove(selectedCoin)


        val response = GameServerResponse(
            userId = secureUserId,
            roomId = secureRoomId,
            assignedChar = session.getCoin(),
            message = ServerEvent.RoomCreated,
        )

        val responseStr = json.encodeToString(
            GameServerResponse.serializer(), response
        )

        session.sendMessage(TextMessage(responseStr))
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        super.afterConnectionClosed(session, status)

        val secureRoomId = session.getSecureRoomId()
        val room = gameRooms[secureRoomId] ?: return

        val disconnectedCoin = session.getCoin()

        // Notify remaining players
        room.socketList.forEach { otherSession ->
            val response = GameServerResponse(
                message = ServerEvent.PlayerDisconnected
            )
            otherSession.sendMessage(
                TextMessage(json.encodeToString(GameServerResponse.serializer(), response))
            )
        }

        // Remove disconnected player from room
        room.socketList.remove(session)

        // Return the coin to the pool
        disconnectedCoin?.let { room.availableCoins.add(it) }

        // Remove room if empty
        if (room.socketList.isEmpty()) {
            gameRooms.remove(secureRoomId)
        }
    }

    public override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        super.handleTextMessage(session, message)

        val secureRoomId = session.getSecureRoomId()
        val secureUserId = session.getSecureUserId()
        val isValidRoomToken = secureRoomId?.let { tokenHandler.verifyRoomToken(it) } ?: false
        val room = secureRoomId?.let { gameRooms[it] }

        // --- Invalid Room ---
        if (secureRoomId == null || !isValidRoomToken || room == null) {
            val response = GameServerResponse(
                message = ServerEvent.InvalidCredentials(
                    message = "Invalid Room Id or Room Id Missing"
                )
            )
            session.sendMessage(TextMessage(json.encodeToString(GameServerResponse.serializer(), response)))
            return
        }

        // --- Invalid User ---
        val isValidUserToken = secureUserId?.let { tokenHandler.verifyUserToken(it) } ?: false
        if (!isValidUserToken || room.socketList.none { it.id == session.id || it.getSecureUserId() == secureUserId }) {
            val response = GameServerResponse(
                message = ServerEvent.InvalidCredentials(
                    message = "Invalid User Id or User Id Missing"
                )
            )
            session.sendMessage(TextMessage(json.encodeToString(GameServerResponse.serializer(), response)))
            return
        }

        // --- Parse Client Message ---
        val clientMessage = json.decodeFromString<ClientMessage>(message.payload)

        // --- Clear Game Request ---
        if (clientMessage.clearGame == true) {
            clearGame(room.board)

            val gameStarted = GameServerResponse(
                roomId = secureRoomId, message = GameEvent.GameStarted
            )

            room.socketList.forEach {
                it.sendMessage(
                    TextMessage(
                        json.encodeToString(
                            GameServerResponse.serializer(), gameStarted
                        )
                    )
                )
            }

            repeat(Random.nextInt(1..5)) {
                room.toggleSocketList()
            }

            val currentSocket = room.socketList.first()
            val yourTurn = GameServerResponse(
                message = GameEvent.YourTurn
            )

            currentSocket.sendMessage(TextMessage(json.encodeToString(GameServerResponse.serializer(), yourTurn)))
            return
        }

        // --- Enforce correct turn ---
        val correctPlayerId = room.socketList.first().getSecureUserId()
        if (correctPlayerId != secureUserId) {
            val invalidMove = GameServerResponse(
                message = GameEvent.InvalidMove
            )
            session.sendMessage(TextMessage(json.encodeToString(GameServerResponse.serializer(), invalidMove)))
            return
        }

        // --- Validate move ---
        val move = clientMessage.move
        val player = session.getCoin()
        if (move?.x == null || move.y == null || player == null) {
            val response = GameServerResponse(
                message = ServerEvent.InvalidCredentials(
                    message = "Player requires x and y"
                )
            )
            session.sendMessage(TextMessage(json.encodeToString(GameServerResponse.serializer(), response)))
            return
        }

        if (move.x !in 0 until room.board.size || move.y !in 0 until room.board.first().size) {
            val response = GameServerResponse(
                message = ServerEvent.InvalidCredentials(
                    message = "Invalid X and Y Coordinates"
                )
            )
            session.sendMessage(TextMessage(json.encodeToString(GameServerResponse.serializer(), response)))
            return
        }

        // --- Make move ---
        val gameEvent = gameController.mark(
            row = move.x, col = move.y, player = player, board = room.board
        )

        // --- Send move to current player ---
        val moveResponseCurrent = GameServerResponse(
            userId = secureUserId, roomId = secureRoomId, assignedChar = player, message = gameEvent
        )
        session.sendMessage(TextMessage(json.encodeToString(GameServerResponse.serializer(), moveResponseCurrent)))

        // --- Broadcast move to other player ---
        room.socketList.filter { it.id != session.id }.forEach { otherSession ->
            val moveResponseOther = GameServerResponse(
                userId = otherSession.getSecureUserId(),
                roomId = secureRoomId,
                assignedChar = otherSession.getCoin(),
                message = gameEvent
            )
            otherSession.sendMessage(
                TextMessage(
                    json.encodeToString(
                        GameServerResponse.serializer(), moveResponseOther
                    )
                )
            )
        }

        // --- Toggle turn ---
        room.toggleSocketList()

        val currentSocket = room.socketList.first()
        val yourTurnResponse = GameServerResponse(
            message = GameEvent.YourTurn
        )

        currentSocket.sendMessage(TextMessage(json.encodeToString(GameServerResponse.serializer(), yourTurnResponse)))

    }

    private fun clearGame(board: List<MutableList<Char?>>) {
        board.forEach { row ->
            for (i in row.indices) {
                row[i] = null
            }
        }
    }

}