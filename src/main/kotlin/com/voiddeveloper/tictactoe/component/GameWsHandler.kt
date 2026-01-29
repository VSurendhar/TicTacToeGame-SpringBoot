package com.voiddeveloper.tictactoe.component

import com.voiddeveloper.tictactoe.model.GameActionType
import com.voiddeveloper.tictactoe.model.GameStatePayload
import com.voiddeveloper.tictactoe.model.Room
import com.voiddeveloper.tictactoe.model.ServerResponse
import com.voiddeveloper.tictactoe.utils.Utils.generateRandomCode
import com.voiddeveloper.tictactoe.utils.Utils.getCleanId
import com.voiddeveloper.tictactoe.utils.Utils.getCoin
import com.voiddeveloper.tictactoe.utils.Utils.getSecureRoomId
import com.voiddeveloper.tictactoe.utils.Utils.getSecureUserId
import com.voiddeveloper.tictactoe.utils.Utils.setCoin
import com.voiddeveloper.tictactoe.utils.Utils.setSecureRoomId
import com.voiddeveloper.tictactoe.utils.Utils.setSecureUserId
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler

@Component
class GameWsHandler : TextWebSocketHandler() {

    val gameRooms: MutableMap<String, Room> = HashMap()

    @Autowired
    private lateinit var tokenHandler: TokenHandler

    @OptIn(ExperimentalSerializationApi::class)
    val json = Json {
        explicitNulls = false
    }


    override fun afterConnectionEstablished(session: WebSocketSession) {
        super.afterConnectionEstablished(session)
        val action = session.attributes["action"]
        println(action)
        when (action) {
            "create_room" -> createRoom(session)
            "join_room" -> joinRoom(session)
            else -> {
                val response = ServerResponse(
                    message = GameStatePayload(
                        content = "invalid action",
                        command = GameActionType.INVALID_ACTION
                    )
                )
                val responseStr = json.encodeToString<ServerResponse>(response)
                session.sendMessage(TextMessage(responseStr))
            }
        }
    }

    private fun joinRoom(session: WebSocketSession) {

        val secureRoomId: String? = session.getSecureRoomId()
        val validRoomID = secureRoomId?.let { tokenHandler.verifyRoomToken(it) } ?: false

        val roomId = secureRoomId?.getCleanId()

        if (roomId == null || !validRoomID || !gameRooms.containsKey(secureRoomId)) {
            println("$roomId is not a valid room")
            println(gameRooms)
            val response = ServerResponse(
                message = GameStatePayload(
                    content = "Invalid Room Id or Room Id Missing",
                    command = GameActionType.INVALID_CREDENTIALS
                )
            )
            val responseStr = json.encodeToString<ServerResponse>(response)
            session.sendMessage(TextMessage(responseStr))
            return
        } else {

            val userId = generateRandomCode()
            val secureUserId = tokenHandler.createUserToken(userId = userId)

            session.setSecureUserId(secureUserId)

            println("user ID - $userId")
            println("room ID - $roomId")
            println("secure user ID - $secureUserId")

            val room = gameRooms[secureRoomId] ?: run {
                println(gameRooms)
                val response = ServerResponse(
                    message = GameStatePayload(
                        content = "Invalid Room Id or Room Id Missing",
                        command = GameActionType.INVALID_CREDENTIALS
                    )
                )
                val responseStr = json.encodeToString<ServerResponse>(response)
                session.sendMessage(TextMessage(responseStr))
                return
            }

            if (gameRooms[secureRoomId]?.socketList?.size == 2) {
                println(gameRooms)
                val response = ServerResponse(
                    message = GameStatePayload(
                        content = "Room Already Full",
                        command = GameActionType.ROOM_FULL
                    )
                )
                val responseStr = json.encodeToString<ServerResponse>(response)
                session.sendMessage(TextMessage(responseStr))
                return
            }

            val availableCoin = room.availableCoins[0]
            session.setCoin(availableCoin)
            room.availableCoins.remove(availableCoin)

            room.socketList.add(session)
            println("$roomId is available and user added to room")


            val serverResponse = ServerResponse(
                userId = secureUserId, roomId = secureRoomId, message = GameStatePayload(
                    command = GameActionType.PLAYER_CONNECTED, content = "Room is Available and you are connected"
                )
            )
            val serverResponseStr = json.encodeToString(serverResponse)

            println(gameRooms)
            session.sendMessage(TextMessage(serverResponseStr))

        }
    }

    private fun createRoom(session: WebSocketSession) {

        val userId = generateRandomCode()
        val roomId = generateRandomCode()
        val secureUserId = tokenHandler.createUserToken(userId = userId)
        val secureRoomId = tokenHandler.createRoomToken(roomId = roomId)

        println("user ID - $userId")
        println("room ID - $roomId")
        println("secure user ID - $secureUserId")
        println("secure room ID - $secureRoomId")

        session.setSecureUserId(secureUserId)
        session.setSecureRoomId(secureRoomId)

        gameRooms.put(
            secureRoomId, Room(socketList = mutableListOf(session))
        )

        val selectedCoin = gameRooms[secureRoomId]?.availableCoins?.random() ?: ' '
        session.setCoin(selectedCoin)
        gameRooms[secureRoomId]?.availableCoins?.remove(selectedCoin) ?: ' '

        println("room created!!!!!")
        println(gameRooms)

        val serverResponse = ServerResponse(
            userId = secureUserId, roomId = secureRoomId, message = GameStatePayload(
                command = GameActionType.PLAYER_CONNECTED, content = "Room is Created and you are connected"
            )
        )

        val serverResponseStr = json.encodeToString(serverResponse)

        println("server response: $serverResponse")

        session.sendMessage(TextMessage(serverResponseStr))

    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        super.afterConnectionClosed(session, status)
        val room = gameRooms[session.getSecureRoomId()]
        val disconnectedPlayer = session.getSecureUserId().getCleanId()
        val disconnectedCoin = session.getCoin()
        room?.socketList?.forEach { session ->
            val serverResponse = ServerResponse(
                message = GameStatePayload(
                    content = "Player $disconnectedPlayer Disconnected",
                    command = GameActionType.PLAYER_DISCONNECTED
                )
            )
            val responseStr = json.encodeToString<ServerResponse>(serverResponse)
            session.sendMessage(TextMessage(responseStr))
        }
        println("Removing Session $session")
        room?.socketList?.remove(session)
        room?.availableCoins?.add(disconnectedCoin)
        if (room?.socketList?.isEmpty() == true) {
            gameRooms.remove(session.getSecureRoomId())
        }
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        super.handleTextMessage(session, message)
//        CheckForValidCoin()
//        CheckForValidRoom()
//        CheckForValidUser()
//        TODO("Game Logic")
    }


}