package com.voiddeveloper.tictactoe.component

import com.voiddeveloper.tictactoe.model.GameActionType
import com.voiddeveloper.tictactoe.model.GameStatePayload
import com.voiddeveloper.tictactoe.model.ServerResponse
import com.voiddeveloper.tictactoe.utils.Utils.generateRandomCode
import com.voiddeveloper.tictactoe.utils.Utils.getSecureRoomId
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

    val gameRooms: MutableMap<String, MutableList<WebSocketSession>> = HashMap()

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

        val roomId = secureRoomId?.split(".")?.get(0)

        if (roomId == null || !validRoomID || !gameRooms.containsKey(roomId)) {
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

            val room = gameRooms[roomId] ?: run {
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

            if (gameRooms[roomId]?.size == 2) {
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


            room.add(session)
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
            roomId, mutableListOf(session)
        )

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
        room?.forEach { session ->
//            TODO()
            /*val serverResponse = ServerResponse(
                userId = session.attributes["userId"] as? String ?: "",
                roomId = session.attributes["roomId"] as? String ?: "",
                message = GameStatePayload(
                    content = "Player ${} Disconnected",
                    command = GameActionType.PLAYER_DISCONNECTED
                )
            )*/
            session.sendMessage(TextMessage("${session.getSecureRoomId()} closed"))
        }
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        super.handleTextMessage(session, message)
//        TODO("Game Logic")
    }


}