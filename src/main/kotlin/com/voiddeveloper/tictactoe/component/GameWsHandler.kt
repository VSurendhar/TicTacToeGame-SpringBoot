package com.voiddeveloper.tictactoe.component

import com.voiddeveloper.tictactoe.model.ClientMessage
import com.voiddeveloper.tictactoe.model.GameServerResponse
import com.voiddeveloper.tictactoe.model.Payload
import com.voiddeveloper.tictactoe.model.Room
import com.voiddeveloper.tictactoe.utils.Utils.generateRandomCode
import com.voiddeveloper.tictactoe.utils.Utils.getCleanId
import com.voiddeveloper.tictactoe.utils.Utils.getCoin
import com.voiddeveloper.tictactoe.utils.Utils.getSecureRoomId
import com.voiddeveloper.tictactoe.utils.Utils.getSecureUserId
import com.voiddeveloper.tictactoe.utils.Utils.safeSendMessage
import com.voiddeveloper.tictactoe.utils.Utils.setCoin
import com.voiddeveloper.tictactoe.utils.Utils.setSecureRoomId
import com.voiddeveloper.tictactoe.utils.Utils.setSecureUserId
import com.voiddeveloper.tictactoe.utils.Utils.somethingWentWrong
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

        val secureRoomId = session.getSecureRoomId()
        val roomId = secureRoomId.getCleanId()
