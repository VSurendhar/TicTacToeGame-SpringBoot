package com.voiddeveloper.tictactoe.model

import com.voiddeveloper.tictactoe.utils.Utils.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json


@Serializable
data class GameServerResponse(
    val userId: String? = null,
    val roomId: String? = null,
    val assignedChar: Char? = null,
    val message: Payload,
)

@Serializable
sealed interface Payload {

    @Serializable
    @SerialName("PLAYER_CONNECTED")
    object PlayerConnected : Payload {
        override fun toString(): String {
            return "PLAYER_CONNECTED"
        }
    }

    @Serializable
    @SerialName("PLAYER_DISCONNECTED")
    data class PlayerDisconnected(val assignedChar: Char?) : Payload {
        override fun toString(): String {
            return "PLAYER_DISCONNECTED"
        }
    }

    @Serializable
    @SerialName("ROOM_FULL")
    object RoomFull : Payload {
        override fun toString(): String {
            return "ROOM_FULL"
        }
    }

    @Serializable
    @SerialName("INVALID_ACTION")
    object InvalidAction : Payload {
        override fun toString(): String {
            return "INVALID_ACTION"
        }
    }

    @Serializable
    @SerialName("INVALID_CREDENTIALS")
    data class InvalidCredentials(
        val message: String,
    ) : Payload

    @Serializable
    @SerialName("ROOM_CREATED")
    object RoomCreated : Payload {
        override fun toString(): String {
            return "ROOM_CREATED"
        }
    }

    @Serializable
    @SerialName("YOU ARE CONNECTED")
    data class YourConnected(val players: List<Char>) : Payload {
        override fun toString(): String {
            return "YOU ARE CONNECTED"
        }
    }


    @Serializable
    @SerialName("GAME_STARTED")
    object GameStarted : Payload {
        override fun toString(): String {
            return "GAME_STARTED"
        }
    }

    @Serializable
    @SerialName("MOVE_ACCEPTED")
    data class MoveAccepted(
        val board: List<List<Char?>>,
    ) : Payload

    @Serializable
    @SerialName("ALREADY_FILLED")
    object AlreadyFilled : Payload {
        override fun toString(): String {
            return "ALREADY_FILLED"
        }
    }

    @Serializable
    @SerialName("TURN")
    data class Turn(val playerCoin: Char?, val board: List<List<Char?>>) : Payload

    @Serializable
    @SerialName("INVALID_MOVE")
    object InvalidMove : Payload {
        override fun toString(): String {
            return "INVALID_MOVE"
        }
    }

    @Serializable
    @SerialName("WIN")
    data class Win(
        val coin: Char,
        val board: List<List<Char?>>,
    ) : Payload

    @Serializable
    @SerialName("TIE")
    data class Tie(val board: List<List<Char?>>) : Payload {
        override fun toString(): String {
            return "TIE"
        }
    }

    @Serializable
    @SerialName("SOMETHING_WENT_WRONG")
    data class SomethingWentWrong(val message: String) : Payload

}
