package com.ggj19.server.mappers

import com.ggj19.server.ServerApplication.Companion.LOG
import com.ggj19.server.dtos.ErrorMessageDTO


import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import javax.ws.rs.core.Response
import javax.ws.rs.ext.ExceptionMapper
import javax.ws.rs.ext.Provider

val INTERNAL_SERVER_ERROR = Response
        .serverError()
        .entity(ErrorMessageDTO(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error"))
        .build()!!

@Provider
@Component
class AllExceptionMapper : ExceptionMapper<Throwable> {

    override fun toResponse(exception: Throwable): Response {


        LOG.error(exception.message, exception)

        return INTERNAL_SERVER_ERROR
    }
}
