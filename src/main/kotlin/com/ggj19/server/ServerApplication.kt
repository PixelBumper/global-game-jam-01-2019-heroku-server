package com.ggj19.server

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ServerApplication {
    companion object {
        val LOG = LoggerFactory.getLogger("GameServer")!!
    }

}

fun main(args: Array<String>) {
  runApplication<ServerApplication>(*args)
}
