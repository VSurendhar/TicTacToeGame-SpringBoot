package com.voiddeveloper.tictactoe

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TicTacToeGameSpringBootApplication

fun main(args: Array<String>) {
    runApplication<TicTacToeGameSpringBootApplication>(*args)
}
