package com.voiddeveloper.tictactoe

import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/")
class InitController {

    @RequestMapping("/")
    fun isAlive() : ResponseEntity<String> {
        return ResponseEntity.ok("I am alive")
    }
}