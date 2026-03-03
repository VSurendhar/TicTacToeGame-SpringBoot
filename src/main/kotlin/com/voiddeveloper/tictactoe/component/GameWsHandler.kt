package com.voiddeveloper.tictactoe.component

import com.voiddeveloper.tictactoe.model.*
import com.voiddeveloper.tictactoe.utils.Utils.generateRandomCode
import com.voiddeveloper.tictactoe.utils.Utils.getCleanId
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
        try {

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
        } catch (e: Exception) {
            session.sendMessage(TextMessage("Something went wrong!"))
        }
    }

    private fun joinRoom(session: WebSocketSession) {

        val secureRoomId = session.getSecureRoomId()
        val roomId = secureRoomId.getCleanId()
//        val isValidRoomToken = secureRoomId?.let { tokenHandler.verifyRoomToken(it) } ?: false

        if (roomId == null || !gameRooms.containsKey(roomId)) {

            val response = GameServerResponse(
                message = ServerEvent.InvalidCredentials(
                    message = "Invalid Room Id or Room Id Missing"
                )
            )

            session.sendMessage(
                TextMessage(json.encodeToString(GameServerResponse.serializer(), response))
            )

            session.close()
            return
        }

        val room = gameRooms[roomId]
        if (room == null) {

            val response = GameServerResponse(
                message = ServerEvent.InvalidCredentials(
                    message = "Invalid Room Id or Room Id Missing"
                )
            )

            session.sendMessage(
                TextMessage(json.encodeToString(GameServerResponse.serializer(), response))
            )

            session.close()
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
            session.close()
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

        // Notify joined player to others
        val joinedResponse = GameServerResponse(
            roomId = secureRoomId,
            assignedChar = session.getCoin(),
            message = ServerEvent.PlayerConnected
        )

        val connectedResponse = GameServerResponse(
            userId = secureUserId,
            roomId = secureRoomId,
            assignedChar = session.getCoin(),
            message = ServerEvent.YourConnected(room.socketList.mapNotNull { it.getCoin() })
        )

        session.sendMessage(
            TextMessage(json.encodeToString(GameServerResponse.serializer(), connectedResponse))
        )


        room.socketList.filter { it.id != session.id }.forEach { session ->
            session.sendMessage(
                TextMessage(json.encodeToString(GameServerResponse.serializer(), joinedResponse.copy(userId = null)))
            )
        }

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
            repeat(Random.nextInt(0..1)) {
                room.toggleSocketList()
            }

            val currentSocket = room.socketList.first()
            val yourTurnResponse = GameServerResponse(
                message = GameEvent.Turn(playerCoin = currentSocket.getCoin(), board = room.board)
            )

            // --- Broadcast turn to All players ---
            room.socketList.forEach { otherSession ->
                otherSession.sendMessage(
                    TextMessage(
                        json.encodeToString(
                            GameServerResponse.serializer(), yourTurnResponse
                        )
                    )
                )
            }

            println("Joined in Created")

        }
    }

    private fun createRoom(session: WebSocketSession) {

        val userId = generateRandomCode()
        val roomId = generateRandomCode()

        val secureUserId = tokenHandler.createUserToken(userId = userId)
        val secureRoomId = tokenHandler.createRoomToken(roomId = roomId)

        session.setSecureUserId(secureUserId)
        session.setSecureRoomId(secureRoomId)

        gameRooms[roomId] = Room(
            socketList = mutableListOf(session)
        )

        val room = gameRooms[roomId]!!
        val selectedCoin = gameRooms[roomId]?.availableCoins?.random() ?: ' '

        session.setCoin(selectedCoin)
        gameRooms[roomId]?.availableCoins?.remove(selectedCoin)


        val response = GameServerResponse(
            message = ServerEvent.RoomCreated,
        )

        val responseStr = json.encodeToString(
            GameServerResponse.serializer(), response
        )

        session.sendMessage(TextMessage(responseStr))

        val connectedResponse = GameServerResponse(
            userId = secureUserId,
            roomId = secureRoomId,
            assignedChar = session.getCoin(),
            message = ServerEvent.YourConnected(room.socketList.mapNotNull { it.getCoin() })
        )

        session.sendMessage(
            TextMessage(json.encodeToString(GameServerResponse.serializer(), connectedResponse))
        )

    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        super.afterConnectionClosed(session, status)

        val secureRoomId = session.getSecureRoomId()
        val roomId = secureRoomId.getCleanId()
        val room = gameRooms[roomId] ?: return

        if (!room.socketList.contains(session)) return

        val disconnectedCoin = session.getCoin()

        // Remove disconnected player from room
        room.socketList.remove(session)

        // Return the coin to the pool
        disconnectedCoin?.let { room.availableCoins.add(it) }

        // Notify remaining players
        room.socketList.forEach { otherSession ->
            val response = GameServerResponse(
                message = ServerEvent.PlayerDisconnected
            )
            otherSession.sendMessage(
                TextMessage(json.encodeToString(GameServerResponse.serializer(), response))
            )
        }

        // Remove room if empty
        if (room.socketList.isEmpty()) {
            gameRooms.remove(roomId)
        }
    }

    public override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        super.handleTextMessage(session, message)
        try {

            val secureRoomId = session.getSecureRoomId()
            val secureUserId = session.getSecureUserId()
            val roomId = session.getSecureRoomId().getCleanId()
            val isValidRoomToken = gameRooms.containsKey(roomId)
            val room = roomId?.let { gameRooms[it] }

            // --- Invalid Room ---
            if (secureRoomId == null || !isValidRoomToken || room == null) {
                println("Invalid Room Id or Room Id Missing 1")
                println("$secureRoomId $isValidRoomToken $room")
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
                println("Invalid Room Id or Room Id Missing 2")
                println("$isValidUserToken ${room.socketList.none { it.id == session.id || it.getSecureUserId() == secureUserId }}")
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
                println("Clearing Game Request")
                printBoard(board = room.board)
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
                val yourTurnResponse = GameServerResponse(
                    message = GameEvent.Turn(playerCoin = currentSocket.getCoin(), board = room.board)
                )

                // --- Broadcast turn to All players ---
                room.socketList.forEach { otherSession ->
                    otherSession.sendMessage(
                        TextMessage(
                            json.encodeToString(
                                GameServerResponse.serializer(), yourTurnResponse
                            )
                        )
                    )
                }

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
            if (move?.row == null || move.col == null || player == null) {
                val response = GameServerResponse(
                    message = ServerEvent.InvalidCredentials(
                        message = "Player requires x and y"
                    )
                )
                println("Player requires x and y")
                session.sendMessage(TextMessage(json.encodeToString(GameServerResponse.serializer(), response)))
                return
            }

            if (move.row !in 0 until room.board.size || move.col !in 0 until room.board.first().size) {
                val response = GameServerResponse(
                    message = ServerEvent.InvalidCredentials(
                        message = "Invalid X and Y Coordinates"
                    )
                )
                println("Invalid X and Y Coordinates")
                session.sendMessage(TextMessage(json.encodeToString(GameServerResponse.serializer(), response)))
                return
            }

            if (gameController.isGameCompleted(board = room.board)) {
                println("Game Completed | Invalid Move")
                val response = GameServerResponse(
                    message = GameEvent.InvalidMove
                )
                session.sendMessage(TextMessage(json.encodeToString(GameServerResponse.serializer(), response)))
                return
            }

            // --- Make move ---
            val gameEvent = gameController.mark(
                row = move.row, col = move.col, player = player, board = room.board
            )

            // --- Send move to current player ---
            val moveResponseCurrent = GameServerResponse(
                userId = secureUserId, roomId = secureRoomId, assignedChar = player, message = gameEvent
            )

            if(moveResponseCurrent.message !is GameEvent.Win && moveResponseCurrent.message !is GameEvent.Tie) {
                session.sendMessage(
                    TextMessage(
                        json.encodeToString(
                            GameServerResponse.serializer(),
                            moveResponseCurrent
                        )
                    )
                )
            }

            if (gameEvent is GameEvent.MoveAccepted) {
                println("Move Accepted")
                printBoard(board = room.board)
                // --- Toggle turn ---
                room.toggleSocketList()

                val currentSocket = room.socketList.first()
                val yourTurnResponse = GameServerResponse(
                    message = GameEvent.Turn(playerCoin = currentSocket.getCoin(), board = room.board)
                )

                // --- Broadcast turn to All players ---
                room.socketList.forEach { otherSession ->
                    otherSession.sendMessage(
                        TextMessage(
                            json.encodeToString(
                                GameServerResponse.serializer(), yourTurnResponse
                            )
                        )
                    )
                }

            } else if (gameEvent is GameEvent.Win || gameEvent is GameEvent.Tie) {
                println("Game is Win or Game is Tie")
                val eventResponse = GameServerResponse(
                    message = gameEvent
                )
                printBoard(board = room.board)
                room.socketList.forEach { otherSession ->
                    otherSession.sendMessage(
                        TextMessage(
                            json.encodeToString(
                                GameServerResponse.serializer(), eventResponse
                            )
                        )
                    )
                }

            }

        } catch (e: Exception) {
            println("Something went wrong! ${e.message}")
            session.sendMessage(TextMessage("Something went wrong!"))
        }

    }

    private fun clearGame(board: List<MutableList<Char?>>) {
        board.forEach { row ->
            for (i in row.indices) {
                row[i] = null
            }
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