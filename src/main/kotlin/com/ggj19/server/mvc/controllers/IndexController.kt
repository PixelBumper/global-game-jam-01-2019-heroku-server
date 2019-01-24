package com.ggj19.server.mvc.controllers

import org.springframework.boot.web.servlet.error.ErrorController
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private const val PATH = "/error"

@RestController
class IndexController : ErrorController {

    @RequestMapping(value = [PATH])
    fun error(): String {
        return "Error handling"
    }

    override fun getErrorPath(): String {
        return PATH
    }
}
