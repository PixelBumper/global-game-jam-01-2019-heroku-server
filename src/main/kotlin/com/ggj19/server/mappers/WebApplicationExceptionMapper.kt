package com.ggj19.server.mappers

import com.ggj19.server.ServerApplication.Companion.LOG
import com.ggj19.server.dtos.ErrorMessageDTO
import javax.ws.rs.ClientErrorException
import javax.ws.rs.WebApplicationException
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.ext.ExceptionMapper
import javax.ws.rs.ext.Provider

@Provider class WebApplicationExceptionMapper : ExceptionMapper<WebApplicationException> {
  override fun toResponse(exception: WebApplicationException): Response {
    if (exception is ClientErrorException) {
      LOG.info(exception.message, exception)
    } else {
      LOG.error(exception.message, exception)
    }

    val message = exception.message ?: ""
    val status = exception.response.status

    return Response
        .status(status)
        .type(MediaType.APPLICATION_JSON)
        .entity(ErrorMessageDTO(status, message))
        .build()
  }
}