//        val isValidRoomToken = secureRoomId?.let { tokenHandler.verifyRoomToken(it) } ?: false

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

        val room = gameRooms[roomId]
        if (room == null) {

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

        // Room full check
        if (room.isRoomFull()) {

            val response = GameServerResponse(
                message = Payload.RoomFull
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
        val availableCoin = room.pickAndRemoveFirstAvailableCoin()!!
        session.setCoin(availableCoin)

        room.addSocket(session)

        // Notify joined player to others
        val joinedResponse = GameServerResponse(
            roomId = secureRoomId,
            assignedChar = session.getCoin(),
            message = Payload.PlayerConnected
        )

        val connectedResponse = GameServerResponse(
            userId = secureUserId,
            roomId = secureRoomId,
            assignedChar = session.getCoin(),
            message = Payload.YourConnected(room.getSocketListSnapshot().mapNotNull { it.getCoin() })
        )

        session.sendMessage(
            TextMessage(json.encodeToString(GameServerResponse.serializer(), connectedResponse))
        )


        room.getSocketListSnapshot().filter { it.id != session.id }.forEach { session ->
            session.sendMessage(
                TextMessage(
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
                roomId = secureRoomId, message = Payload.GameStarted
            )

            room.getSocketListSnapshot().forEach {
                it.sendMessage(
                    TextMessage(json.encodeToString(GameServerResponse.serializer(), gameStarted))
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

        gameRooms[roomId] = Room()

        val room = gameRooms[roomId]!!
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

        session.safeSendMessage(responseStr)

        val connectedResponse = GameServerResponse(
            userId = secureUserId,
            roomId = secureRoomId,
            assignedChar = session.getCoin(),
            message = Payload.YourConnected(room.getSocketListSnapshot().mapNotNull { it.getCoin() })
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

        if (!room.containsSocket(session)) return

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
            otherSession.sendMessage(
                TextMessage(json.encodeToString(GameServerResponse.serializer(), response))
            )
        }

        // Remove room if empty
        if (room.isRoomEmpty()) {
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
            val room = roomId?.let { gameRooms[it] } ?: run {
                println("Invalid Room Id or Room Id Missing 0")
                println("$secureRoomId $isValidRoomToken $roomId")
                val response = GameServerResponse(
                    message = Payload.InvalidCredentials(
                        message = "Invalid Room Id or Room Id Missing"
                    )
                )
                session.safeSendMessage(json.encodeToString(GameServerResponse.serializer(), response))
                return
            }

            // --- Invalid Room ---
            if (secureRoomId == null || !isValidRoomToken || room == null) {
                println("Invalid Room Id or Room Id Missing 1")
                println("$secureRoomId $isValidRoomToken $room")
                val response = GameServerResponse(
                    message = Payload.InvalidCredentials(
                        message = "Invalid Room Id or Room Id Missing"
                    )
                )
                session.safeSendMessage(json.encodeToString(GameServerResponse.serializer(), response))
                return
            }

            // --- Invalid User ---
            val isValidUserToken = secureUserId?.let { tokenHandler.verifyUserToken(it) } ?: false
            if (!isValidUserToken || room.getSocketListSnapshot()
                    .none { it.id == session.id || it.getSecureUserId() == secureUserId }
            ) {
                val response = GameServerResponse(
                    message = Payload.InvalidCredentials(
                        message = "Invalid User Id or User Id Missing"
                    )
                )
                session.safeSendMessage(json.encodeToString(GameServerResponse.serializer(), response))
                return
            }

            // --- Parse Client Message ---
            val clientMessage = json.decodeFromString<ClientMessage>(message.payload)

            // --- Clear Game Request ---
            if (clientMessage.clearGame == true) {
                room.clearBoard()
                val gameStarted = GameServerResponse(
                    roomId = secureRoomId, message = Payload.GameStarted
                )

                room.getSocketListSnapshot().forEach {
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

                val currentSocket = room.getCurrentSocket()!!
                val yourTurnResponse = GameServerResponse(
                    message = Payload.Turn(playerCoin = currentSocket.getCoin(), board = room.getBoardSnapshot())
                )

                // --- Broadcast turn to All players ---
                room.getSocketListSnapshot().forEach { otherSession ->
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
            val correctPlayerId = room.getCurrentSocket()?.getSecureUserId()
            if (correctPlayerId != secureUserId) {
                val invalidMove = GameServerResponse(
                    message = Payload.InvalidMove
                )
                session.safeSendMessage(json.encodeToString(GameServerResponse.serializer(), invalidMove))
                return
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
                session.safeSendMessage(json.encodeToString(GameServerResponse.serializer(), response))
                return
            }

            val boardSnapshot = room.getBoardSnapshot()
            if (move.row !in 0 until boardSnapshot.size || move.col !in 0 until boardSnapshot.first().size) {
                val response = GameServerResponse(
                    message = Payload.InvalidCredentials(
                        message = "Invalid X and Y Coordinates"
                    )
                )
                println("Invalid X and Y Coordinates")
                session.safeSendMessage(json.encodeToString(GameServerResponse.serializer(), response))
                return
            }

            if (gameController.isGameCompleted(board = room.getBoardSnapshot())) {
                println("Game Completed | Invalid Move")
                val response = GameServerResponse(
                    message = Payload.InvalidMove
                )
                session.safeSendMessage(json.encodeToString(GameServerResponse.serializer(), response))
                return
            }

            // --- Make move ---
            val payload = gameController.mark(
                row = move.row, col = move.col, player = player, board = room.getExactBoard()
            )

            // --- Send move to current player ---
            val moveResponseCurrent = GameServerResponse(
                userId = secureUserId, roomId = secureRoomId, assignedChar = player, message = payload
            )

            if (moveResponseCurrent.message !is Payload.Win && moveResponseCurrent.message !is Payload.Tie) {
                session.sendMessage(
                    TextMessage(
                        json.encodeToString(
                            GameServerResponse.serializer(),
                            moveResponseCurrent
                        )
                    )
                )
            }

            if (payload is Payload.MoveAccepted) {
                // --- Toggle turn ---
                room.toggleSocketList()

                val currentSocket = room.getCurrentSocket()!!
                val yourTurnResponse = GameServerResponse(
                    message = Payload.Turn(playerCoin = currentSocket.getCoin(), board = room.getBoardSnapshot())
                )

                // --- Broadcast turn to All players ---
                room.getSocketListSnapshot().forEach { otherSession ->
                    otherSession.sendMessage(
                        TextMessage(
                            json.encodeToString(
                                GameServerResponse.serializer(), yourTurnResponse
                            )
                        )
                    )
                }

            } else if (payload is Payload.Win || payload is Payload.Tie) {
                val eventResponse = GameServerResponse(
                    message = payload
                )
                room.getSocketListSnapshot().forEach { otherSession ->
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