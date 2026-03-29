package com.voiddeveloper.tictactoe.model

import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class Room(
    private var socketList: MutableList<WebSocketSession> = mutableListOf(),
    private val availableCoins: MutableList<Char> = mutableListOf('X', 'O'),
    private val board: List<MutableList<Char?>> = List(3) { MutableList(3) { null } },
) {

    private val lock = ReentrantLock()

    fun getSocketListSnapshot(): List<WebSocketSession> {
        return socketList.toList()
    }


    fun getSocketCount(): Int {
        return socketList.size
    }


    fun isRoomFull(): Boolean {
        return socketList.size >= 2
    }


    fun isRoomEmpty(): Boolean {
        return socketList.isEmpty()
    }


    fun containsSocket(session: WebSocketSession): Boolean {
        return socketList.contains(session)
    }


    fun addSocket(session: WebSocketSession) {
        socketList.add(session)
    }


    fun removeSocket(session: WebSocketSession): Boolean {
        return socketList.remove(session)
    }


    fun getCurrentSocket(): WebSocketSession? {
        return socketList.firstOrNull()
    }


    fun replaceSocketList(newSocketList: MutableList<WebSocketSession>) {
        socketList = newSocketList
    }


    fun toggleSocketList() {
        if (socketList.isNotEmpty()) {
            socketList = (socketList.drop(1) + socketList.first()).toMutableList()
        }
    }


    fun getAvailableCoinsSnapshot(): List<Char> {
        return availableCoins.toList()
    }


    fun getFirstAvailableCoin(): Char? {
        return availableCoins.firstOrNull()
    }


    fun removeAvailableCoin(coin: Char): Boolean {
        return availableCoins.remove(coin)
    }


    fun addAvailableCoin(coin: Char) {
        availableCoins.add(coin)
    }


    fun addAvailableCoinIfMissing(coin: Char) {
        if (!availableCoins.contains(coin)) {
            availableCoins.add(coin)
        }
    }


    fun pickAndRemoveFirstAvailableCoin(): Char? {
        val coin = availableCoins.firstOrNull() ?: return null
        availableCoins.remove(coin)
        return coin
    }


    fun getBoardSnapshot(): List<List<Char?>> {
        return board.map { row -> row.toList() }
    }


    fun getExactBoard(): List<MutableList<Char?>> {
        return board
    }


    fun getCell(row: Int, col: Int): Char? {
        return board[row][col]
    }


    fun setCell(row: Int, col: Int, value: Char?) {
        board[row][col] = value
    }


    fun clearBoard() {
        board.forEach { row ->
            for (i in row.indices) {
                row[i] = null
            }
        }
    }

    fun <T> executeLocked(action: () -> T): T {
        return lock.withLock {
            action()
        }
    }

}
