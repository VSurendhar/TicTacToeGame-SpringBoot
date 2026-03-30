package com.voiddeveloper.tictactoe.component

import com.voiddeveloper.tictactoe.model.ClientMessage
import com.voiddeveloper.tictactoe.model.GameServerResponse
import com.voiddeveloper.tictactoe.model.Payload
import com.voiddeveloper.tictactoe.model.Room
import com.voiddeveloper.tictactoe.model.SessionMessage
import com.voiddeveloper.tictactoe.utils.Utils.generateRandomCode
import com.voiddeveloper.tictactoe.utils.Utils.getCoin
import com.voiddeveloper.tictactoe.utils.Utils.getRoomId
import com.voiddeveloper.tictactoe.utils.Utils.getUserId
import com.voiddeveloper.tictactoe.utils.Utils.safeSendMessage
import com.voiddeveloper.tictactoe.utils.Utils.setCoin
import com.voiddeveloper.tictactoe.utils.Utils.setRoomId
import com.voiddeveloper.tictactoe.utils.Utils.setUserId
import com.voiddeveloper.tictactoe.utils.Utils.somethingWentWrong
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
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

    @OptIn(ExperimentalSerializationApi::class)
    val json = Json {
        explicitNulls = false
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        super.afterConnectionEstablished(session)
        try {

            val action = session.attributes["action"]
            when (action) {
                "create_room" -> createRoom(session)
                "join_room" -> joinRoom(session)
                else -> {
                    val response = GameServerResponse(
                        message = Payload.InvalidAction
                    )
                    val responseStr = json.encodeToString(GameServerResponse.serializer(), response)
                    session.safeSendMessage(responseStr)
                }
            }
        } catch (e: Exception) {
            session.safeSendMessage(somethingWentWrong())
        }
    }

    private fun joinRoom(session: WebSocketSession) {

        val roomId = session.getRoomId()

        if (roomId == null || !gameRooms.containsKey(roomId)) {

            val response = GameServerResponse(
                message = Payload.InvalidCredentials(
                    message = "Invalid Room Id or Room Id Missing"
                )
            )

            session.sendMessage(
                TextMessage(json.encodeToString(GameServerResponse.serializer(), response))
            )

            session.close()
            return
        }

        val room = gameRooms[roomId] ?: return
        val sessionMessages = mutableListOf<SessionMessage>()
        var shouldCloseSession = false

        room.executeLocked {
            sessionMessages.addAll(checkAndCleanRoom(roomId, room, session = session, shouldWin = false))
            if (!gameRooms.containsKey(roomId)) return@executeLocked

            // Room full check
            if (room.isRoomFull()) {

                val response = GameServerResponse(
                    message = Payload.RoomFull
                )

                sessionMessages.add(
                    SessionMessage(
                        session,
                        json.encodeToString(GameServerResponse.serializer(), response)
                    )
                )
                shouldCloseSession = true
                return@executeLocked
            }

            // Generate & assign user
            val userId = generateRandomCode()
            session.setUserId(userId)

            // Assign coin
            val availableCoin = room.pickAndRemoveFirstAvailableCoin()!!
            session.setCoin(availableCoin)

            room.addSocket(session)

            // Notify joined player to others
            val joinedResponse = GameServerResponse(
                roomId = roomId,
                assignedChar = session.getCoin(),
                message = Payload.PlayerConnected
            )

            val connectedResponse = GameServerResponse(
                userId = userId,
                roomId = roomId,
                assignedChar = session.getCoin(),
                message = Payload.YourConnected(room.getSocketListSnapshot().mapNotNull { it.getCoin() })
            )

            sessionMessages.add(
                SessionMessage(
                    session,
                    json.encodeToString(GameServerResponse.serializer(), connectedResponse)
                )
            )

            room.getSocketListSnapshot().filter { it.id != session.id }.forEach { otherSession ->
                sessionMessages.add(
                    SessionMessage(
                        otherSession,
                        json.encodeToString(
                            GameServerResponse.serializer(),
                            joinedResponse.copy(userId = null)
                        )
                    )
                )
            }

            if (room.isRoomFull()) {

                room.clearBoard()

                val gameStarted = GameServerResponse(
                    roomId = roomId, message = Payload.GameStarted
                )

                room.getSocketListSnapshot().forEach { it ->
                    sessionMessages.add(
                        SessionMessage(
                            it,
                            json.encodeToString(GameServerResponse.serializer(), gameStarted)
                        )
                    )
                }

                // Randomize turn
                repeat(Random.nextInt(0..1)) {
                    room.toggleSocketList()
                }

                val currentSocket = room.getCurrentSocket()!!
                val yourTurnResponse = GameServerResponse(
                    message = Payload.Turn(playerCoin = currentSocket.getCoin(), board = room.getBoardSnapshot())
                )

                // --- Broadcast turn to All players ---
                room.getSocketListSnapshot().forEach { otherSession ->
                    sessionMessages.add(
                        SessionMessage(
                            otherSession,
                            json.encodeToString(
                                GameServerResponse.serializer(), yourTurnResponse
                            )
                        )
                    )
                }

            }

            sessionMessages.addAll(checkAndCleanRoom(roomId, room, session = session))
        }

        sessionMessages.forEach { (sess, msg) ->
            sess.safeSendMessage(msg)
        }

        if (shouldCloseSession) {
            session.close()
        }
    }

    private fun checkAndCleanRoom(
        roomId: String,
        room: Room,
        session: WebSocketSession? = null,
        shouldWin: Boolean = true
    ): List<SessionMessage> {
        val messages = mutableListOf<SessionMessage>()
        var roomShouldBeRemoved = false

        val currentSockets = room.getSocketListSnapshot()
        val closedSockets = currentSockets.filter { !it.isOpen }

        if (closedSockets.isNotEmpty()) {
            closedSockets.forEach { closedSession ->
                room.removeSocket(closedSession)
                closedSession.getCoin()?.let { room.addAvailableCoinIfMissing(it) }
            }

            val remainingSockets = room.getSocketListSnapshot()
            if (remainingSockets.size == 1) {
                val winnerSession = remainingSockets.first()
                val winnerCoin = winnerSession.getCoin()!!
                val response = GameServerResponse(
                    message = if (shouldWin) {
                        Payload.Win(
                            coin = winnerCoin,
                            board = room.getBoardSnapshot(),
                            isForced = true
                        )
                    } else {
                        Payload.InvalidCredentials(
                            message = "Room Not Available!!!"
                        )
                    }
                )
                messages.add(
                    SessionMessage(
                        winnerSession,
                        json.encodeToString(GameServerResponse.serializer(), response)
                    )
                )
                roomShouldBeRemoved = true
            } else if (remainingSockets.isEmpty()) {
                if (session != null && !shouldWin) {
                    val response = GameServerResponse(
                        message = Payload.InvalidCredentials(
                            message = "Room Not Available!!!"
                        )
                    )
                    messages.add(
                        SessionMessage(
                            session,
                            json.encodeToString(GameServerResponse.serializer(), response)
                        )
                    )
                }
                roomShouldBeRemoved = true
            }
        }

        // --- Always remove if empty ---
        if (room.isRoomEmpty()) {
            roomShouldBeRemoved = true
        }

        if (roomShouldBeRemoved) {
            gameRooms.remove(roomId)
        }

        return messages
    }

    private fun createRoom(session: WebSocketSession) {

        val userId = generateRandomCode()
        val roomId = generateRandomCode()

        session.setUserId(userId)
        session.setRoomId(roomId)

        gameRooms[roomId] = Room()

        val room = gameRooms[roomId]!!
        val sessionMessages = mutableListOf<SessionMessage>()

        room.executeLocked {
            room.addSocket(session)
            val selectedCoin = room.getAvailableCoinsSnapshot().randomOrNull() ?: ' '

            session.setCoin(selectedCoin)
            room.removeAvailableCoin(selectedCoin)

            val response = GameServerResponse(
                message = Payload.RoomCreated,
            )

            val responseStr = json.encodeToString(
                GameServerResponse.serializer(), response
            )

            sessionMessages.add(SessionMessage(session, responseStr))

            val connectedResponse = GameServerResponse(
                userId = userId,
                roomId = roomId,
                assignedChar = session.getCoin(),
                message = Payload.YourConnected(room.getSocketListSnapshot().mapNotNull { it.getCoin() })
            )

            val connectedResponseStr = json.encodeToString(GameServerResponse.serializer(), connectedResponse)
            sessionMessages.add(SessionMessage(session, connectedResponseStr))

            sessionMessages.addAll(checkAndCleanRoom(roomId, room, session = session))
        }

        sessionMessages.forEach { (sess, msg) ->
            sess.safeSendMessage(msg)
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        super.afterConnectionClosed(session, status)

        val roomId = session.getRoomId()
        val room = gameRooms[roomId] ?: return
        val sessionMessages = mutableListOf<SessionMessage>()

        room.executeLocked {
            if (!room.containsSocket(session)) return@executeLocked

            val disconnectedCoin = session.getCoin()

            // Remove disconnected player from room
            room.removeSocket(session)

            // Return the coin to the pool
            disconnectedCoin?.let { room.addAvailableCoin(it) }

            // Notify remaining players
            room.getSocketListSnapshot().forEach { otherSession ->
                val response = GameServerResponse(
                    message = Payload.PlayerDisconnected(disconnectedCoin)
                )
                sessionMessages.add(
                    SessionMessage(
                        otherSession,
                        json.encodeToString(GameServerResponse.serializer(), response)
                    )
                )
            }

            if(roomId!=null) {
                sessionMessages.addAll(checkAndCleanRoom(roomId, room, session = session))
            }
        }

//            TODO("Check for Winner without Game Play Here and remove session and close the room")

        if(roomId!=null) {
            sessionMessages.addAll(checkAndCleanRoom(roomId, room, session = session))
        }

        sessionMessages.forEach { (sess, msg) ->
            sess.safeSendMessage(msg)
        }
    }

    public override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        super.handleTextMessage(session, message)
        try {

            val roomId = session.getRoomId()
            val userId = session.getUserId()

            val isValidRoomToken = gameRooms.containsKey(roomId)
            val room = roomId?.let { gameRooms[it] } ?: run {
                println("Invalid Room Id or Room Id Missing 0")
                println("$roomId $isValidRoomToken $roomId")
                val response = GameServerResponse(
                    message = Payload.InvalidCredentials(
                        message = "Invalid Room Id or Room Id Missing"
                    )
                )
                session.safeSendMessage(json.encodeToString(GameServerResponse.serializer(), response))
                return
            }

            val sessionMessages = mutableListOf<SessionMessage>()

            // Update room and check status
            room.executeLocked {
                sessionMessages.addAll(checkAndCleanRoom(roomId, room, session = session))
            }

            if (!gameRooms.containsKey(roomId)) {
                sessionMessages.forEach { (sess, msg) ->
                    sess.safeSendMessage(msg)
                }
                return
            }

            // --- Invalid Room ---
            if (!isValidRoomToken) {
                println("Invalid Room Id or Room Id Missing 1")
                println("$roomId $room")
                val response = GameServerResponse(
                    message = Payload.InvalidCredentials(
                        message = "Invalid Room Id or Room Id Missing"
                    )
                )
                session.safeSendMessage(json.encodeToString(GameServerResponse.serializer(), response))
                return
            }

        room.executeLocked {
            sessionMessages.addAll(checkAndCleanRoom(roomId, room, session = session))
            if (!gameRooms.containsKey(roomId)) return@executeLocked

            // --- Invalid User ---
            if (room.getSocketListSnapshot()
                    .none { it.id == session.id || it.getUserId() == userId }
            ) {
                val response = GameServerResponse(
                    message = Payload.InvalidCredentials(
                        message = "Invalid User Id or User Id Missing"
                    )
                )
                sessionMessages.add(
                    SessionMessage(
                        session,
                        json.encodeToString(GameServerResponse.serializer(), response)
                    )
                )
                return@executeLocked
            }

            // --- Parse Client Message ---
            val clientMessage = json.decodeFromString<ClientMessage>(message.payload)

            // --- Clear Game Request ---
            if (clientMessage.clearGame == true) {
                room.clearBoard()
                val gameStarted = GameServerResponse(
                    roomId = roomId, message = Payload.GameStarted
                )

                room.getSocketListSnapshot().forEach {
                    sessionMessages.add(
                        SessionMessage(
                            it,
                            json.encodeToString(GameServerResponse.serializer(), gameStarted)
                        )
                    )
                }

                repeat(Random.nextInt(1..5)) {
                    room.toggleSocketList()
                }

                val currentSocket = room.getCurrentSocket()!!
                val yourTurnResponse = GameServerResponse(
                    message = Payload.Turn(playerCoin = currentSocket.getCoin(), board = room.getBoardSnapshot())
                )

                // --- Broadcast turn to All players ---
                room.getSocketListSnapshot().forEach { otherSession ->
                    sessionMessages.add(
                        SessionMessage(
                            otherSession,
                            json.encodeToString(GameServerResponse.serializer(), yourTurnResponse)
                        )
                    )
                }

                return@executeLocked
            }

            // --- Enforce correct turn ---
            val correctPlayerId = room.getCurrentSocket()?.getUserId()
            if (correctPlayerId != userId) {
                val invalidMove = GameServerResponse(
                    message = Payload.InvalidMove
                )
                sessionMessages.add(
                    SessionMessage(
                        session,
                        json.encodeToString(GameServerResponse.serializer(), invalidMove)
                    )
                )
                return@executeLocked
            }

            // --- Validate move ---
            val move = clientMessage.move
            val player = session.getCoin()
            if (move?.row == null || move.col == null || player == null) {
                val response = GameServerResponse(
                    message = Payload.InvalidCredentials(
                        message = "Player requires x and y"
                    )
                )
                println("Player requires x and y")
                sessionMessages.add(
                    SessionMessage(
                        session,
                        json.encodeToString(GameServerResponse.serializer(), response)
                    )
                )
                return@executeLocked
            }

            val boardSnapshot = room.getBoardSnapshot()
            if (move.row !in 0 until boardSnapshot.size || move.col !in 0 until boardSnapshot.first().size) {
                val response = GameServerResponse(
                    message = Payload.InvalidCredentials(
                        message = "Invalid X and Y Coordinates"
                    )
                )
                println("Invalid X and Y Coordinates")
                sessionMessages.add(
                    SessionMessage(
                        session,
                        json.encodeToString(GameServerResponse.serializer(), response)
                    )
                )
                return@executeLocked
            }

            if (gameController.isGameCompleted(board = room.getBoardSnapshot())) {
                println("Game Completed | Invalid Move")
                val response = GameServerResponse(
                    message = Payload.InvalidMove
                )
                sessionMessages.add(
                    SessionMessage(
                        session,
                        json.encodeToString(GameServerResponse.serializer(), response)
                    )
                )
                return@executeLocked
            }

            // --- Make move ---
            val payload = gameController.mark(
                row = move.row, col = move.col, player = player, board = room.getExactBoard()
            )

            // --- Send move to current player ---
            val moveResponseCurrent = GameServerResponse(
                userId = userId, roomId = roomId, assignedChar = player, message = payload
            )

            if (moveResponseCurrent.message !is Payload.Win && moveResponseCurrent.message !is Payload.Tie) {
                sessionMessages.add(
                    SessionMessage(
                        session,
                        json.encodeToString(GameServerResponse.serializer(), moveResponseCurrent)
                    )
                )
            }

            if (payload is Payload.MoveAccepted) {
                // --- Toggle turn ---
                room.toggleSocketList()

                val currentSocket = room.getCurrentSocket()!!
                val yourTurnResponse = GameServerResponse(
                    message = Payload.Turn(
                        playerCoin = currentSocket.getCoin(),
                        board = room.getBoardSnapshot()
                    )
                )

                // --- Broadcast turn to All players ---
                room.getSocketListSnapshot().forEach { otherSession ->
                    sessionMessages.add(
                        SessionMessage(
                            otherSession,
                            json.encodeToString(GameServerResponse.serializer(), yourTurnResponse)
                        )
                    )
                }

            } else if (payload is Payload.Win || payload is Payload.Tie) {
                val eventResponse = GameServerResponse(
                    message = payload
                )
                room.getSocketListSnapshot().forEach { otherSession ->
                    sessionMessages.add(
                        SessionMessage(
                            otherSession,
                            json.encodeToString(GameServerResponse.serializer(), eventResponse)
                        )
                    )
                }

            }

            sessionMessages.addAll(checkAndCleanRoom(roomId, room, session = session))
            if (!gameRooms.containsKey(roomId)) return@executeLocked

        }

        sessionMessages.forEach { (sess, msg) ->
            sess.safeSendMessage(msg)
        }

        } catch (e: Exception) {
            println("Something went wrong! ${e.message}")
            session.safeSendMessage(somethingWentWrong())
        }

    }


    fun printBoard(board: List<List<Char?>>) {

        println()
        board.forEach { row ->
            println(
                row.joinToString(" | ") { cell ->
                    cell?.toString() ?: " "
                }
            )
            println("---------")
        }
        println()
    }

}